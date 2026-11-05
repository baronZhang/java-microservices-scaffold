package com.wd.ms.order.application;

import com.wd.ms.common.core.BizException;
import com.wd.ms.common.core.ErrorCode;
import com.wd.ms.order.domain.order.CancelReason;
import com.wd.ms.order.domain.order.Order;
import com.wd.ms.order.domain.order.OrderItem;
import com.wd.ms.order.domain.order.OrderNoGenerator;
import com.wd.ms.order.domain.order.OrderRepository;
import com.wd.ms.order.domain.order.OrderStatus;
import com.wd.ms.order.domain.outbox.OutboxRepository;
import com.wd.ms.order.domain.port.InventoryPort;
import com.wd.ms.order.domain.port.InventoryPort.ReserveOutcome;
import com.wd.ms.order.domain.port.ProductPort;
import com.wd.ms.order.domain.port.ProductPort.ProductSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 下单、支付、取消，以及两个后台任务调用的"超时关单""卡单恢复"。
 *
 * <p>下单链路的一致性方案（详见文档"分布式事务：下单链路的一致性方案"）：
 *
 * <ol>
 *   <li>本地事务 1：订单落库，状态 RESERVING（幂等键有唯一约束）
 *   <li>事务外：同步调用库存服务预占库存（绝不在数据库事务里做远程调用）
 *   <li>本地事务 2：按预占结果迁移订单状态；需要通知库存服务的变化，和"事件"一起写进本地消息表
 *   <li>消息中继（OutboxRelay）把事件可靠地投递给库存服务：确认（支付）或释放（取消、超时）
 * </ol>
 */
@Service
public class OrderCommandService {

    private static final Logger log = LoggerFactory.getLogger(OrderCommandService.class);
    public static final String ORDER_PAID = "ORDER_PAID";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";

    private final OrderRepository orders;
    private final OutboxRepository outbox;
    private final OrderNoGenerator orderNos;
    private final ProductPort productPort;
    private final InventoryPort inventoryPort;
    private final TransactionTemplate tx;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final Duration paymentTimeout;

    public OrderCommandService(
            OrderRepository orders,
            OutboxRepository outbox,
            OrderNoGenerator orderNos,
            ProductPort productPort,
            InventoryPort inventoryPort,
            TransactionTemplate tx,
            JsonMapper jsonMapper,
            Clock clock,
            @Value("${ms.order.payment-timeout:15m}") Duration paymentTimeout) {
        this.orders = orders;
        this.outbox = outbox;
        this.orderNos = orderNos;
        this.productPort = productPort;
        this.inventoryPort = inventoryPort;
        this.tx = tx;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.paymentTimeout = paymentTimeout;
    }

    /**
     * 下单。@ConcurrencyLimit 是 Spring Framework 7 自带的并发限制（舱壁）：同时进来的下单请求超过 64 个，
     * 多出来的直接拒绝（转成 429），保护数据库和库存服务，而不是让请求排队把线程和连接都耗光。
     */
    @ConcurrencyLimit(limit = 64, policy = ConcurrencyLimit.ThrottlePolicy.REJECT)
    public CreateOrderResult create(long userId, String idempotencyKey, List<OrderLine> lines) {
        // 同一商品合并数量、按商品 id 排序：请求指纹和明细都以这个规范化后的结果为准
        Map<Long, Integer> quantities = new TreeMap<>();
        lines.forEach(line -> quantities.merge(line.skuId(), line.quantity(), Integer::sum));
        String requestHash = fingerprint(quantities);

        var existing = orders.findByIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash);
        }

        Map<Long, ProductSnapshot> products = productPort.findProducts(quantities.keySet());
        List<OrderItem> items = quantities.entrySet().stream()
                .map(entry -> toItem(products.get(entry.getKey()), entry.getKey(), entry.getValue()))
                .toList();

        Instant now = clock.instant();
        Order order = Order.create(orderNos.next(), userId, idempotencyKey, requestHash, items, now);
        try {
            tx.executeWithoutResult(status -> orders.insert(order, now));
        } catch (DuplicateKeyException e) {
            // 同一个幂等键的两个请求同时到达，后到的这个撞上唯一约束：返回先到的那个的结果
            return orders.findByIdempotencyKey(userId, idempotencyKey)
                    .map(first -> replay(first, requestHash))
                    .orElseThrow(() -> e);
        }

        List<InventoryPort.Line> reserveLines = items.stream()
                .map(item -> new InventoryPort.Line(item.skuId(), item.quantity()))
                .toList();
        ReserveOutcome outcome = inventoryPort.reserve(order.orderNo(), reserveLines);
        return switch (outcome.status()) {
            case RESERVED -> {
                Order reserved = order.reserved(clock.instant(), paymentTimeout);
                yield new CreateOrderResult(
                        transition(order, reserved, null) ? reserved : reload(order.orderNo()), false);
            }
            case REJECTED -> {
                // 库存服务明确拒绝：什么都没扣，不需要补偿事件
                transition(order, order.cancel(CancelReason.STOCK_INSUFFICIENT), null);
                throw new BizException(ErrorCode.STOCK_INSUFFICIENT, outcome.message());
            }
            case UNKNOWN -> {
                // 不知道扣没扣成功：取消订单，同时发一条释放事件兜底。没扣过的话，库存服务会做"空回滚"
                log.warn("订单 {} 预占库存结果未知（{}），取消订单并发出释放事件", order.orderNo(), outcome.message());
                transition(order, order.cancel(CancelReason.INVENTORY_UNAVAILABLE), ORDER_CANCELLED);
                throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "库存服务繁忙，请稍后重试");
            }
        };
    }

    public Order pay(String orderNo, long userId) {
        Order order = load(orderNo, userId);
        if (order.status() == OrderStatus.PAID) {
            return order; // 幂等：重复支付请求直接返回
        }
        Order paid = order.pay(clock.instant());
        if (transition(order, paid, ORDER_PAID)) {
            return paid;
        }
        // 条件更新失败：并发的另一个请求先改了状态（比如超时任务刚好把它取消了），按最新状态重新判断
        Order latest = reload(orderNo);
        if (latest.status() == OrderStatus.PAID) {
            return latest;
        }
        throw new BizException(ErrorCode.ORDER_STATUS_CONFLICT, "订单已" + describe(latest) + "，不能支付");
    }

    public Order cancel(String orderNo, long userId) {
        Order order = load(orderNo, userId);
        if (order.status() == OrderStatus.CANCELLED) {
            return order;
        }
        Order cancelled = order.cancel(CancelReason.USER);
        if (transition(order, cancelled, ORDER_CANCELLED)) {
            return cancelled;
        }
        Order latest = reload(orderNo);
        if (latest.status() == OrderStatus.CANCELLED) {
            return latest;
        }
        throw new BizException(ErrorCode.ORDER_STATUS_CONFLICT, "订单已" + describe(latest) + "，不能取消");
    }

    /** 超时关单：由定时任务调用。每单一个小事务，一单失败不影响其他单 */
    public int cancelExpired(int limit) {
        int cancelled = 0;
        for (Order order : orders.findExpired(clock.instant(), limit)) {
            if (transition(order, order.cancel(CancelReason.TIMEOUT), ORDER_CANCELLED)) {
                cancelled++;
                log.info("订单 {} 超时未支付，已取消", order.orderNo());
            }
        }
        return cancelled;
    }

    /** 卡单恢复：预占阶段进程崩溃等原因留下的 RESERVING 订单，超过阈值就取消并发释放事件 */
    public int cancelStuckReserving(Duration threshold, int limit) {
        int cancelled = 0;
        for (Order order : orders.findStuckReserving(clock.instant().minus(threshold), limit)) {
            if (transition(order, order.cancel(CancelReason.RESERVE_STUCK), ORDER_CANCELLED)) {
                cancelled++;
                log.warn("订单 {} 卡在预占阶段超过 {}，已取消并发出释放事件", order.orderNo(), threshold);
            }
        }
        return cancelled;
    }

    /**
     * 本地消息表的核心：订单状态的条件更新和事件插入在同一个本地事务里。
     * 返回 false 表示条件更新没成功（状态已被别人改了），这时事件也不会写入。
     */
    private boolean transition(Order from, Order to, String eventType) {
        Instant now = clock.instant();
        Boolean done = tx.execute(status -> {
            if (!orders.transition(to, from.status(), now)) {
                return false;
            }
            if (eventType != null) {
                String eventId = UUID.randomUUID().toString();
                outbox.insert(eventId, to.orderNo(), eventType, payload(eventId, eventType, to, now), now);
            }
            return true;
        });
        return Boolean.TRUE.equals(done);
    }

    private String payload(String eventId, String type, Order order, Instant now) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", eventId);
        event.put("type", type);
        event.put("orderNo", order.orderNo());
        event.put("occurredAt", now.toString());
        if (order.cancelReason() != null) {
            event.put("reason", order.cancelReason().name());
        }
        return jsonMapper.writeValueAsString(event);
    }

    /**
     * 同一个幂等键再次提交：返回第一次的结果，包括第一次的失败。
     * 先比对请求指纹：键相同但内容不同，说明客户端把幂等键用错了（比如复用了上一单的键），明确报错，而不是悄悄返回上一张订单。
     */
    private CreateOrderResult replay(Order order, String requestHash) {
        if (order.requestHash() != null && !order.requestHash().equals(requestHash)) {
            throw BizException.rule("这个幂等键已经用于另一个下单请求，请重新生成幂等键");
        }
        if (order.status() == OrderStatus.RESERVING) {
            throw new BizException(ErrorCode.IDEMPOTENT_PROCESSING);
        }
        if (order.cancelReason() == CancelReason.STOCK_INSUFFICIENT) {
            throw new BizException(ErrorCode.STOCK_INSUFFICIENT);
        }
        if (order.cancelReason() == CancelReason.INVENTORY_UNAVAILABLE) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "库存服务繁忙，请稍后重试");
        }
        return new CreateOrderResult(order, true);
    }

    /** 请求指纹：规范化后的"商品:数量"列表的 SHA-256 */
    static String fingerprint(Map<Long, Integer> quantities) {
        String canonical = quantities.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(","));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static OrderItem toItem(ProductSnapshot product, long skuId, int quantity) {
        if (product == null) {
            throw BizException.notFound("商品 " + skuId + " 不存在");
        }
        if (!product.onSale()) {
            throw BizException.rule("商品 " + product.name() + " 已下架");
        }
        return new OrderItem(skuId, product.name(), product.price(), quantity);
    }

    /** 支付、取消只能操作自己的订单（管理员也不行：替别人付款不是"查看"权限能覆盖的） */
    private Order load(String orderNo, long userId) {
        Order order = reload(orderNo);
        order.checkVisibleTo(userId, false);
        return order;
    }

    private Order reload(String orderNo) {
        return orders.find(orderNo).orElseThrow(() -> BizException.notFound("订单 " + orderNo + " 不存在"));
    }

    private static String describe(Order order) {
        return switch (order.status()) {
            case PAID -> "支付";
            case CANCELLED -> "取消";
            case PENDING_PAYMENT -> "待支付";
            case RESERVING -> "在处理中";
        };
    }
}

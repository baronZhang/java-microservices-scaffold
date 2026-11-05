package com.wd.ms.order.domain.order;

import com.wd.ms.common.core.BizException;
import com.wd.ms.common.core.ErrorCode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 订单聚合根。状态迁移的规则都在这里，方法返回迁移后的新对象（不可变），由应用层用条件更新落库。
 *
 * @param orderNo 雪花算法生成的订单号（十进制字符串：64 位整数超出了 JS 的安全整数范围，传给前端必须是字符串）
 * @param idempotencyKey 客户端生成的幂等键，同一用户同一个键只会产生一个订单
 * @param requestHash 请求指纹（下单内容的摘要）：同一个幂等键再次提交时，用它判断是不是"同一个请求"
 * @param expireAt 支付截止时间，预占成功后才有值
 */
public record Order(
        String orderNo,
        long userId,
        OrderStatus status,
        CancelReason cancelReason,
        List<OrderItem> items,
        BigDecimal totalAmount,
        String idempotencyKey,
        String requestHash,
        Instant createdAt,
        Instant expireAt,
        Instant paidAt) {

    public Order {
        items = List.copyOf(items);
    }

    public static Order create(
            String orderNo,
            long userId,
            String idempotencyKey,
            String requestHash,
            List<OrderItem> items,
            Instant now) {
        if (items.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "订单至少要有一个商品");
        }
        BigDecimal total = items.stream().map(OrderItem::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Order(
                orderNo,
                userId,
                OrderStatus.RESERVING,
                null,
                items,
                total,
                idempotencyKey,
                requestHash,
                now,
                null,
                null);
    }

    /** 库存预占成功 → 待支付，开始计算支付超时 */
    public Order reserved(Instant now, Duration paymentTimeout) {
        require(OrderStatus.RESERVING);
        return with(OrderStatus.PENDING_PAYMENT, null, now.plus(paymentTimeout), null);
    }

    public Order pay(Instant now) {
        require(OrderStatus.PENDING_PAYMENT);
        if (!now.isBefore(expireAt)) {
            throw new BizException(ErrorCode.ORDER_STATUS_CONFLICT, "订单已超时，请重新下单");
        }
        return with(OrderStatus.PAID, null, expireAt, now);
    }

    public Order cancel(CancelReason reason) {
        if (status != OrderStatus.RESERVING && status != OrderStatus.PENDING_PAYMENT) {
            throw new BizException(ErrorCode.ORDER_STATUS_CONFLICT, "订单当前状态（" + status + "）不能取消");
        }
        // 用户只能取消待支付的订单；预占中的订单由系统处理
        if (reason == CancelReason.USER && status != OrderStatus.PENDING_PAYMENT) {
            throw new BizException(ErrorCode.ORDER_STATUS_CONFLICT, "订单正在处理中，请稍后再试");
        }
        return with(OrderStatus.CANCELLED, reason, expireAt, null);
    }

    /** 别人的订单按"不存在"处理，不暴露订单是否存在。canViewAll：有 order:all 权限（管理员）可以看所有订单 */
    public void checkVisibleTo(long currentUserId, boolean canViewAll) {
        if (!canViewAll && userId != currentUserId) {
            throw BizException.notFound("订单 " + orderNo + " 不存在");
        }
    }

    private void require(OrderStatus expected) {
        if (status != expected) {
            throw new BizException(ErrorCode.ORDER_STATUS_CONFLICT, "订单当前状态（" + status + "）不允许该操作");
        }
    }

    private Order with(OrderStatus newStatus, CancelReason reason, Instant newExpireAt, Instant newPaidAt) {
        return new Order(
                orderNo,
                userId,
                newStatus,
                reason,
                items,
                totalAmount,
                idempotencyKey,
                requestHash,
                createdAt,
                newExpireAt,
                newPaidAt);
    }
}

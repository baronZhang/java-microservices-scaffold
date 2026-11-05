package com.wd.ms.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wd.ms.common.core.BizException;
import com.wd.ms.common.core.ErrorCode;
import com.wd.ms.order.domain.order.CancelReason;
import com.wd.ms.order.domain.order.Order;
import com.wd.ms.order.domain.order.OrderRepository;
import com.wd.ms.order.domain.order.OrderStatus;
import com.wd.ms.order.domain.port.InventoryPort;
import com.wd.ms.order.domain.port.InventoryPort.ReserveOutcome;
import com.wd.ms.order.domain.port.ProductPort;
import com.wd.ms.order.domain.port.ProductPort.ProductSnapshot;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 下单链路的集成测试：真实的订单库、本地消息表、状态机；库存服务和商品服务用 Mock 替身，
 * 这样能精确构造"库存不足""结果未知（超时）""进程在预占后崩溃"这些在真实环境里很难复现的分支。
 */
@SpringBootTest(properties = "ms.order.payment-timeout=PT1S")
@ActiveProfiles("test")
class OrderFlowIT {

    @Autowired
    OrderCommandService commands;

    @Autowired
    OrderRepository orders;

    @Autowired
    JdbcClient jdbc;

    @MockitoBean
    InventoryPort inventory;

    @MockitoBean
    ProductPort products;

    @BeforeEach
    void productsExist() {
        when(products.findProducts(anyCollection()))
                .thenReturn(Map.of(
                        1L, new ProductSnapshot(1, "机械键盘 K8", new BigDecimal("399.00"), true),
                        2L, new ProductSnapshot(2, "无线鼠标 M3", new BigDecimal("129.00"), true)));
    }

    @Test
    void 下单成功_同一幂等键重复提交返回同一订单() {
        when(inventory.reserve(anyString(), anyList())).thenReturn(ReserveOutcome.reserved());
        String key = key();
        CreateOrderResult created = commands.create(3, key, List.of(new OrderLine(1, 2), new OrderLine(2, 1)));
        assertThat(created.replayed()).isFalse();
        Order first = created.order();
        assertThat(first.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(first.totalAmount()).isEqualByComparingTo("927.00");
        assertThat(first.expireAt()).isNotNull();

        // 同样的内容换个顺序提交：规范化后指纹相同，算同一个请求
        CreateOrderResult replay = commands.create(3, key, List.of(new OrderLine(2, 1), new OrderLine(1, 2)));
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.order().orderNo()).isEqualTo(first.orderNo());
        assertThat(events(first.orderNo())).isEmpty();

        // 同一个幂等键、不同的下单内容：明确报错，而不是返回上一张订单
        assertThatThrownBy(() -> commands.create(3, key, List.of(new OrderLine(1, 5))))
                .extracting(e -> ((BizException) e).errorCode())
                .isEqualTo(ErrorCode.BUSINESS_RULE);
    }

    @Test
    void 库存不足_订单取消_不发补偿事件_重复提交得到同样的结果() {
        when(inventory.reserve(anyString(), anyList())).thenReturn(ReserveOutcome.rejected("库存不足：商品 1"));
        String key = key();
        assertThatThrownBy(() -> commands.create(3, key, List.of(new OrderLine(1, 1))))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).errorCode())
                .isEqualTo(ErrorCode.STOCK_INSUFFICIENT);
        Order cancelled = orders.findByIdempotencyKey(3, key).orElseThrow();
        assertThat(cancelled.cancelReason()).isEqualTo(CancelReason.STOCK_INSUFFICIENT);
        assertThat(events(cancelled.orderNo())).isEmpty();
        assertThatThrownBy(() -> commands.create(3, key, List.of(new OrderLine(1, 1))))
                .extracting(e -> ((BizException) e).errorCode())
                .isEqualTo(ErrorCode.STOCK_INSUFFICIENT);
    }

    @Test
    void 预占结果未知_订单取消并写入释放事件兜底() {
        when(inventory.reserve(anyString(), anyList())).thenReturn(ReserveOutcome.unknown("Read timed out"));
        String key = key();
        assertThatThrownBy(() -> commands.create(3, key, List.of(new OrderLine(1, 1))))
                .extracting(e -> ((BizException) e).errorCode())
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
        Order cancelled = orders.findByIdempotencyKey(3, key).orElseThrow();
        assertThat(cancelled.cancelReason()).isEqualTo(CancelReason.INVENTORY_UNAVAILABLE);
        assertThat(events(cancelled.orderNo())).containsExactly("ORDER_CANCELLED");
    }

    @Test
    void 商品服务不可用_快速失败且不落订单() {
        when(products.findProducts(anyCollection()))
                .thenThrow(new BizException(ErrorCode.SERVICE_UNAVAILABLE, "商品服务暂不可用，请稍后重试"));
        String key = key();
        assertThatThrownBy(() -> commands.create(3, key, List.of(new OrderLine(1, 1))))
                .isInstanceOf(BizException.class);
        assertThat(orders.findByIdempotencyKey(3, key)).isEmpty();
        verify(inventory, never()).reserve(anyString(), any());
    }

    @Test
    void 支付写入支付事件_重复支付幂等() {
        when(inventory.reserve(anyString(), anyList())).thenReturn(ReserveOutcome.reserved());
        Order order = commands.create(3, key(), List.of(new OrderLine(1, 1))).order();
        assertThat(commands.pay(order.orderNo(), 3).status()).isEqualTo(OrderStatus.PAID);
        assertThat(commands.pay(order.orderNo(), 3).status()).isEqualTo(OrderStatus.PAID);
        assertThat(events(order.orderNo())).containsExactly("ORDER_PAID");
        assertThatThrownBy(() -> commands.pay(order.orderNo(), 2))
                .extracting(e -> ((BizException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void 超时关单后不能再支付() throws Exception {
        when(inventory.reserve(anyString(), anyList())).thenReturn(ReserveOutcome.reserved());
        Order order = commands.create(3, key(), List.of(new OrderLine(2, 1))).order();
        // 这个测试类把支付超时设成了 1 秒
        Thread.sleep(1100);
        assertThat(commands.cancelExpired(100)).isGreaterThanOrEqualTo(1);
        Order cancelled = orders.find(order.orderNo()).orElseThrow();
        assertThat(cancelled.cancelReason()).isEqualTo(CancelReason.TIMEOUT);
        assertThat(events(order.orderNo())).containsExactly("ORDER_CANCELLED");
        assertThatThrownBy(() -> commands.pay(order.orderNo(), 3))
                .extracting(e -> ((BizException) e).errorCode())
                .isEqualTo(ErrorCode.ORDER_STATUS_CONFLICT);
    }

    @Test
    void 预占后进程崩溃留下的卡单_由恢复任务取消并发释放事件() {
        when(inventory.reserve(anyString(), anyList())).thenThrow(new IllegalStateException("模拟：进程在这里挂了"));
        String key = key();
        assertThatThrownBy(() -> commands.create(3, key, List.of(new OrderLine(1, 1))))
                .isInstanceOf(IllegalStateException.class);
        Order stuck = orders.findByIdempotencyKey(3, key).orElseThrow();
        assertThat(stuck.status()).isEqualTo(OrderStatus.RESERVING);
        // 卡单期间同一个幂等键再提交：告诉客户端"处理中"
        assertThatThrownBy(() -> commands.create(3, key, List.of(new OrderLine(1, 1))))
                .extracting(e -> ((BizException) e).errorCode())
                .isEqualTo(ErrorCode.IDEMPOTENT_PROCESSING);

        assertThat(commands.cancelStuckReserving(Duration.ZERO, 100)).isGreaterThanOrEqualTo(1);
        assertThat(orders.find(stuck.orderNo()).orElseThrow().cancelReason()).isEqualTo(CancelReason.RESERVE_STUCK);
        assertThat(events(stuck.orderNo())).containsExactly("ORDER_CANCELLED");
    }

    @Test
    void 用户取消待支付订单_重复取消幂等() {
        when(inventory.reserve(anyString(), anyList())).thenReturn(ReserveOutcome.reserved());
        Order order = commands.create(3, key(), List.of(new OrderLine(1, 1))).order();
        assertThat(commands.cancel(order.orderNo(), 3).cancelReason()).isEqualTo(CancelReason.USER);
        assertThat(commands.cancel(order.orderNo(), 3).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(events(order.orderNo())).containsExactly("ORDER_CANCELLED");
    }

    private List<String> events(String orderNo) {
        return jdbc.sql("SELECT event_type FROM outbox_event WHERE aggregate_id = ? ORDER BY id")
                .param(orderNo)
                .query(String.class)
                .list();
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }
}

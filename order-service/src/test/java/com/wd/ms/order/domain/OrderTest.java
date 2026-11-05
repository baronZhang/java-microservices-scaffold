package com.wd.ms.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wd.ms.common.core.BizException;
import com.wd.ms.order.domain.order.CancelReason;
import com.wd.ms.order.domain.order.Order;
import com.wd.ms.order.domain.order.OrderItem;
import com.wd.ms.order.domain.order.OrderStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 订单状态机的规则（纯领域逻辑，不需要 Spring）。 */
class OrderTest {

    private final Instant now = Instant.parse("2026-10-27T08:00:00Z");
    private final Order order = Order.create(
            "1",
            3,
            "key-1",
            "hash-1",
            List.of(
                    new OrderItem(1, "机械键盘 K8", new BigDecimal("399.00"), 2),
                    new OrderItem(6, "限量帆布包（秒杀）", new BigDecimal("9.90"), 1)),
            now);

    @Test
    void 下单时计算总金额_状态是预占中() {
        assertThat(order.totalAmount()).isEqualByComparingTo("807.90");
        assertThat(order.status()).isEqualTo(OrderStatus.RESERVING);
    }

    @Test
    void 预占成功后才能支付_超过截止时间不能支付() {
        Order pending = order.reserved(now, Duration.ofMinutes(15));
        assertThat(pending.pay(now.plusSeconds(60)).status()).isEqualTo(OrderStatus.PAID);
        assertThatThrownBy(() -> pending.pay(now.plus(Duration.ofMinutes(15))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("超时");
        assertThatThrownBy(() -> order.pay(now)).isInstanceOf(BizException.class);
    }

    @Test
    void 已支付的订单不能取消_用户不能取消预占中的订单() {
        Order paid = order.reserved(now, Duration.ofMinutes(15)).pay(now);
        assertThatThrownBy(() -> paid.cancel(CancelReason.TIMEOUT)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> order.cancel(CancelReason.USER)).isInstanceOf(BizException.class);
        assertThat(order.cancel(CancelReason.RESERVE_STUCK).status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void 只能看自己的订单_有查看全部权限的除外() {
        assertThatThrownBy(() -> order.checkVisibleTo(2, false)).isInstanceOf(BizException.class);
        order.checkVisibleTo(2, true);
        order.checkVisibleTo(3, false);
    }
}

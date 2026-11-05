package com.wd.ms.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.wd.ms.inventory.domain.reservation.ReservationLine;
import com.wd.ms.inventory.domain.stock.Stock;
import com.wd.ms.inventory.domain.stock.StockRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 一致性方案在库存侧的异常分支：幂等、空回滚、防悬挂、重复消息。
 * 这些分支在正常流程里几乎遇不到，但一旦遇到就是资损，所以必须有测试守着。
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationLifecycleIT {

    @Autowired
    ReservationService reservationService;

    @Autowired
    OrderEventHandler eventHandler;

    @Autowired
    StockRepository stocks;

    @Test
    void 预占_确认_重复确认只生效一次() {
        Stock before = stocks.find(1).orElseThrow();
        assertThat(reservationService
                        .reserve("life-1", List.of(new ReservationLine(1, 2)))
                        .reserved())
                .isTrue();
        reservationService.confirm("life-1");
        reservationService.confirm("life-1");
        Stock after = stocks.find(1).orElseThrow();
        assertThat(after.available()).isEqualTo(before.available() - 2);
        assertThat(after.reserved()).isEqualTo(before.reserved());
        assertThat(after.sold()).isEqualTo(before.sold() + 2);
    }

    @Test
    void 释放预占_重复释放只退一次() {
        Stock before = stocks.find(2).orElseThrow();
        reservationService.reserve("life-2", List.of(new ReservationLine(2, 5)));
        reservationService.release("life-2");
        reservationService.release("life-2");
        assertThat(stocks.find(2).orElseThrow()).isEqualTo(before);
    }

    @Test
    void 空回滚加防悬挂_先收到释放_迟到的预占被拒绝() {
        Stock before = stocks.find(3).orElseThrow();
        // 预占请求还在路上（或者根本没发出去），释放事件先到：记一条 RELEASED，什么也不退
        reservationService.release("life-3");
        // 迟到的预占请求：不能再扣库存，否则这部分库存永远不会被释放（悬挂）
        ReservationResult late = reservationService.reserve("life-3", List.of(new ReservationLine(3, 1)));
        assertThat(late.reserved()).isFalse();
        assertThat(late.message()).contains("订单已取消");
        assertThat(stocks.find(3).orElseThrow()).isEqualTo(before);
    }

    @Test
    void 已支付的订单不能释放库存() {
        reservationService.reserve("life-4", List.of(new ReservationLine(4, 1)));
        reservationService.confirm("life-4");
        Stock confirmed = stocks.find(4).orElseThrow();
        reservationService.release("life-4");
        assertThat(stocks.find(4).orElseThrow()).isEqualTo(confirmed);
    }

    @Test
    void 库存不足时整单失败_已扣的商品一起回滚() {
        stocks.setAvailable(5, 1);
        Stock sku1 = stocks.find(1).orElseThrow();
        ReservationResult result =
                reservationService.reserve("life-5", List.of(new ReservationLine(1, 1), new ReservationLine(5, 2)));
        assertThat(result.reserved()).isFalse();
        assertThat(stocks.find(1).orElseThrow()).isEqualTo(sku1);
        assertThat(stocks.find(5).orElseThrow().available()).isEqualTo(1);
    }

    @Test
    void 同一事件重复投递只处理一次() {
        reservationService.reserve("life-6", List.of(new ReservationLine(1, 3)));
        Stock reserved = stocks.find(1).orElseThrow();
        OrderEvent event = new OrderEvent("event-life-6", OrderEvent.ORDER_CANCELLED, "life-6", Instant.now());
        assertThat(eventHandler.handle(event)).isTrue();
        assertThat(eventHandler.handle(event)).isFalse();
        Stock released = stocks.find(1).orElseThrow();
        assertThat(released.available()).isEqualTo(reserved.available() + 3);
        assertThat(released.reserved()).isEqualTo(reserved.reserved() - 3);
    }
}

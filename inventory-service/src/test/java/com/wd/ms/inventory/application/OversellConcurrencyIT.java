package com.wd.ms.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.wd.ms.inventory.domain.reservation.ReservationLine;
import com.wd.ms.inventory.domain.stock.Stock;
import com.wd.ms.inventory.domain.stock.StockRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 防超卖并发测试：秒杀商品库存 50，200 个订单同时抢购，每单 1 件。
 * 期望：恰好 50 单成功，可售归零、预占 50，任何时刻都不出现负数（还有数据库 CHECK 约束兜底）。
 */
@SpringBootTest
@ActiveProfiles("test")
class OversellConcurrencyIT {

    @Autowired
    ReservationService reservationService;

    @Autowired
    StockRepository stocks;

    @Test
    void 两百个并发订单抢五十件库存_恰好成功五十单() throws Exception {
        long skuId = 6;
        stocks.setAvailable(skuId, 50);
        int orders = 200;
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < orders; i++) {
            String orderNo = "oversell-" + i;
            tasks.add(() -> {
                start.await();
                return reservationService
                        .reserve(orderNo, List.of(new ReservationLine(skuId, 1)))
                        .reserved();
            });
        }
        try (var pool = Executors.newFixedThreadPool(32)) {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Callable<Boolean> task : tasks) {
                futures.add(pool.submit(task));
            }
            start.countDown();
            int success = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    success++;
                }
            }
            Stock stock = stocks.find(skuId).orElseThrow();
            assertThat(success).isEqualTo(50);
            assertThat(stock.available()).isZero();
            assertThat(stock.reserved()).isEqualTo(50);
        }
    }

    @Test
    void 同一个订单号并发重复预占_只扣一次() throws Exception {
        long skuId = 5;
        stocks.setAvailable(skuId, 60);
        try (var pool = Executors.newFixedThreadPool(10)) {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                tasks.add(() -> reservationService
                        .reserve("dup-order", List.of(new ReservationLine(skuId, 3)))
                        .reserved());
            }
            for (Future<Boolean> future : pool.invokeAll(tasks)) {
                assertThat(future.get()).isTrue();
            }
        }
        assertThat(stocks.find(skuId).orElseThrow().available()).isEqualTo(57);
    }

    @Test
    void 多商品订单按商品id排序加锁_交叉下单不死锁() throws Exception {
        stocks.setAvailable(1, 1000);
        stocks.setAvailable(2, 1000);
        try (var pool = Executors.newFixedThreadPool(16)) {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                // 一半订单是 (1, 2)，一半是 (2, 1)：如果按原顺序扣减，就可能出现交叉等锁
                List<ReservationLine> lines = i % 2 == 0
                        ? List.of(new ReservationLine(1, 1), new ReservationLine(2, 1))
                        : List.of(new ReservationLine(2, 1), new ReservationLine(1, 1));
                String orderNo = "cross-" + i;
                tasks.add(() -> reservationService.reserve(orderNo, lines).reserved());
            }
            for (Future<Boolean> future : pool.invokeAll(tasks)) {
                assertThat(future.get()).isTrue();
            }
        }
        assertThat(stocks.find(1).orElseThrow().reserved()).isEqualTo(100);
        assertThat(stocks.find(2).orElseThrow().reserved()).isEqualTo(100);
    }
}

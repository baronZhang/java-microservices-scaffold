package com.wd.ms.order.interfaces.job;

import com.wd.ms.order.application.OrderCommandService;
import com.wd.ms.order.application.OutboxRelayService;
import java.time.Duration;
import java.util.concurrent.locks.Lock;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单服务的三个后台任务（接口层：它们和 HTTP 接口一样，是"触发业务的入口"）。
 *
 * <p>多实例部署时每个实例都会触发定时器，用分布式锁保证同一时刻只有一个实例在跑同一个任务：
 * 拿不到锁就跳过这一轮。注意锁在这里只是"效率保障"（避免重复扫描、减少重复投递），
 * 正确性靠的是数据库的条件更新——即使锁失效、两个实例同时处理同一张订单，也只有一个能把状态改掉。
 */
@Component
public class OrderJobs {

    private static final Logger log = LoggerFactory.getLogger(OrderJobs.class);
    private static final int BATCH_SIZE = 100;

    private final LockRegistry<?> lockRegistry;
    private final OrderCommandService commands;
    private final OutboxRelayService relay;
    private final String instance;

    public OrderJobs(
            LockRegistry<?> lockRegistry,
            OrderCommandService commands,
            OutboxRelayService relay,
            @Value("${spring.application.name}:${server.port}") String instance) {
        this.lockRegistry = lockRegistry;
        this.commands = commands;
        this.relay = relay;
        this.instance = instance;
    }

    /** 消息中继：每秒把到期的本地消息投递出去 */
    @Scheduled(fixedDelayString = "${ms.order.jobs.relay-interval:1s}")
    public void relayOutbox() {
        runExclusively("order-outbox-relay", () -> relay.relayDue(BATCH_SIZE));
    }

    /** 超时关单 */
    @Scheduled(fixedDelayString = "${ms.order.jobs.timeout-interval:5s}")
    public void cancelExpiredOrders() {
        runExclusively("order-timeout-cancel", () -> commands.cancelExpired(BATCH_SIZE));
    }

    /** 卡单恢复：RESERVING 超过 30 秒的订单 */
    @Scheduled(fixedDelayString = "${ms.order.jobs.recovery-interval:10s}")
    public void recoverStuckOrders() {
        runExclusively(
                "order-recover-reserving", () -> commands.cancelStuckReserving(Duration.ofSeconds(30), BATCH_SIZE));
    }

    private void runExclusively(String lockKey, IntSupplier job) {
        Lock lock = lockRegistry.obtain(lockKey);
        boolean locked;
        try {
            locked = lock.tryLock();
        } catch (RuntimeException e) {
            log.warn("获取分布式锁 {} 失败，本轮跳过：{}", lockKey, e.toString());
            return;
        }
        if (!locked) {
            log.debug("任务 {} 正由其他实例执行，{} 本轮跳过", lockKey, instance);
            return;
        }
        try {
            int processed = job.getAsInt();
            if (processed > 0) {
                log.info("任务 {} 由 {} 执行，处理了 {} 条", lockKey, instance, processed);
            }
        } catch (RuntimeException e) {
            log.error("任务 {} 执行失败", lockKey, e);
        } finally {
            try {
                lock.unlock();
            } catch (RuntimeException e) {
                // 锁已经过期被别人拿走时，unlock 会失败：说明任务执行时间超过了锁的租期，要调大租期或续期
                log.warn("释放分布式锁 {} 失败：{}", lockKey, e.toString());
            }
        }
    }
}

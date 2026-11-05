package com.wd.ms.order.infrastructure.id;

import java.time.Instant;
import java.util.function.LongSupplier;

/**
 * 雪花算法：64 位整数 = 1 位符号（恒为 0）+ 41 位毫秒时间戳 + 10 位机器号 + 12 位序列号。
 *
 * <pre>
 * 0 | 41 位：距 2026-01-01 的毫秒数（约 69 年） | 10 位：workerId（0~1023） | 12 位：同一毫秒内的序号（0~4095）
 * </pre>
 *
 * <p>特点：趋势递增（对 B+ 树索引友好）、不依赖数据库、单机每毫秒最多 4096 个。
 * 难点是时钟回拨：服务器时间被 NTP 往回调了，可能生成重复 id。处理策略：
 *
 * <ul>
 *   <li>回拨不超过 {@value #MAX_BACKWARD_MS} 毫秒：等时钟追上来再生成（短暂阻塞）
 *   <li>回拨更多：直接抛异常拒绝服务，宁可下单失败也不能生成重复订单号（同时要告警）
 * </ul>
 *
 * <p>另外两个配套措施在 {@link WorkerIdLease} 里：workerId 由数据库租约分配，不靠手工配置；
 * 每次心跳记录"已经用到的最大时间戳"，重启时如果当前时间比它还小（重启前后发生了回拨），拒绝启动。
 */
public final class SnowflakeIdGenerator {

    /** 自定义纪元：2026-01-01T00:00:00Z。纪元越近，41 位时间戳能用的年头越长 */
    public static final long EPOCH = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();

    static final int WORKER_ID_BITS = 10;
    static final int SEQUENCE_BITS = 12;
    public static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;
    static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;
    static final long MAX_BACKWARD_MS = 5;

    private final long workerId;
    private final LongSupplier clock;
    private final Sleeper sleeper;
    private long lastTimestamp = -1;
    private long sequence;

    public SnowflakeIdGenerator(long workerId, LongSupplier clock, Sleeper sleeper) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId 必须在 0~" + MAX_WORKER_ID + " 之间：" + workerId);
        }
        this.workerId = workerId;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    public SnowflakeIdGenerator(long workerId) {
        this(workerId, System::currentTimeMillis, Thread::sleep);
    }

    /**
     * synchronized 保证同一个生成器内的序列号不重复。JDK 24 起 synchronized 不再把虚拟线程钉在平台线程上（JEP 491），
     * 这里的临界区只有几行计算，用它最简单。
     */
    public synchronized long nextId() {
        long now = clock.getAsLong();
        if (now < lastTimestamp) {
            now = handleClockBackward(now);
        }
        if (now == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 这一毫秒的 4096 个号用完了，自旋等到下一毫秒
                now = waitUntilAfter(lastTimestamp);
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = now;
        return ((now - EPOCH) << (WORKER_ID_BITS + SEQUENCE_BITS)) | (workerId << SEQUENCE_BITS) | sequence;
    }

    public synchronized long lastTimestamp() {
        return lastTimestamp;
    }

    private long handleClockBackward(long now) {
        long backward = lastTimestamp - now;
        if (backward > MAX_BACKWARD_MS) {
            throw new ClockMovedBackwardsException(backward);
        }
        try {
            // 小幅回拨：睡到时钟追上来（多睡一倍，给 NTP 渐进调整留余量）
            sleeper.sleep(backward << 1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClockMovedBackwardsException(backward);
        }
        long after = clock.getAsLong();
        if (after < lastTimestamp) {
            throw new ClockMovedBackwardsException(lastTimestamp - after);
        }
        return after;
    }

    private long waitUntilAfter(long timestamp) {
        long now = clock.getAsLong();
        while (now <= timestamp) {
            Thread.onSpinWait();
            now = clock.getAsLong();
        }
        return now;
    }

    public static long timestampOf(long id) {
        return (id >>> (WORKER_ID_BITS + SEQUENCE_BITS)) + EPOCH;
    }

    public static long workerIdOf(long id) {
        return (id >>> SEQUENCE_BITS) & MAX_WORKER_ID;
    }

    public static long sequenceOf(long id) {
        return id & SEQUENCE_MASK;
    }

    /** 可替换的睡眠，测试里用来模拟"睡完以后时钟追上来了" */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    /** 时钟回拨超过容忍范围 */
    public static class ClockMovedBackwardsException extends IllegalStateException {
        public ClockMovedBackwardsException(long backwardMillis) {
            super("服务器时钟回拨了 " + backwardMillis + " 毫秒，拒绝生成 id");
        }
    }
}

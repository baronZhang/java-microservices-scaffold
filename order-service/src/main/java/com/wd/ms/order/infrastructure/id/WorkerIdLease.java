package com.wd.ms.order.infrastructure.id;

import com.wd.ms.common.core.UtcTimes;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 雪花算法的 workerId 分配：数据库租约（思路同百度 uid-generator 的 WORKER_NODE 表；美团 Leaf 的雪花模式则用 ZooKeeper 顺序节点分配）。
 *
 * <ul>
 *   <li>启动时：优先复用已过期的租约（实例下线后机器号可以回收），没有就新插一行，主键冲突说明被别的实例抢了，重试
 *   <li>运行中：每 10 秒心跳续约，同时记下"已经用到的最大时间戳"
 *   <li>续约失败（比如长时间 GC 停顿导致租约过期、被别的实例接手）：立即停止发号，避免两个实例用同一个 workerId
 *   <li>重启时：如果当前时间小于上次记录的最大时间戳，说明发生过时钟回拨，拒绝启动
 * </ul>
 *
 * <p>为什么不手工配置 workerId：K8s 里实例是弹性伸缩的，Pod 名字和数量都不固定，手工配置迟早会配重。
 */
@Component
public class WorkerIdLease {

    private static final Logger log = LoggerFactory.getLogger(WorkerIdLease.class);
    static final Duration LEASE_TTL = Duration.ofSeconds(60);

    private final JdbcClient jdbc;
    private final Clock clock;
    private final String instanceId;
    private volatile int workerId = -1;
    private volatile boolean valid;
    private volatile SnowflakeIdGenerator generator;

    public WorkerIdLease(JdbcClient jdbc, Clock clock, @Value("${server.port:0}") int port) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.instanceId =
                hostName() + ":" + port + ":" + UUID.randomUUID().toString().substring(0, 8);
    }

    @PostConstruct
    public void acquire() {
        for (int attempt = 1; attempt <= 5; attempt++) {
            Optional<Integer> claimed = claimExpired().or(this::claimNew);
            if (claimed.isPresent()) {
                workerId = claimed.get();
                checkClock();
                generator = new SnowflakeIdGenerator(workerId);
                valid = true;
                log.info("雪花算法 workerId = {}（实例 {}）", workerId, instanceId);
                return;
            }
        }
        throw new IllegalStateException("分配 workerId 失败");
    }

    public long nextId() {
        if (!valid) {
            throw new IllegalStateException("workerId 租约已失效，停止发号");
        }
        return generator.nextId();
    }

    public int workerId() {
        return workerId;
    }

    /** 心跳续约。条件里带上 instance_id：租约已经被别人接手的话，一行都更新不了 */
    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    public void heartbeat() {
        if (!valid) {
            return;
        }
        // GREATEST：最大时间戳只增不减。新实例接手 workerId 后还没发过号时，generator.lastTimestamp() 是 -1，
        // 直接覆盖会把前任留下的最大时间戳冲掉，重启时的时钟回拨自检就失效了（写文档时审代码发现的缺陷，WorkerIdLeaseIT 守着）
        int updated = jdbc.sql("""
                        UPDATE worker_node SET heartbeat_at = ?, last_timestamp = GREATEST(last_timestamp, ?)
                        WHERE worker_id = ? AND instance_id = ?
                        """)
                .params(UtcTimes.toDb(clock.instant()), generator.lastTimestamp(), workerId, instanceId)
                .update();
        if (updated == 0) {
            valid = false;
            log.error("workerId {} 的租约已丢失（可能被其他实例接手），停止发号", workerId);
        }
    }

    /** 正常下线时把租约置为过期，让机器号可以马上被别的实例复用；同时写入最终的最大时间戳（心跳最多落后 10 秒） */
    @PreDestroy
    public void release() {
        if (workerId >= 0 && generator != null) {
            jdbc.sql("""
                    UPDATE worker_node SET heartbeat_at = ?, last_timestamp = GREATEST(last_timestamp, ?)
                    WHERE worker_id = ? AND instance_id = ?
                    """)
                    .params(
                            UtcTimes.toDb(Instant.EPOCH.plusSeconds(86_400)),
                            generator.lastTimestamp(),
                            workerId,
                            instanceId)
                    .update();
        }
    }

    /** 测试和运维排查用：库里记录的最大时间戳 */
    long recordedLastTimestamp() {
        return jdbc.sql("SELECT last_timestamp FROM worker_node WHERE worker_id = ?")
                .param(workerId)
                .query(Long.class)
                .single();
    }

    private Optional<Integer> claimExpired() {
        var expiredBefore = UtcTimes.toDb(clock.instant().minus(LEASE_TTL));
        Optional<Integer> candidate = jdbc.sql(
                        "SELECT worker_id FROM worker_node WHERE heartbeat_at < ? ORDER BY worker_id LIMIT 1")
                .param(expiredBefore)
                .query(Integer.class)
                .optional();
        // 用条件更新"抢"这个过期租约：两个实例同时看中同一个机器号，只有一个能更新成功
        return candidate.filter(id -> jdbc.sql(
                                "UPDATE worker_node SET instance_id = ?, heartbeat_at = ? WHERE worker_id = ? AND heartbeat_at < ?")
                        .params(instanceId, UtcTimes.toDb(clock.instant()), id, expiredBefore)
                        .update()
                == 1);
    }

    private Optional<Integer> claimNew() {
        int next = jdbc.sql("SELECT COALESCE(MAX(worker_id), -1) + 1 FROM worker_node")
                .query(Integer.class)
                .single();
        if (next > SnowflakeIdGenerator.MAX_WORKER_ID) {
            throw new IllegalStateException("workerId 已用完（最多 1024 个）");
        }
        try {
            jdbc.sql(
                            "INSERT INTO worker_node (worker_id, instance_id, heartbeat_at, last_timestamp) VALUES (?, ?, ?, 0)")
                    .params(next, instanceId, UtcTimes.toDb(clock.instant()))
                    .update();
            return Optional.of(next);
        } catch (DuplicateKeyException e) {
            return Optional.empty();
        }
    }

    private void checkClock() {
        long lastTimestamp = jdbc.sql("SELECT last_timestamp FROM worker_node WHERE worker_id = ?")
                .param(workerId)
                .query(Long.class)
                .single();
        long now = clock.millis();
        if (now < lastTimestamp) {
            throw new IllegalStateException(
                    "当前时间比 workerId " + workerId + " 上次发号的时间早 " + (lastTimestamp - now) + " 毫秒，可能发生了时钟回拨，拒绝启动");
        }
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}

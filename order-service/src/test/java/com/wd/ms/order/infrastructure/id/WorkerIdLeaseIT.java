package com.wd.ms.order.infrastructure.id;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * workerId 租约里记录的"最大时间戳"只能增大。
 * 场景：前任实例发号到了某个时间点后下线，新实例接手同一个 workerId，还没发过号就心跳了一次——
 * 这时绝不能把前任的最大时间戳覆盖成 -1，否则下次重启的时钟回拨自检就失效了。
 */
@SpringBootTest
@ActiveProfiles("test")
class WorkerIdLeaseIT {

    @Autowired
    WorkerIdLease lease;

    @Autowired
    JdbcClient jdbc;

    @Test
    void 心跳不会把记录的最大时间戳改小() {
        long predecessor = System.currentTimeMillis() + 60_000;
        jdbc.sql("UPDATE worker_node SET last_timestamp = ? WHERE worker_id = ?")
                .params(predecessor, lease.workerId())
                .update();

        lease.heartbeat();
        assertThat(lease.recordedLastTimestamp()).isEqualTo(predecessor);

        lease.nextId();
        lease.heartbeat();
        assertThat(lease.recordedLastTimestamp()).isEqualTo(predecessor);
    }

    @Test
    void 发号后心跳会推进最大时间戳() {
        jdbc.sql("UPDATE worker_node SET last_timestamp = 0 WHERE worker_id = ?")
                .param(lease.workerId())
                .update();
        long id = lease.nextId();
        lease.heartbeat();
        assertThat(lease.recordedLastTimestamp()).isEqualTo(SnowflakeIdGenerator.timestampOf(id));
    }
}

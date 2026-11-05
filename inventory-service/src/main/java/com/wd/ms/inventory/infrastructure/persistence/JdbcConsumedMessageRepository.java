package com.wd.ms.inventory.infrastructure.persistence;

import com.wd.ms.common.core.UtcTimes;
import com.wd.ms.inventory.domain.message.ConsumedMessageRepository;
import java.time.Clock;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 已消费消息表的 JDBC 实现。生产上要定期清理很久以前的记录（重投窗口之外的不再需要判重）。 */
@Repository
public class JdbcConsumedMessageRepository implements ConsumedMessageRepository {

    private final JdbcClient jdbc;
    private final Clock clock;

    public JdbcConsumedMessageRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public boolean exists(String messageId) {
        return jdbc.sql("SELECT COUNT(*) FROM consumed_message WHERE message_id = ?")
                        .param(messageId)
                        .query(Long.class)
                        .single()
                > 0;
    }

    @Override
    public void insert(String messageId, String topic) {
        jdbc.sql("INSERT INTO consumed_message (message_id, topic, consumed_at) VALUES (?, ?, ?)")
                .params(messageId, topic, UtcTimes.toDb(clock.instant()))
                .update();
    }
}

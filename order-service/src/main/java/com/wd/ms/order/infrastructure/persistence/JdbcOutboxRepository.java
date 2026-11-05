package com.wd.ms.order.infrastructure.persistence;

import com.wd.ms.common.core.UtcTimes;
import com.wd.ms.order.domain.outbox.OutboxEvent;
import com.wd.ms.order.domain.outbox.OutboxRepository;
import com.wd.ms.order.domain.outbox.OutboxStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 本地消息表的 JDBC 实现。 */
@Repository
public class JdbcOutboxRepository implements OutboxRepository {

    private final JdbcClient jdbc;

    public JdbcOutboxRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(String eventId, String aggregateId, String type, String payload, Instant now) {
        jdbc.sql("""
                INSERT INTO outbox_event (event_id, aggregate_id, event_type, payload, status, attempts,
                                          next_attempt_at, created_at)
                VALUES (?, ?, ?, ?, 'PENDING', 0, ?, ?)
                """)
                .params(eventId, aggregateId, type, payload, UtcTimes.toDb(now), UtcTimes.toDb(now))
                .update();
    }

    @Override
    public List<OutboxEvent> findDue(Instant now, int limit) {
        return jdbc.sql("""
                SELECT * FROM outbox_event WHERE status = 'PENDING' AND next_attempt_at <= ?
                ORDER BY id LIMIT ?
                """)
                .params(UtcTimes.toDb(now), limit)
                .query(JdbcOutboxRepository::map)
                .list();
    }

    @Override
    public List<OutboxEvent> findUnsent(int limit) {
        return jdbc.sql("SELECT * FROM outbox_event WHERE status <> 'SENT' ORDER BY id DESC LIMIT ?")
                .param(limit)
                .query(JdbcOutboxRepository::map)
                .list();
    }

    private static OutboxEvent map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new OutboxEvent(
                rs.getLong("id"),
                rs.getString("event_id"),
                rs.getString("aggregate_id"),
                rs.getString("event_type"),
                rs.getString("payload"),
                OutboxStatus.valueOf(rs.getString("status")),
                rs.getInt("attempts"),
                UtcTimes.fromDb(rs.getObject("next_attempt_at", LocalDateTime.class)));
    }

    @Override
    public void markSent(long id, Instant now) {
        jdbc.sql("UPDATE outbox_event SET status = 'SENT', sent_at = ?, last_error = NULL WHERE id = ?")
                .params(UtcTimes.toDb(now), id)
                .update();
    }

    @Override
    public void markRetry(long id, int attempts, Instant nextAttemptAt, String error) {
        jdbc.sql("UPDATE outbox_event SET attempts = ?, next_attempt_at = ?, last_error = ? WHERE id = ?")
                .params(attempts, UtcTimes.toDb(nextAttemptAt), error, id)
                .update();
    }

    @Override
    public void markDead(long id, int attempts, String error) {
        jdbc.sql("UPDATE outbox_event SET status = 'DEAD', attempts = ?, last_error = ? WHERE id = ?")
                .params(attempts, error, id)
                .update();
    }

    @Override
    public long countByStatus(OutboxStatus status) {
        return jdbc.sql("SELECT COUNT(*) FROM outbox_event WHERE status = ?")
                .param(status.name())
                .query(Long.class)
                .single();
    }
}

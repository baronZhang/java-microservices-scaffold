package com.wd.ms.order.domain.outbox;

import java.time.Instant;
import java.util.List;

/** 本地消息表仓储。 */
public interface OutboxRepository {

    void insert(String eventId, String aggregateId, String type, String payload, Instant now);

    /** 到了投递时间的待投递事件，按 id 升序 */
    List<OutboxEvent> findDue(Instant now, int limit);

    void markSent(long id, Instant now);

    void markRetry(long id, int attempts, Instant nextAttemptAt, String error);

    void markDead(long id, int attempts, String error);

    long countByStatus(OutboxStatus status);

    /** 还没投递成功的事件（PENDING / DEAD），最新的在前，运维排查用 */
    List<OutboxEvent> findUnsent(int limit);
}

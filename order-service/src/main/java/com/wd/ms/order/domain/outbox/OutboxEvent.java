package com.wd.ms.order.domain.outbox;

import java.time.Instant;

/**
 * 本地消息表（Outbox）里的一条事件。和订单状态变更写在同一个本地事务里：
 * 订单改成功了，事件一定也落库了；事务回滚，事件也不存在——这就把"改库 + 发消息"变成了一个原子操作。
 *
 * @param id 自增主键，投递按它的顺序进行
 * @param eventId 事件全局唯一 id，消费方用它判重
 * @param aggregateId 订单号
 * @param payload 事件内容（JSON）
 */
public record OutboxEvent(
        long id,
        String eventId,
        String aggregateId,
        String type,
        String payload,
        OutboxStatus status,
        int attempts,
        Instant nextAttemptAt) {}

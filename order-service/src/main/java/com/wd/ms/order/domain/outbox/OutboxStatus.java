package com.wd.ms.order.domain.outbox;

/** 本地消息表的状态：待投递 → 已投递；多次失败后进入 DEAD，需要人工介入。 */
public enum OutboxStatus {
    PENDING,
    SENT,
    DEAD
}

package com.wd.ms.inventory.domain.message;

/**
 * 已消费消息表：消费者幂等的关键。和业务修改在同一个本地事务里插入一条"我处理过它"的记录，
 * 重复投递的消息查到记录就直接跳过。
 */
public interface ConsumedMessageRepository {

    boolean exists(String messageId);

    void insert(String messageId, String topic);
}

package com.wd.ms.order.domain.outbox;

/**
 * 把一条事件投递出去（出站端口）。默认实现是 HTTP 推送给库存服务，kafka profile 下换成 Kafka。
 * 投递失败必须抛异常，消息中继据此安排重试；正常返回才算对方确认收到。
 */
public interface EventPublisher {

    void publish(OutboxEvent event);
}

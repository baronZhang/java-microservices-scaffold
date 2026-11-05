package com.wd.ms.order.infrastructure.messaging;

import com.wd.ms.order.domain.outbox.EventPublisher;
import com.wd.ms.order.domain.outbox.OutboxEvent;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * kafka profile 的投递方式：发到 order-events 主题，以订单号为 key（同一订单的事件进同一个分区，保证顺序）。
 *
 * <p>同步等待 broker 确认（acks=all）后才返回，消息中继据此把事件标记为已投递；
 * 等待超时就抛异常，下次重投——可能产生重复，消费方幂等兜底。
 */
@Component
@ConditionalOnProperty(prefix = "ms.order", name = "event-transport", havingValue = "kafka")
public class KafkaEventPublisher implements EventPublisher {

    static final String TOPIC = "order-events";
    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(OutboxEvent event) {
        try {
            kafkaTemplate.send(TOPIC, event.aggregateId(), event.payload()).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("投递到 Kafka 被中断", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("投递到 Kafka 失败：" + e, e);
        }
    }
}

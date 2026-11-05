package com.wd.ms.inventory.interfaces.messaging;

import com.wd.ms.inventory.application.OrderEvent;
import com.wd.ms.inventory.application.OrderEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * kafka profile 的消息入口。监听方法正常返回后 Spring Kafka 才提交 offset；
 * 处理中抛异常会按错误处理器的策略重试，重试耗尽进死信主题（见 KafkaConsumerConfiguration）。
 *
 * <p>"处理完、提交 offset 前进程挂了"这种情况，重启后会再收到同一条消息——所以和 HTTP 入口一样依赖幂等。
 */
@Component
@Profile("kafka")
public class OrderEventKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventKafkaListener.class);
    private final OrderEventHandler handler;
    private final JsonMapper jsonMapper;

    public OrderEventKafkaListener(OrderEventHandler handler, JsonMapper jsonMapper) {
        this.handler = handler;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = OrderEventHandler.TOPIC, groupId = "inventory-service")
    public void onMessage(String payload) {
        OrderEvent event = jsonMapper.readValue(payload, OrderEvent.class);
        log.info("从 Kafka 收到订单事件 {} {} {}", event.eventId(), event.type(), event.orderNo());
        handler.handle(event);
    }
}

package com.wd.ms.inventory.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * kafka profile 的消费端错误处理：失败后间隔 1 秒重试 3 次，还不行就把消息转发到死信主题 order-events-dlt，
 * 不阻塞后面的消息。死信要有人看（告警 + 人工处理），否则等于悄悄丢消息。
 */
@Configuration(proxyBeanMethods = false)
@Profile("kafka")
public class KafkaConsumerConfiguration {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        var recoverer = new DeadLetterPublishingRecoverer(
                template, (record, ex) -> new org.apache.kafka.common.TopicPartition(record.topic() + "-dlt", -1));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    }
}

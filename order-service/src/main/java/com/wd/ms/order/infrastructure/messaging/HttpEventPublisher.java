package com.wd.ms.order.infrastructure.messaging;

import com.wd.ms.common.core.Result;
import com.wd.ms.order.domain.outbox.EventPublisher;
import com.wd.ms.order.domain.outbox.OutboxEvent;
import com.wd.ms.order.infrastructure.client.InventoryClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 默认 profile 的投递方式：HTTP 直接推给库存服务的内部接口（经负载均衡）。
 *
 * <p>为什么默认不用 MQ：本机没有消息中间件也要能跑通核心链路。本地消息表保证了"一定会投递"，
 * HTTP 推送 + 失败重试 + 消费幂等，在消费方只有一个的场景下和 MQ 的效果相同；
 * 消费方变多（还有积分、通知……）时，就该换成 MQ 的发布订阅，见 kafka profile。
 */
@Component
@ConditionalOnProperty(prefix = "ms.order", name = "event-transport", havingValue = "http", matchIfMissing = true)
public class HttpEventPublisher implements EventPublisher {

    private final InventoryClient inventoryClient;

    public HttpEventPublisher(InventoryClient inventoryClient) {
        this.inventoryClient = inventoryClient;
    }

    @Override
    public void publish(OutboxEvent event) {
        Result<Object> result = inventoryClient.deliverOrderEvent(event.payload());
        if (result == null || !result.succeeded()) {
            throw new IllegalStateException("库存服务没有确认事件：" + (result == null ? "空响应" : result.message()));
        }
    }
}

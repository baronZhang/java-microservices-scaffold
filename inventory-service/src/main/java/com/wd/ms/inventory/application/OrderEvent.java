package com.wd.ms.inventory.application;

import java.time.Instant;

/**
 * 库存服务能理解的订单事件。消费方自己定义事件结构（而不是依赖订单服务的 jar），
 * 两边的一致性由 contracts/ 目录下的契约样例和契约测试保证。
 *
 * @param eventId 事件唯一 id，消费幂等用它判重
 * @param type ORDER_PAID 或 ORDER_CANCELLED
 */
public record OrderEvent(String eventId, String type, String orderNo, Instant occurredAt) {

    public static final String ORDER_PAID = "ORDER_PAID";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
}

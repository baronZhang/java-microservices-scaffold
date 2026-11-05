package com.wd.ms.order.application;

import com.wd.ms.order.domain.order.Order;

/**
 * 下单结果。replayed = true 表示这是同一个幂等键的重复提交，返回的是第一次创建的订单
 * （接口层据此加响应头 Idempotent-Replayed: true，客户端能区分"新下的单"和"重放的结果"）。
 */
public record CreateOrderResult(Order order, boolean replayed) {}

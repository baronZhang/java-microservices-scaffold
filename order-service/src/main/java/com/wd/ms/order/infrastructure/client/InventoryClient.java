package com.wd.ms.order.infrastructure.client;

import com.wd.ms.common.core.Result;
import java.util.List;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 库存服务的内部接口。DTO 由消费方（订单服务）自己定义，不依赖库存服务的 jar；
 * 两边是否对得上，由 contracts/order-to-inventory 下的契约样例 + 双方的契约测试保证。
 */
public interface InventoryClient {

    @PostExchange("/internal/inventory/reservations")
    Result<ReservationDto> reserve(@RequestBody ReserveRequest request);

    /** 消息中继用：把本地消息表里的事件原样推给库存服务 */
    @PostExchange(value = "/internal/events/order", contentType = "application/json")
    Result<Object> deliverOrderEvent(@RequestBody String payload);

    record ReserveRequest(String orderNo, List<Item> items) {}

    record Item(long skuId, int quantity) {}

    record ReservationDto(String orderNo, boolean reserved, String message) {}
}

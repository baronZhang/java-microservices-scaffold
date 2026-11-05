package com.wd.ms.inventory.interfaces.internal;

import com.wd.ms.common.core.Result;
import com.wd.ms.inventory.application.OrderEvent;
import com.wd.ms.inventory.application.OrderEventHandler;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 默认 profile 的消息入口：订单服务的消息中继（OutboxRelay）把本地消息表里的事件 POST 到这里。
 * 返回 2xx 才算投递成功；超时、5xx 都会被重投，所以这里必须幂等（由 OrderEventHandler 保证）。
 */
@RestController
public class OrderEventInternalController {

    private final OrderEventHandler handler;

    public OrderEventInternalController(OrderEventHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/internal/events/order")
    public Result<Map<String, Boolean>> receive(@RequestBody OrderEvent event) {
        boolean processed = handler.handle(event);
        return Result.ok(Map.of("processed", processed));
    }
}

package com.wd.ms.order.interfaces.ops;

import com.wd.ms.order.application.OutboxQueryService;
import java.util.Map;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

/**
 * 运维端点 GET /actuator/outbox：本地消息表里有多少待投递、已投递、死信。
 * 生产上这几个数字要做成监控指标并配告警：pending 持续增长说明下游不可用或中继卡住，dead 大于 0 需要人工处理。
 */
@Component
@Endpoint(id = "outbox")
public class OutboxEndpoint {

    private final OutboxQueryService queryService;

    public OutboxEndpoint(OutboxQueryService queryService) {
        this.queryService = queryService;
    }

    @ReadOperation
    public Map<String, Object> outbox() {
        return queryService.snapshot();
    }
}

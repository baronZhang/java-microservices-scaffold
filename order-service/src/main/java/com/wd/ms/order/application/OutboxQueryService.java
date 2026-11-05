package com.wd.ms.order.application;

import com.wd.ms.order.domain.outbox.OutboxEvent;
import com.wd.ms.order.domain.outbox.OutboxRepository;
import com.wd.ms.order.domain.outbox.OutboxStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 本地消息表的运维视图：各状态的数量（积压 / 死信监控的指标来源）和最近没投递成功的事件。 */
@Service
public class OutboxQueryService {

    private final OutboxRepository outbox;

    public OutboxQueryService(OutboxRepository outbox) {
        this.outbox = outbox;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (OutboxStatus status : OutboxStatus.values()) {
            result.put(status.name().toLowerCase(), outbox.countByStatus(status));
        }
        List<Map<String, Object>> unsent =
                outbox.findUnsent(20).stream().map(OutboxQueryService::view).toList();
        result.put("unsent", unsent);
        return result;
    }

    private static Map<String, Object> view(OutboxEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", event.id());
        map.put("type", event.type());
        map.put("orderNo", event.aggregateId());
        map.put("status", event.status().name());
        map.put("attempts", event.attempts());
        map.put("nextAttemptAt", String.valueOf(event.nextAttemptAt()));
        return map;
    }
}

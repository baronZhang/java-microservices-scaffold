package com.wd.ms.order.infrastructure.id;

import com.wd.ms.order.domain.order.OrderNoGenerator;
import org.springframework.stereotype.Component;

/** 订单号 = 雪花 id 的十进制字符串（最多 19 位）。 */
@Component
public class SnowflakeOrderNoGenerator implements OrderNoGenerator {

    private final WorkerIdLease lease;

    public SnowflakeOrderNoGenerator(WorkerIdLease lease) {
        this.lease = lease;
    }

    @Override
    public String next() {
        return Long.toString(lease.nextId());
    }
}

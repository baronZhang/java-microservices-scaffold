package com.wd.ms.order.domain.order;

/**
 * 订单状态机：
 *
 * <pre>
 * RESERVING（预占库存中）──预占成功──▶ PENDING_PAYMENT（待支付）──支付──▶ PAID
 *     │                                   │
 *     └─库存不足 / 库存服务不可用 / 卡住超时    └─用户取消 / 支付超时
 *                    ▼                                 ▼
 *                CANCELLED ◀──────────────────────────┘
 * </pre>
 *
 * <p>所有迁移都用"条件更新"落库（WHERE status = 旧状态），并发的两个迁移（比如支付和超时取消）只有一个能成功。
 */
public enum OrderStatus {
    RESERVING,
    PENDING_PAYMENT,
    PAID,
    CANCELLED
}

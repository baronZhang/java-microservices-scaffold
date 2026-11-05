package com.wd.ms.order.domain.order;

import com.wd.ms.common.core.PageQuery;
import com.wd.ms.common.core.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 订单仓储。 */
public interface OrderRepository {

    /** 插入订单和明细。(user_id, idempotency_key) 有唯一约束，重复提交会抛 DuplicateKeyException */
    void insert(Order order, Instant now);

    Optional<Order> find(String orderNo);

    Optional<Order> findByIdempotencyKey(long userId, String idempotencyKey);

    PageResult<Order> pageByUser(long userId, PageQuery page);

    /**
     * 条件更新：只有库里的状态仍然是 {@code expectedStatus} 时才把订单更新成 {@code target}。
     * 这就是订单状态机的"乐观锁"：并发迁移只有一个成功，失败的一方重新读取再决定怎么办。
     */
    boolean transition(Order target, OrderStatus expectedStatus, Instant now);

    /** 超时未支付的订单（status = PENDING_PAYMENT 且 expire_at <= now） */
    List<Order> findExpired(Instant now, int limit);

    /** 卡在 RESERVING 超过阈值的订单 */
    List<Order> findStuckReserving(Instant createdBefore, int limit);
}

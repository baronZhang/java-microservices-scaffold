package com.wd.ms.inventory.domain.reservation;

import java.util.Optional;

/** 预占记录仓储。 */
public interface ReservationRepository {

    Optional<Reservation> find(String orderNo);

    /** 插入预占记录（含明细）。订单号是主键，重复插入会抛 DuplicateKeyException：这就是并发重复预占的兜底 */
    void insert(Reservation reservation);

    /** 状态迁移：只有当前状态等于 from 时才更新，返回是否更新成功 */
    boolean transition(String orderNo, ReservationStatus from, ReservationStatus to);
}

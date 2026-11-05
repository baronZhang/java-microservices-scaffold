package com.wd.ms.inventory.domain.reservation;

/**
 * 预占记录的状态机：
 *
 * <pre>
 * RESERVED ──支付──▶ CONFIRMED
 *    │
 *    └──取消/超时──▶ RELEASED
 * （从未预占过就收到释放：直接记一条 RELEASED，即"空回滚"，用来挡住迟到的预占请求）
 * </pre>
 */
public enum ReservationStatus {
    RESERVED,
    CONFIRMED,
    RELEASED
}

package com.wd.ms.order.domain.order;

/** 订单取消原因 */
public enum CancelReason {
    /** 用户主动取消 */
    USER,
    /** 超时未支付 */
    TIMEOUT,
    /** 库存不足：库存服务明确拒绝，什么都没扣，不需要补偿 */
    STOCK_INSUFFICIENT,
    /** 库存服务不可用（超时、5xx、熔断）：不知道扣没扣成功，要发释放事件兜底 */
    INVENTORY_UNAVAILABLE,
    /** 预占阶段卡住（比如进程在调用库存服务后崩溃），由恢复任务取消 */
    RESERVE_STUCK
}

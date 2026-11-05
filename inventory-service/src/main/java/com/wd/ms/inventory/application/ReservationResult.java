package com.wd.ms.inventory.application;

/**
 * 预占结果。
 *
 * @param reserved true 表示预占成功（包括"之前已经成功过"的重复请求）
 * @param message 失败原因，比如"库存不足：商品 6"或"订单已取消"
 */
public record ReservationResult(String orderNo, boolean reserved, String message) {

    static ReservationResult ok(String orderNo) {
        return new ReservationResult(orderNo, true, "ok");
    }

    static ReservationResult rejected(String orderNo, String message) {
        return new ReservationResult(orderNo, false, message);
    }
}

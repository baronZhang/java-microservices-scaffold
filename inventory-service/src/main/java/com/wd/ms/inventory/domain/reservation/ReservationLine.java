package com.wd.ms.inventory.domain.reservation;

/** 预占明细：哪个商品、多少件。 */
public record ReservationLine(long skuId, int quantity) {

    public ReservationLine {
        if (quantity <= 0) {
            throw new IllegalArgumentException("数量必须大于 0");
        }
    }
}

package com.wd.ms.inventory.domain.reservation;

import java.util.List;

/** 一个订单的库存预占记录。订单号就是幂等键：同一个订单不管请求多少次，只预占一次。 */
public record Reservation(String orderNo, ReservationStatus status, List<ReservationLine> lines) {

    public Reservation {
        lines = List.copyOf(lines);
    }
}

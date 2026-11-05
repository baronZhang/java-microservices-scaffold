package com.wd.ms.order.domain.order;

import java.math.BigDecimal;

/** 订单明细。商品名称和单价是下单时的快照：商品后来改名、调价都不影响已有订单。 */
public record OrderItem(long skuId, String productName, BigDecimal unitPrice, int quantity) {

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}

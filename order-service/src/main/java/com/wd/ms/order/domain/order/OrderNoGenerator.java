package com.wd.ms.order.domain.order;

/** 订单号生成器。领域层不关心算法；实现是雪花算法（见 infrastructure/id）。 */
public interface OrderNoGenerator {

    String next();
}

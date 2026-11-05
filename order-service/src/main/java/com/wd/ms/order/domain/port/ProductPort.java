package com.wd.ms.order.domain.port;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/** 查询商品快照（名称、价格、是否在售）。商品服务不可用时抛 BizException(SERVICE_UNAVAILABLE)。 */
public interface ProductPort {

    Map<Long, ProductSnapshot> findProducts(Collection<Long> skuIds);

    record ProductSnapshot(long id, String name, BigDecimal price, boolean onSale) {}
}

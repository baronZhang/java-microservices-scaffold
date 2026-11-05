package com.wd.ms.product.domain.product;

import java.math.BigDecimal;

/** 商品。演示简化：一个商品就是一个 SKU，商品 id 也是库存服务里的 skuId。 */
public record Product(long id, String name, BigDecimal price, String description, boolean onSale) {}

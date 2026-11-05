package com.wd.ms.order.infrastructure.client;

import com.wd.ms.common.core.Result;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;

/** 商品服务的内部接口：批量查询下单需要的商品快照。 */
public interface ProductClient {

    @GetExchange("/internal/products")
    Result<List<ProductDto>> products(@RequestParam List<Long> ids);

    record ProductDto(long id, String name, BigDecimal price, boolean onSale) {}
}

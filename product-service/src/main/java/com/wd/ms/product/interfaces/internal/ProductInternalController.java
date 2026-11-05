package com.wd.ms.product.interfaces.internal;

import com.wd.ms.common.core.Result;
import com.wd.ms.product.application.ProductQueryService;
import com.wd.ms.product.domain.product.Product;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 内部接口：订单服务下单时批量查询商品名称和价格。 */
@RestController
public class ProductInternalController {

    private final ProductQueryService queryService;

    public ProductInternalController(ProductQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/internal/products")
    public Result<List<Product>> list(@RequestParam List<Long> ids) {
        return Result.ok(queryService.findByIds(ids));
    }
}

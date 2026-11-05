package com.wd.ms.product.interfaces.web;

import com.wd.ms.common.core.PageQuery;
import com.wd.ms.common.core.PageResult;
import com.wd.ms.common.core.Result;
import com.wd.ms.product.application.ProductQueryService;
import com.wd.ms.product.application.ProductQueryService.ProductWithStock;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 商品接口（经网关 /api/products/**），登录即可访问。 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductQueryService queryService;

    public ProductController(ProductQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public Result<PageResult<ProductResponse>> page(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.ok(
                queryService.page(keyword, PageQuery.of(page, pageSize)).map(ProductResponse::from));
    }

    @GetMapping("/{id}")
    public Result<ProductResponse> get(@PathVariable long id) {
        return Result.ok(ProductResponse.from(queryService.get(id)));
    }

    /**
     * @param stock 可售数量；库存服务不可用时为 null
     * @param stockStatus IN_STOCK / LOW_STOCK / SOLD_OUT / UNKNOWN（已降级）
     */
    public record ProductResponse(
            long id, String name, BigDecimal price, String description, Integer stock, String stockStatus) {

        static ProductResponse from(ProductWithStock item) {
            var product = item.product();
            return new ProductResponse(
                    product.id(),
                    product.name(),
                    product.price(),
                    product.description(),
                    item.stock().available(),
                    item.stock().level().name());
        }
    }
}

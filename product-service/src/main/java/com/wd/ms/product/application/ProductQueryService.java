package com.wd.ms.product.application;

import com.wd.ms.common.core.BizException;
import com.wd.ms.common.core.PageQuery;
import com.wd.ms.common.core.PageResult;
import com.wd.ms.product.domain.product.Product;
import com.wd.ms.product.domain.product.ProductRepository;
import com.wd.ms.product.domain.product.StockQueryPort;
import com.wd.ms.product.domain.product.StockView;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 商品查询：本地查商品，再批量查库存拼在一起。 */
@Service
public class ProductQueryService {

    private final ProductRepository products;
    private final StockQueryPort stockQuery;

    public ProductQueryService(ProductRepository products, StockQueryPort stockQuery) {
        this.products = products;
        this.stockQuery = stockQuery;
    }

    public PageResult<ProductWithStock> page(String keyword, PageQuery page) {
        PageResult<Product> result = products.page(keyword, page);
        // 一页商品只发一次库存查询（批量），避免 N 个商品发 N 次远程调用
        Map<Long, StockView> stocks =
                stockQuery.query(result.list().stream().map(Product::id).toList());
        return result.map(
                product -> new ProductWithStock(product, stocks.getOrDefault(product.id(), StockView.unknown())));
    }

    public ProductWithStock get(long id) {
        Product product = products.findById(id).orElseThrow(() -> BizException.notFound("商品 " + id + " 不存在"));
        StockView stock = stockQuery.query(List.of(id)).getOrDefault(id, StockView.unknown());
        return new ProductWithStock(product, stock);
    }

    /** 给订单服务用：下单时以服务端价格为准，绝不信任前端传来的价格 */
    public List<Product> findByIds(Collection<Long> ids) {
        return products.findByIds(ids);
    }

    public record ProductWithStock(Product product, StockView stock) {}
}

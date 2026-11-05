package com.wd.ms.product.infrastructure.client;

import com.wd.ms.common.core.Result;
import java.util.List;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;

/**
 * 库存服务的 HTTP Interface 客户端：只写接口，实现由 Spring 生成（和 OpenFeign 的写法很像，但是 Spring 官方的）。
 *
 * <p>属于 "inventory-service" 这个客户端分组：
 * 分组名就是服务名，Spring Cloud 自动把基础地址设成 http://inventory-service 并接上负载均衡；
 * 分组配置了降级类，所以每次调用都包在名为 inventory-service 的熔断器里（见 HttpClientConfiguration）。
 */
public interface InventoryClient {

    @GetExchange("/internal/inventory/stocks")
    Result<List<StockDto>> stocks(@RequestParam List<Long> skuIds);

    /** 消费方自己定义需要的字段（容错读者）：库存服务加字段不影响这里 */
    record StockDto(long skuId, int available) {}
}

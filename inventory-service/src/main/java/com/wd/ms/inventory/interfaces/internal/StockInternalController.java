package com.wd.ms.inventory.interfaces.internal;

import com.wd.ms.common.core.Result;
import com.wd.ms.inventory.application.StockService;
import com.wd.ms.inventory.domain.stock.Stock;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 内部接口：商品服务展示库存时批量查询（一次查多个，避免 N+1 次远程调用）。 */
@RestController
public class StockInternalController {

    private final StockService stockService;

    public StockInternalController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/internal/inventory/stocks")
    public Result<List<Stock>> list(@RequestParam List<Long> skuIds) {
        return Result.ok(stockService.list(skuIds));
    }
}

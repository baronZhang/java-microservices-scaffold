package com.wd.ms.inventory.interfaces.web;

import com.wd.ms.common.core.Result;
import com.wd.ms.inventory.application.StockService;
import com.wd.ms.inventory.domain.stock.Stock;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 对外的库存接口（经网关 /api/inventory/**）。 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final StockService stockService;

    public InventoryController(StockService stockService) {
        this.stockService = stockService;
    }

    /** 登录即可查看 */
    @GetMapping("/{skuId}")
    public Result<Stock> get(@PathVariable long skuId) {
        return Result.ok(stockService.get(skuId));
    }

    /** 调整可售数量：需要 inventory:edit 权限，演示账号里只有管理员（权限码 *）满足 */
    @PutMapping("/{skuId}")
    @PreAuthorize("@perm.has('inventory:edit')")
    public Result<Stock> setAvailable(@PathVariable long skuId, @RequestBody AdjustRequest request) {
        return Result.ok(stockService.setAvailable(skuId, request.available()));
    }

    public record AdjustRequest(int available) {}
}

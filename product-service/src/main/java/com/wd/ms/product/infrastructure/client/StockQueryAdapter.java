package com.wd.ms.product.infrastructure.client;

import com.wd.ms.common.core.Result;
import com.wd.ms.product.domain.product.StockQueryPort;
import com.wd.ms.product.domain.product.StockView;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** {@link StockQueryPort} 的实现：调用库存服务；降级时（业务码非 0）返回空结果，上层显示"库存未知"。 */
@Component
public class StockQueryAdapter implements StockQueryPort {

    private final InventoryClient inventoryClient;

    public StockQueryAdapter(InventoryClient inventoryClient) {
        this.inventoryClient = inventoryClient;
    }

    @Override
    public Map<Long, StockView> query(Collection<Long> skuIds) {
        if (skuIds.isEmpty()) {
            return Map.of();
        }
        Result<List<InventoryClient.StockDto>> result = inventoryClient.stocks(List.copyOf(skuIds));
        if (result == null || !result.succeeded() || result.data() == null) {
            return Map.of();
        }
        return result.data().stream()
                .collect(Collectors.toMap(InventoryClient.StockDto::skuId, stock -> StockView.of(stock.available())));
    }
}

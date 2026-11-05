package com.wd.ms.inventory.application;

import com.wd.ms.common.core.BizException;
import com.wd.ms.inventory.domain.stock.Stock;
import com.wd.ms.inventory.domain.stock.StockRepository;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 库存查询与管理员调整。 */
@Service
public class StockService {

    private final StockRepository stocks;

    public StockService(StockRepository stocks) {
        this.stocks = stocks;
    }

    @Transactional(readOnly = true)
    public Stock get(long skuId) {
        return stocks.find(skuId).orElseThrow(() -> BizException.notFound("商品 " + skuId + " 没有库存记录"));
    }

    @Transactional(readOnly = true)
    public List<Stock> list(Collection<Long> skuIds) {
        return stocks.findAll(skuIds);
    }

    @Transactional
    public Stock setAvailable(long skuId, int available) {
        if (available < 0) {
            throw BizException.rule("可售数量不能小于 0");
        }
        if (!stocks.setAvailable(skuId, available)) {
            throw BizException.notFound("商品 " + skuId + " 没有库存记录");
        }
        return get(skuId);
    }
}

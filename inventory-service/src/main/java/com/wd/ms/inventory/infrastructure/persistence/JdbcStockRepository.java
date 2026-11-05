package com.wd.ms.inventory.infrastructure.persistence;

import com.wd.ms.common.core.UtcTimes;
import com.wd.ms.inventory.domain.stock.Stock;
import com.wd.ms.inventory.domain.stock.StockRepository;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 库存表的 JDBC 实现。 */
@Repository
public class JdbcStockRepository implements StockRepository {

    private final JdbcClient jdbc;
    private final Clock clock;

    public JdbcStockRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Optional<Stock> find(long skuId) {
        return jdbc.sql("SELECT sku_id, available, reserved, sold FROM stock WHERE sku_id = ?")
                .param(skuId)
                .query(Stock.class)
                .optional();
    }

    @Override
    public List<Stock> findAll(Collection<Long> skuIds) {
        if (skuIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT sku_id, available, reserved, sold FROM stock WHERE sku_id IN (:ids) ORDER BY sku_id")
                .param("ids", skuIds)
                .query(Stock.class)
                .list();
    }

    @Override
    public boolean tryReserve(long skuId, int quantity) {
        // 防超卖的核心：检查和扣减在同一条 UPDATE 里，WHERE available >= ? 不满足就一行都不更新
        return jdbc.sql("""
                UPDATE stock SET available = available - ?, reserved = reserved + ?, updated_at = ?
                WHERE sku_id = ? AND available >= ?
                """)
                        .params(quantity, quantity, UtcTimes.toDb(clock.instant()), skuId, quantity)
                        .update()
                == 1;
    }

    @Override
    public boolean confirm(long skuId, int quantity) {
        return jdbc.sql("""
                UPDATE stock SET reserved = reserved - ?, sold = sold + ?, updated_at = ?
                WHERE sku_id = ? AND reserved >= ?
                """)
                        .params(quantity, quantity, UtcTimes.toDb(clock.instant()), skuId, quantity)
                        .update()
                == 1;
    }

    @Override
    public boolean release(long skuId, int quantity) {
        return jdbc.sql("""
                UPDATE stock SET reserved = reserved - ?, available = available + ?, updated_at = ?
                WHERE sku_id = ? AND reserved >= ?
                """)
                        .params(quantity, quantity, UtcTimes.toDb(clock.instant()), skuId, quantity)
                        .update()
                == 1;
    }

    @Override
    public boolean setAvailable(long skuId, int available) {
        return jdbc.sql("UPDATE stock SET available = ?, updated_at = ? WHERE sku_id = ?")
                        .params(available, UtcTimes.toDb(clock.instant()), skuId)
                        .update()
                == 1;
    }
}

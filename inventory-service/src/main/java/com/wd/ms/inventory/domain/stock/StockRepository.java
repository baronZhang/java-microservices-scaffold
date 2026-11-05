package com.wd.ms.inventory.domain.stock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 库存仓储。扣减类方法全部是"条件更新"：在一条 UPDATE 里同时完成"检查 + 扣减"，
 * 由数据库的行锁保证原子性，不需要先查再改，也就不存在"两个请求都查到还有 1 件"的超卖窗口。
 */
public interface StockRepository {

    Optional<Stock> find(long skuId);

    List<Stock> findAll(Collection<Long> skuIds);

    /** available -= quantity, reserved += quantity；可售不足时不更新，返回 false */
    boolean tryReserve(long skuId, int quantity);

    /** 支付确认：reserved -= quantity，sold += quantity */
    boolean confirm(long skuId, int quantity);

    /** 释放预占：reserved -= quantity，available += quantity */
    boolean release(long skuId, int quantity);

    /** 管理员直接设置可售数量（演示里用来重置秒杀商品的库存） */
    boolean setAvailable(long skuId, int available);
}

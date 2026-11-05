package com.wd.ms.product.domain.product;

import java.util.Collection;
import java.util.Map;

/**
 * 查询库存（出站端口）。应用层只认这个接口；实现是调用库存服务的 HTTP 客户端，带熔断和降级。
 * 约定：任何失败都不抛异常，查不到的商品返回 {@link StockView#unknown()}——展示库存是"锦上添花"，不能拖垮商品页。
 */
public interface StockQueryPort {

    Map<Long, StockView> query(Collection<Long> skuIds);
}

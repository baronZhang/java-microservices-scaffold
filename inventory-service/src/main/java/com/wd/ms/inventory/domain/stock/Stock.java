package com.wd.ms.inventory.domain.stock;

/**
 * 某个商品（SKU）的库存，分三列：
 *
 * <ul>
 *   <li>available：可售。下单时从这里预占
 *   <li>reserved：已预占、未支付。支付后转为 sold，取消/超时后退回 available
 *   <li>sold：已售出
 * </ul>
 *
 * <p>三列之和在整个生命周期里不变（除非管理员调整库存），对账时可以用它校验。
 */
public record Stock(long skuId, int available, int reserved, int sold) {}

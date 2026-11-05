package com.wd.ms.order.application;

/** 下单请求里的一行：买哪个商品、几件。 */
public record OrderLine(long skuId, int quantity) {}

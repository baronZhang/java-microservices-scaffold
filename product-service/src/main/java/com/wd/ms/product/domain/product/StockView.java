package com.wd.ms.product.domain.product;

/**
 * 商品页上展示的库存信息。available 为 null 表示"现在不知道"（库存服务不可用，已降级），
 * 而不是 0——把"不知道"当成"没有货"会误导用户，也可能让运营误判。
 */
public record StockView(Integer available, StockLevel level) {

    public static final int LOW_STOCK_THRESHOLD = 10;

    public static StockView unknown() {
        return new StockView(null, StockLevel.UNKNOWN);
    }

    public static StockView of(int available) {
        StockLevel level = available <= 0
                ? StockLevel.SOLD_OUT
                : available < LOW_STOCK_THRESHOLD ? StockLevel.LOW_STOCK : StockLevel.IN_STOCK;
        return new StockView(available, level);
    }

    /** 库存档位 */
    public enum StockLevel {
        IN_STOCK,
        LOW_STOCK,
        SOLD_OUT,
        UNKNOWN
    }
}

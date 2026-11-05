-- 库存服务的表结构

-- 库存：三列的含义见 Stock 类注释。CHECK 约束是防超卖的最后一道防线：哪怕代码写错，数据库也不允许出现负数
CREATE TABLE stock (
    sku_id     BIGINT      NOT NULL PRIMARY KEY,
    available  INT         NOT NULL,
    reserved   INT         NOT NULL,
    sold       INT         NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT ck_stock_non_negative CHECK (available >= 0 AND reserved >= 0 AND sold >= 0)
);

-- 预占记录：订单号是主键，天然保证"同一订单只预占一次"
CREATE TABLE reservation (
    order_no   VARCHAR(32) NOT NULL PRIMARY KEY,
    status     VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE TABLE reservation_item (
    order_no VARCHAR(32) NOT NULL,
    sku_id   BIGINT      NOT NULL,
    quantity INT         NOT NULL,
    CONSTRAINT pk_reservation_item PRIMARY KEY (order_no, sku_id)
);

-- 已消费消息：消费幂等的判重表
CREATE TABLE consumed_message (
    message_id  VARCHAR(64) NOT NULL PRIMARY KEY,
    topic       VARCHAR(64) NOT NULL,
    consumed_at DATETIME(3) NOT NULL
);

-- 商品服务的表结构。金额用 DECIMAL，不用浮点数（0.1 + 0.2 != 0.3）
CREATE TABLE product (
    id          BIGINT        NOT NULL PRIMARY KEY,
    name        VARCHAR(128)  NOT NULL,
    price       DECIMAL(12, 2) NOT NULL,
    description VARCHAR(512)  NOT NULL,
    on_sale     BOOLEAN       NOT NULL,
    created_at  DATETIME(3)   NOT NULL
);

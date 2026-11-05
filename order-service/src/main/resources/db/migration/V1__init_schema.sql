-- 订单服务的表结构

-- 订单。(user_id, idempotency_key) 唯一约束是幂等的最后一道防线：同一用户同一个幂等键只能有一个订单
CREATE TABLE orders (
    order_no        VARCHAR(32)    NOT NULL PRIMARY KEY,
    user_id         BIGINT         NOT NULL,
    status          VARCHAR(24)    NOT NULL,
    cancel_reason   VARCHAR(32),
    total_amount    DECIMAL(12, 2) NOT NULL,
    idempotency_key VARCHAR(64)    NOT NULL,
    expire_at       DATETIME(3),
    paid_at         DATETIME(3),
    created_at      DATETIME(3)    NOT NULL,
    updated_at      DATETIME(3)    NOT NULL,
    CONSTRAINT uk_orders_user_idempotency UNIQUE (user_id, idempotency_key)
);
-- 超时关单扫描用：WHERE status = 'PENDING_PAYMENT' AND expire_at <= ?
CREATE INDEX idx_orders_status_expire ON orders (status, expire_at);
-- 卡单恢复扫描用：WHERE status = 'RESERVING' AND created_at <= ?
CREATE INDEX idx_orders_status_created ON orders (status, created_at);
CREATE INDEX idx_orders_user_created ON orders (user_id, created_at);

-- 订单明细：商品名称、单价是下单时的快照
CREATE TABLE order_item (
    order_no     VARCHAR(32)    NOT NULL,
    sku_id       BIGINT         NOT NULL,
    product_name VARCHAR(128)   NOT NULL,
    unit_price   DECIMAL(12, 2) NOT NULL,
    quantity     INT            NOT NULL,
    CONSTRAINT pk_order_item PRIMARY KEY (order_no, sku_id)
);

-- 本地消息表（Outbox）：和订单状态变更在同一个本地事务里写入，由消息中继异步投递
CREATE TABLE outbox_event (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id        VARCHAR(64)  NOT NULL,
    aggregate_id    VARCHAR(32)  NOT NULL,
    event_type      VARCHAR(32)  NOT NULL,
    payload         VARCHAR(2000) NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    attempts        INT          NOT NULL,
    next_attempt_at DATETIME(3)  NOT NULL,
    last_error      VARCHAR(500),
    created_at      DATETIME(3)  NOT NULL,
    sent_at         DATETIME(3),
    CONSTRAINT uk_outbox_event_id UNIQUE (event_id)
);
CREATE INDEX idx_outbox_status_next ON outbox_event (status, next_attempt_at);

-- 雪花算法的 workerId 租约
CREATE TABLE worker_node (
    worker_id      INT         NOT NULL PRIMARY KEY,
    instance_id    VARCHAR(128) NOT NULL,
    heartbeat_at   DATETIME(3) NOT NULL,
    last_timestamp BIGINT      NOT NULL
);

-- 分布式锁（Spring Integration JdbcLockRegistry 约定的表结构）
CREATE TABLE INT_LOCK (
    LOCK_KEY      CHAR(36)     NOT NULL,
    REGION        VARCHAR(100) NOT NULL,
    CLIENT_ID     CHAR(36),
    CREATED_DATE  DATETIME(6)  NOT NULL,
    EXPIRED_AFTER DATETIME(6)  NOT NULL,
    CONSTRAINT INT_LOCK_PK PRIMARY KEY (LOCK_KEY, REGION)
);

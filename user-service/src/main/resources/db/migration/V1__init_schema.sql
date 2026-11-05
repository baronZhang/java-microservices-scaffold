-- 用户服务的表结构。表名用 users 而不是 user：USER 在 H2 和很多数据库里是保留字
CREATE TABLE users (
    id         BIGINT       NOT NULL PRIMARY KEY,
    username   VARCHAR(64)  NOT NULL,
    nickname   VARCHAR(64)  NOT NULL,
    email      VARCHAR(128) NOT NULL,
    role       VARCHAR(16)  NOT NULL,
    status     VARCHAR(16)  NOT NULL,
    created_at DATETIME(3)  NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username)
);
CREATE INDEX idx_users_status ON users (status);

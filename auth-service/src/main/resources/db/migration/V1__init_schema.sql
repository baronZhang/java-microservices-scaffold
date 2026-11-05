-- 认证服务的表结构（H2 的 MySQL 兼容模式和 MySQL 8 都能执行）
-- 时间列一律存 UTC，见 common-core 的 UtcTimes

-- 账号：身份 + 权限（演示简化：角色、权限码逗号分隔）
CREATE TABLE account (
    id            BIGINT       NOT NULL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname      VARCHAR(64)  NOT NULL,
    roles         VARCHAR(255) NOT NULL,
    permissions   VARCHAR(1000) NOT NULL,
    created_at    DATETIME(3)  NOT NULL,
    CONSTRAINT uk_account_username UNIQUE (username)
);

-- refresh token：只存 SHA-256 摘要；同一次登录轮换出来的 token 共享 session_id
CREATE TABLE refresh_token (
    token_hash CHAR(64)    NOT NULL PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    user_id    BIGINT      NOT NULL,
    status     VARCHAR(16) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    used_at    DATETIME(3)
);
CREATE INDEX idx_refresh_token_session ON refresh_token (session_id);

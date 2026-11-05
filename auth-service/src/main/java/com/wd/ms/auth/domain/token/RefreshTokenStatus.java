package com.wd.ms.auth.domain.token;

/** refresh token 的状态。轮换时 ACTIVE → USED；检测到复用或退出登录时整个会话的 token → REVOKED。 */
public enum RefreshTokenStatus {
    ACTIVE,
    USED,
    REVOKED
}

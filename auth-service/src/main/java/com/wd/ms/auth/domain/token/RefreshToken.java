package com.wd.ms.auth.domain.token;

import java.time.Instant;

/**
 * refresh token 记录。库里只存 token 的 SHA-256 摘要：数据库泄露了，攻击者也拿不到能用的 token。
 *
 * @param sessionId 一次登录产生一个会话 id，轮换出来的新 token 沿用它，用来"整串作废"
 */
public record RefreshToken(
        String tokenHash, String sessionId, long userId, RefreshTokenStatus status, Instant expiresAt) {

    public boolean usableAt(Instant now) {
        return status == RefreshTokenStatus.ACTIVE && expiresAt.isAfter(now);
    }
}

package com.wd.ms.auth.domain.token;

import java.time.Instant;
import java.util.Optional;

/** refresh token 仓储。 */
public interface RefreshTokenRepository {

    void save(RefreshToken token, Instant createdAt);

    Optional<RefreshToken> findByHash(String tokenHash);

    /**
     * 把 token 标记为已使用：条件更新（只有 ACTIVE 且未过期才会成功）。
     * 两个请求拿同一个 token 并发刷新时，数据库保证只有一个返回 true。
     */
    boolean markUsed(String tokenHash, Instant now);

    /** 作废一个会话下所有还能用的 token，返回作废的条数 */
    int revokeSession(String sessionId);
}

package com.wd.ms.auth.infrastructure.persistence;

import com.wd.ms.auth.domain.token.RefreshToken;
import com.wd.ms.auth.domain.token.RefreshTokenRepository;
import com.wd.ms.auth.domain.token.RefreshTokenStatus;
import com.wd.ms.common.core.UtcTimes;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** refresh token 表的 JDBC 实现。 */
@Repository
public class JdbcRefreshTokenRepository implements RefreshTokenRepository {

    private final JdbcClient jdbc;

    public JdbcRefreshTokenRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(RefreshToken token, Instant createdAt) {
        jdbc.sql("""
                INSERT INTO refresh_token (token_hash, session_id, user_id, status, expires_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)
                .params(
                        token.tokenHash(),
                        token.sessionId(),
                        token.userId(),
                        token.status().name(),
                        UtcTimes.toDb(token.expiresAt()),
                        UtcTimes.toDb(createdAt))
                .update();
    }

    @Override
    public Optional<RefreshToken> findByHash(String tokenHash) {
        return jdbc.sql("SELECT * FROM refresh_token WHERE token_hash = ?")
                .param(tokenHash)
                .query(JdbcRefreshTokenRepository::map)
                .optional();
    }

    @Override
    public boolean markUsed(String tokenHash, Instant now) {
        // 关键在 WHERE 里的 status = 'ACTIVE'：并发的两个请求只有一个能把它从 ACTIVE 改掉
        int updated = jdbc.sql("""
                UPDATE refresh_token SET status = 'USED', used_at = ?
                WHERE token_hash = ? AND status = 'ACTIVE' AND expires_at > ?
                """)
                .params(UtcTimes.toDb(now), tokenHash, UtcTimes.toDb(now))
                .update();
        return updated == 1;
    }

    @Override
    public int revokeSession(String sessionId) {
        return jdbc.sql("UPDATE refresh_token SET status = 'REVOKED' WHERE session_id = ? AND status = 'ACTIVE'")
                .param(sessionId)
                .update();
    }

    private static RefreshToken map(ResultSet rs, int row) throws SQLException {
        return new RefreshToken(
                rs.getString("token_hash"),
                rs.getString("session_id"),
                rs.getLong("user_id"),
                RefreshTokenStatus.valueOf(rs.getString("status")),
                UtcTimes.fromDb(rs.getObject("expires_at", LocalDateTime.class)));
    }
}

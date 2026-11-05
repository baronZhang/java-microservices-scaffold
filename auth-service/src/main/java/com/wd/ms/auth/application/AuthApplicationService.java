package com.wd.ms.auth.application;

import com.wd.ms.auth.domain.account.Account;
import com.wd.ms.auth.domain.account.AccountRepository;
import com.wd.ms.auth.domain.token.AccessTokenIssuer;
import com.wd.ms.auth.domain.token.RefreshToken;
import com.wd.ms.auth.domain.token.RefreshTokenRepository;
import com.wd.ms.auth.domain.token.RefreshTokenStatus;
import com.wd.ms.common.core.BizException;
import com.wd.ms.common.core.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登录、刷新、退出。和前端 Mock（mock/session.ts）的行为保持一致，并补上了生产要考虑的几点：
 *
 * <ul>
 *   <li>refresh token 只存摘要，不存明文
 *   <li>轮换：每个 refresh token 只能用一次，用完立即作废并签发新的
 *   <li>复用检测：一个已经用过的 refresh token 又被拿来刷新，说明可能被盗，作废整个会话
 *   <li>用户名不存在时也做一次密码哈希比较，避免通过响应时间判断用户名是否存在
 * </ul>
 */
@Service
public class AuthApplicationService {

    private static final Logger log = LoggerFactory.getLogger(AuthApplicationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accounts;
    private final RefreshTokenRepository refreshTokens;
    private final AccessTokenIssuer tokenIssuer;
    private final PasswordEncoder passwordEncoder;
    private final RefreshStats stats;
    private final Clock clock;
    private final Duration refreshTokenTtl;
    /** 用户名不存在时拿来做一次"陪跑"比较的哈希值 */
    private final String dummyHash;

    public AuthApplicationService(
            AccountRepository accounts,
            RefreshTokenRepository refreshTokens,
            AccessTokenIssuer tokenIssuer,
            PasswordEncoder passwordEncoder,
            RefreshStats stats,
            Clock clock,
            @Value("${ms.auth.refresh-token-ttl:7d}") Duration refreshTokenTtl) {
        this.accounts = accounts;
        this.refreshTokens = refreshTokens;
        this.tokenIssuer = tokenIssuer;
        this.passwordEncoder = passwordEncoder;
        this.stats = stats;
        this.clock = clock;
        this.refreshTokenTtl = refreshTokenTtl;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public TokenPair login(String username, String password) {
        Account account =
                username == null ? null : accounts.findByUsername(username).orElse(null);
        String hash = account == null ? dummyHash : account.passwordHash();
        boolean matched = passwordEncoder.matches(password == null ? "" : password, hash);
        if (account == null || !matched) {
            // 普通业务失败：HTTP 200 + 业务码 10001，由前端按业务码提示
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }
        return issuePair(account, UUID.randomUUID().toString());
    }

    /**
     * noRollbackFor：检测到复用时要先作废整个会话、再抛 401。默认遇到运行时异常会回滚，
     * 那样"作废会话"这一步就白做了，复用检测形同虚设（AuthFlowIT 里有用例专门守着这一点）。
     */
    @Transactional(noRollbackFor = BizException.class)
    public TokenPair refresh(String refreshToken) {
        stats.increment();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String hash = sha256(refreshToken);
        RefreshToken token = refreshTokens.findByHash(hash).orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
        Instant now = clock.instant();

        if (token.status() == RefreshTokenStatus.USED) {
            int revoked = refreshTokens.revokeSession(token.sessionId());
            log.warn(
                    "检测到 refresh token 被重复使用，作废会话 {}（{} 个 token），用户 id={}", token.sessionId(), revoked, token.userId());
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        // 条件更新失败 = 并发请求里的另一个已经用掉了它，或者它已过期 / 已作废
        if (!token.usableAt(now) || !refreshTokens.markUsed(hash, now)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        Account account = accounts.findById(token.userId()).orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
        return issuePair(account, token.sessionId());
    }

    /** 退出登录：作废本次会话的 refresh token。access token 由网关加入吊销清单（见网关的 RevokeAccessToken 过滤器） */
    @Transactional
    public void logout(String sessionId) {
        if (sessionId != null) {
            refreshTokens.revokeSession(sessionId);
        }
    }

    public Account profile(long userId) {
        return accounts.findById(userId).orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "用户不存在"));
    }

    private TokenPair issuePair(Account account, String sessionId) {
        Instant now = clock.instant();
        String refreshToken = "rt_" + randomToken();
        refreshTokens.save(
                new RefreshToken(
                        sha256(refreshToken),
                        sessionId,
                        account.id(),
                        RefreshTokenStatus.ACTIVE,
                        now.plus(refreshTokenTtl)),
                now);
        AccessTokenIssuer.IssuedToken accessToken = tokenIssuer.issueUserToken(account, sessionId);
        return new TokenPair(accessToken.value(), refreshToken, accessToken.expiresInSeconds());
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

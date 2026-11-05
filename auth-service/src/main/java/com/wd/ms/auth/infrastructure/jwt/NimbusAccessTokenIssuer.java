package com.wd.ms.auth.infrastructure.jwt;

import com.wd.ms.auth.domain.account.Account;
import com.wd.ms.auth.domain.token.AccessTokenIssuer;
import com.wd.ms.auth.infrastructure.config.AuthProperties;
import com.wd.ms.common.security.TokenClaims;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * 用 Nimbus 签发 JWT。
 *
 * <p>access token 里放了用户 id、用户名、角色和权限码：网关和各服务验完签名就能做权限判断，不用每次回查认证服务。
 * 代价是权限变更要等旧 token 过期（最长 5 分钟）才生效，所以有效期不能太长。
 */
public class NimbusAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder encoder;
    private final String keyId;
    private final AuthProperties properties;
    private final Clock clock;

    public NimbusAccessTokenIssuer(JwtEncoder encoder, String keyId, AuthProperties properties, Clock clock) {
        this.encoder = encoder;
        this.keyId = keyId;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public IssuedToken issueUserToken(Account account, String sessionId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = baseClaims(String.valueOf(account.id()), now, properties.accessTokenTtl())
                .claim(TokenClaims.TYPE, TokenClaims.TYPE_USER)
                .claim(TokenClaims.USERNAME, account.username())
                .claim(TokenClaims.NICKNAME, account.nickname())
                .claim(TokenClaims.ROLES, account.roles())
                .claim(TokenClaims.PERMISSIONS, account.permissions())
                .claim(TokenClaims.SESSION_ID, sessionId)
                .build();
        return new IssuedToken(encode(claims), properties.accessTokenTtl().toSeconds());
    }

    @Override
    public IssuedToken issueServiceToken(String clientId, List<String> scopes) {
        Instant now = clock.instant();
        JwtClaimsSet claims = baseClaims(clientId, now, properties.serviceTokenTtl())
                .claim(TokenClaims.TYPE, TokenClaims.TYPE_SERVICE)
                .claim(TokenClaims.SCOPE, String.join(" ", scopes))
                .build();
        return new IssuedToken(encode(claims), properties.serviceTokenTtl().toSeconds());
    }

    private JwtClaimsSet.Builder baseClaims(String subject, Instant now, Duration ttl) {
        return JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(subject)
                // jti：每个 token 唯一，网关的吊销清单按它记录
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl));
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}

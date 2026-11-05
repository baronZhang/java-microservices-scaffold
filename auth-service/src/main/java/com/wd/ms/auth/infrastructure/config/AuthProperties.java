package com.wd.ms.auth.infrastructure.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * 认证服务配置（前缀 {@code ms.auth}）。
 *
 * @param accessTokenTtl access token 有效期，和前端 Mock 一样是 5 分钟
 * @param refreshTokenTtl refresh token 有效期，7 天
 * @param serviceTokenTtl 服务令牌有效期
 * @param jwt 签名密钥（PEM）。不配置时启动时临时生成，重启后旧 token 全部失效，多实例部署必须配置同一对密钥
 * @param clients 允许用 client_credentials 换服务令牌的服务
 */
@ConfigurationProperties("ms.auth")
public record AuthProperties(
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration serviceTokenTtl,
        Jwt jwt,
        List<Client> clients) {

    public AuthProperties {
        issuer = issuer == null ? "ms-auth" : issuer;
        accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(5) : accessTokenTtl;
        refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(7) : refreshTokenTtl;
        serviceTokenTtl = serviceTokenTtl == null ? Duration.ofMinutes(10) : serviceTokenTtl;
        jwt = jwt == null ? new Jwt(null, null, null) : jwt;
        clients = clients == null ? List.of() : List.copyOf(clients);
    }

    /**
     * @param privateKey PKCS#8 私钥（生产来自 K8s Secret 挂载的文件）
     * @param publicKey X.509 公钥
     * @param keyId JWK 的 kid，换密钥时换一个新的 kid，验签方据此判断要不要重新拉公钥
     */
    public record Jwt(Resource privateKey, Resource publicKey, String keyId) {}

    public record Client(String id, String secret, List<String> scopes) {

        public Client {
            scopes = scopes == null ? List.of("internal") : List.copyOf(scopes);
        }
    }
}

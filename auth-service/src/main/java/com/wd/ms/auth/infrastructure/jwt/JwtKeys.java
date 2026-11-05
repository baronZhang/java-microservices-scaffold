package com.wd.ms.auth.infrastructure.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.wd.ms.auth.domain.token.JwkSetSource;
import com.wd.ms.auth.infrastructure.config.AuthProperties;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.converter.RsaKeyConverters;

/**
 * 签名密钥。选 RS256（非对称）而不是 HS256（共享密钥）：
 * 验签方（网关、各服务）只需要公钥，即使某个服务被攻破，也伪造不了 token。
 */
public class JwtKeys implements JwkSetSource {

    private static final Logger log = LoggerFactory.getLogger(JwtKeys.class);
    private final RSAKey rsaKey;

    public JwtKeys(AuthProperties.Jwt config) {
        this.rsaKey = config.privateKey() != null && config.publicKey() != null ? load(config) : generate();
    }

    public RSAKey rsaKey() {
        return rsaKey;
    }

    public RSAPublicKey publicKey() {
        try {
            return rsaKey.toRSAPublicKey();
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Map<String, Object> publicJwkSet() {
        // toPublicJWKSet()：只输出公钥部分，私钥永远不会出现在响应里
        return new JWKSet(rsaKey).toPublicJWKSet().toJSONObject();
    }

    private static RSAKey load(AuthProperties.Jwt config) {
        try (InputStream privateIn = config.privateKey().getInputStream();
                InputStream publicIn = config.publicKey().getInputStream()) {
            RSAPrivateKey privateKey = RsaKeyConverters.pkcs8().convert(privateIn);
            RSAPublicKey publicKey = RsaKeyConverters.x509().convert(publicIn);
            String keyId = config.keyId() == null ? "ms-configured" : config.keyId();
            log.info("JWT 签名密钥：使用配置的密钥，kid={}", keyId);
            return build(publicKey, privateKey, keyId);
        } catch (IOException e) {
            throw new IllegalStateException("读取 JWT 密钥失败", e);
        }
    }

    private static RSAKey generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            String keyId =
                    "ms-" + LocalDate.now() + "-" + UUID.randomUUID().toString().substring(0, 8);
            log.warn("JWT 签名密钥：未配置，已临时生成（kid={}）。重启后旧 token 全部失效；多实例部署必须配置同一对密钥", keyId);
            return build((RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate(), keyId);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey build(RSAPublicKey publicKey, RSAPrivateKey privateKey, String keyId) {
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(keyId)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
    }
}

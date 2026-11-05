package com.wd.ms.common.test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * 测试用的 JWT：进程内生成一对 RSA 密钥，签发和校验都用它，测试不依赖认证服务。
 *
 * <p>用真实签名而不是 Spring Security 的 {@code jwt()} 模拟：这样从 HTTP 头到权限判断的整条链路都被测到了。
 * 声明名称和真实签发方一致（这里写字面量，避免测试支撑模块反过来依赖安全 starter）。
 */
public final class TestJwts {

    public static final String ISSUER = "ms-auth";
    private static final KeyPair KEY_PAIR = generate();
    private static final NimbusJwtEncoder ENCODER = NimbusJwtEncoder.withKeyPair(
                    (RSAPublicKey) KEY_PAIR.getPublic(), (RSAPrivateKey) KEY_PAIR.getPrivate())
            .build();

    private TestJwts() {}

    /** 管理员：拥有全部权限 */
    public static String admin() {
        return user(1, "admin", List.of("admin"), List.of("*"));
    }

    /** 运营：能查看和启停用户，不能删除 */
    public static String editor() {
        return user(2, "editor", List.of("editor"), List.of("user:list", "user:edit"));
    }

    /** 访客：没有任何权限码 */
    public static String guest() {
        return user(3, "guest", List.of("guest"), List.of());
    }

    public static String user(long id, String username, List<String> roles, List<String> permissions) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(id))
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("typ", "user")
                .claim("username", username)
                .claim("nickname", username)
                .claim("roles", roles)
                .claim("perms", permissions)
                .claim("sid", UUID.randomUUID().toString())
                .build();
        return ENCODER.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /** 服务令牌：带 internal scope，可以访问 /internal/** */
    public static String service(String clientId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(clientId)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .claim("typ", "service")
                .claim("scope", "internal")
                .build();
        return ENCODER.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    public static JwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY_PAIR.getPublic())
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return decoder;
    }

    public static RSAPublicKey publicKey() {
        return (RSAPublicKey) KEY_PAIR.getPublic();
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 在测试类上 {@code @Import(TestJwts.Config.class)}，用测试密钥替换掉"去认证服务拉公钥"的解码器 */
    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        public JwtDecoder testJwtDecoder() {
            return decoder();
        }
    }
}

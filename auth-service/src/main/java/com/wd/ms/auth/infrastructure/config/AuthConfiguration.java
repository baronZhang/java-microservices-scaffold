package com.wd.ms.auth.infrastructure.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.wd.ms.auth.domain.client.ServiceClient;
import com.wd.ms.auth.domain.client.ServiceClientRegistry;
import com.wd.ms.auth.domain.token.AccessTokenIssuer;
import com.wd.ms.auth.infrastructure.jwt.JwtKeys;
import com.wd.ms.auth.infrastructure.jwt.NimbusAccessTokenIssuer;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** 认证服务的装配：密钥、签发器、密码编码器、服务客户端登记表。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {

    @Bean
    public JwtKeys jwtKeys(AuthProperties properties) {
        return new JwtKeys(properties.jwt());
    }

    @Bean
    public AccessTokenIssuer accessTokenIssuer(JwtKeys keys, AuthProperties properties, Clock clock) {
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(keys.rsaKey())));
        return new NimbusAccessTokenIssuer(encoder, keys.rsaKey().getKeyID(), properties, clock);
    }

    /**
     * 认证服务自己也是资源服务器（logout、profile 要校验 token），但没必要通过网络找自己要公钥，
     * 直接用内存里的公钥。安全 starter 里的 JwtDecoder 标了 @ConditionalOnMissingBean，会让位给这个。
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtKeys keys, AuthProperties properties) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withPublicKey(keys.publicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    /** 委托式编码器：库里的哈希带 {bcrypt} 前缀，以后换算法（如 argon2）时新旧哈希可以共存、登录时渐进升级 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public ServiceClientRegistry serviceClientRegistry(AuthProperties properties) {
        Map<String, ServiceClient> clients = properties.clients().stream()
                .map(client -> new ServiceClient(client.id(), client.secret(), client.scopes()))
                .collect(Collectors.toUnmodifiableMap(ServiceClient::clientId, Function.identity()));
        return clientId -> Optional.ofNullable(clients.get(clientId));
    }
}

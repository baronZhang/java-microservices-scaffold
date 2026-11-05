package com.wd.ms.gateway.config;

import com.wd.ms.gateway.gray.GrayRouteFilter;
import com.wd.ms.gateway.ratelimit.InMemoryRateLimiter;
import com.wd.ms.gateway.ratelimit.RateLimitFilter;
import com.wd.ms.gateway.security.AuthenticationFilter;
import com.wd.ms.gateway.security.InMemoryTokenDenylist;
import com.wd.ms.gateway.security.RedisTokenDenylist;
import com.wd.ms.gateway.security.RevokeAccessTokenGatewayFilterFactory;
import com.wd.ms.gateway.security.TokenDenylist;
import com.wd.ms.gateway.web.AccessLogFilter;
import com.wd.ms.gateway.web.GatewayErrorHandler;
import com.wd.ms.gateway.web.JsonResponses;
import com.wd.ms.gateway.web.TraceIdFilter;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * 网关的装配。过滤器执行顺序（数字越小越先）：
 *
 * <pre>
 * TraceIdFilter(-200) → AccessLogFilter(-190) → AuthenticationFilter(-100) → RateLimitFilter(-90) → GrayRouteFilter(-80) → 路由转发
 * </pre>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MsGatewayProperties.class)
@LoadBalancerClients(defaultConfiguration = GatewayLoadBalancerConfiguration.class)
public class GatewayConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public JsonResponses jsonResponses(JsonMapper jsonMapper) {
        return new JsonResponses(jsonMapper);
    }

    /**
     * 验签用的公钥从认证服务的 JWKS 端点拉取（按服务名，经负载均衡），带缓存；遇到新的 kid 会自动重新拉取。
     * 用 Spring Cloud 提供的负载均衡过滤函数自己组一个 WebClient，而不是声明全局的 @LoadBalanced WebClient.Builder，
     * 避免影响别处用到的 WebClient。
     */
    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(
            MsGatewayProperties properties, ReactorLoadBalancerExchangeFilterFunction loadBalancerFunction) {
        WebClient webClient = WebClient.builder().filter(loadBalancerFunction).build();
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(properties.getJwkSetUri())
                .webClient(webClient)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.getIssuer()));
        return decoder;
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "ms.gateway.rate-limit",
            name = "backend",
            havingValue = "memory",
            matchIfMissing = true)
    public TokenDenylist inMemoryTokenDenylist(Clock clock) {
        return new InMemoryTokenDenylist(clock);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ms.gateway.rate-limit", name = "backend", havingValue = "redis")
    public TokenDenylist redisTokenDenylist(ReactiveStringRedisTemplate redis, Clock clock) {
        return new RedisTokenDenylist(redis, clock);
    }

    /** 默认 profile：内存令牌桶 */
    @Bean
    @Primary
    @ConditionalOnProperty(
            prefix = "ms.gateway.rate-limit",
            name = "backend",
            havingValue = "memory",
            matchIfMissing = true)
    public RateLimiter<?> msRateLimiter(MsGatewayProperties properties, Clock clock) {
        return new InMemoryRateLimiter(properties, clock);
    }

    /** redis profile：Gateway 自带的 Redis 令牌桶（Lua 脚本保证"补充 + 扣减"原子执行），多个网关实例共享 */
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "ms.gateway.rate-limit", name = "backend", havingValue = "redis")
    public RedisRateLimiter msRedisRateLimiter(MsGatewayProperties properties) {
        var limits = properties.getRateLimit();
        var limiter = new RedisRateLimiter(
                limits.getDefaults().getReplenishRate(), limits.getDefaults().getBurstCapacity());
        limiter.getConfig()
                .put(
                        InMemoryRateLimiter.POLICY_LOGIN,
                        new RedisRateLimiter.Config()
                                .setReplenishRate(limits.getLogin().getReplenishRate())
                                .setBurstCapacity(limits.getLogin().getBurstCapacity())
                                .setRequestedTokens(1));
        return limiter;
    }

    @Bean
    public TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }

    @Bean
    public AccessLogFilter accessLogFilter() {
        return new AccessLogFilter();
    }

    @Bean
    public AuthenticationFilter authenticationFilter(
            ReactiveJwtDecoder decoder,
            TokenDenylist denylist,
            MsGatewayProperties properties,
            JsonResponses responses) {
        return new AuthenticationFilter(decoder, denylist, properties, responses);
    }

    @Bean
    public RateLimitFilter rateLimitFilter(
            ObjectProvider<RateLimiter<?>> rateLimiter, MsGatewayProperties properties, JsonResponses responses) {
        return new RateLimitFilter(rateLimiter.getObject(), properties, responses);
    }

    @Bean
    public GrayRouteFilter grayRouteFilter(MsGatewayProperties properties) {
        return new GrayRouteFilter(properties);
    }

    @Bean
    public RevokeAccessTokenGatewayFilterFactory revokeAccessTokenGatewayFilterFactory(TokenDenylist denylist) {
        return new RevokeAccessTokenGatewayFilterFactory(denylist);
    }

    @Bean
    public GatewayErrorHandler gatewayErrorHandler(JsonResponses responses) {
        return new GatewayErrorHandler(responses);
    }
}

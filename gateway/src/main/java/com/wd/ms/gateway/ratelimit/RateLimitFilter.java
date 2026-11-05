package com.wd.ms.gateway.ratelimit;

import com.wd.ms.common.core.ErrorCode;
import com.wd.ms.gateway.config.MsGatewayProperties;
import com.wd.ms.gateway.security.AuthenticatedUser;
import com.wd.ms.gateway.web.JsonResponses;
import java.net.InetSocketAddress;
import java.util.Optional;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 网关限流：登录后按用户、未登录按 IP；登录接口单独一档更严格的策略（防暴力破解）。
 *
 * <p>没有直接用 Gateway 自带的 RequestRateLimiter 过滤器：它拒绝时只返回空的 429，不符合统一响应契约。
 * 这里复用它的 {@link RateLimiter} 接口（默认内存令牌桶，redis profile 换成 Gateway 的 RedisRateLimiter），
 * 只把"拒绝时的响应"换成统一的 JSON。
 */
public class RateLimitFilter implements WebFilter, Ordered {

    /** 认证（-100）之后：要按用户限流，得先知道是谁 */
    public static final int ORDER = -90;

    private final RateLimiter<?> rateLimiter;
    private final MsGatewayProperties properties;
    private final JsonResponses responses;

    public RateLimitFilter(RateLimiter<?> rateLimiter, MsGatewayProperties properties, JsonResponses responses) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.responses = responses;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!properties.getRateLimit().isEnabled() || !path.startsWith("/api/")) {
            return chain.filter(exchange);
        }
        boolean login = path.equals("/api/auth/login");
        String policy = login ? InMemoryRateLimiter.POLICY_LOGIN : "default";
        String key = login
                ? "ip:" + clientIp(exchange)
                : AuthenticatedUser.from(exchange)
                        .map(user -> "user:" + user.userId())
                        .orElseGet(() -> "ip:" + clientIp(exchange));
        return rateLimiter.isAllowed(policy, key).flatMap(response -> {
            response.getHeaders()
                    .forEach(
                            (name, value) -> exchange.getResponse().getHeaders().set(name, value));
            if (response.isAllowed()) {
                return chain.filter(exchange);
            }
            return responses.write(exchange, ErrorCode.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_REQUESTS.defaultMessage());
        });
    }

    /** 演示环境直接用连接的对端地址；生产在负载均衡器后面，要从可信代理设置的 X-Forwarded-For 里取 */
    private static String clientIp(ServerWebExchange exchange) {
        return Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(address -> address.getHostAddress())
                .orElse("unknown");
    }
}

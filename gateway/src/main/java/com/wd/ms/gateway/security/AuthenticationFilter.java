package com.wd.ms.gateway.security;

import com.wd.ms.common.core.ErrorCode;
import com.wd.ms.gateway.config.MsGatewayProperties;
import com.wd.ms.gateway.web.JsonResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.PathContainer;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

/**
 * 网关统一认证：所有 /api/** 请求先在这里验 token，验不过直接返回 401，根本到不了后端服务。
 *
 * <ol>
 *   <li>公开接口（登录、刷新……）放行
 *   <li>取 Authorization: Bearer xxx，验签名、过期时间、签发方（公钥从认证服务的 JWKS 拉取并缓存）
 *   <li>只接受用户令牌：服务令牌是给内网服务间调用的，不能从外网用
 *   <li>查吊销清单：退出登录、演示里"让 token 立即过期"的 token 在这里被拦下
 * </ol>
 *
 * <p>验过的 token 原样转发给后端（不改写成明文的 X-User-Id 请求头），后端服务会再验一次签名，原因见文档"认证鉴权"。
 *
 * <p>用 WebFilter 而不是 Gateway 的 GlobalFilter：GlobalFilter 只作用于匹配上路由的请求，
 * 网关自己处理的接口（如 /api/debug/expire-token）不经过它。
 */
public class AuthenticationFilter implements WebFilter, Ordered {

    /** 在 Spring 的观测过滤器之后（这样 401 响应也有 traceId），在限流、灰度之前 */
    public static final int ORDER = -100;

    private static final Logger log = LoggerFactory.getLogger(AuthenticationFilter.class);
    private static final PathPatternParser PARSER = PathPatternParser.defaultInstance;

    private final ReactiveJwtDecoder jwtDecoder;
    private final TokenDenylist denylist;
    private final MsGatewayProperties properties;
    private final JsonResponses responses;

    public AuthenticationFilter(
            ReactiveJwtDecoder jwtDecoder,
            TokenDenylist denylist,
            MsGatewayProperties properties,
            JsonResponses responses) {
        this.jwtDecoder = jwtDecoder;
        this.denylist = denylist;
        this.properties = properties;
        this.responses = responses;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        PathContainer path = exchange.getRequest().getPath().pathWithinApplication();
        if (!path.value().startsWith("/api/") || isPublic(path)) {
            return chain.filter(exchange);
        }
        String token = bearerToken(exchange);
        if (token == null) {
            return unauthorized(exchange);
        }
        return jwtDecoder
                .decode(token)
                .filter(jwt -> "user".equals(jwt.getClaimAsString("typ")))
                .filterWhen(jwt -> denylist.isRevoked(jwt.getId()).map(revoked -> !revoked))
                .flatMap(jwt -> {
                    exchange.getAttributes().put(AuthenticatedUser.ATTRIBUTE, toUser(jwt));
                    return chain.filter(exchange).thenReturn(Boolean.TRUE);
                })
                // 走到这里说明 token 类型不对或已被吊销
                .switchIfEmpty(Mono.defer(() -> unauthorized(exchange).thenReturn(Boolean.FALSE)))
                .onErrorResume(JwtException.class, e -> {
                    log.debug("token 校验失败：{}", e.getMessage());
                    return unauthorized(exchange).thenReturn(Boolean.FALSE);
                })
                .then();
    }

    private boolean isPublic(PathContainer path) {
        return properties.getPublicPaths().stream()
                .anyMatch(pattern -> PARSER.parse(pattern).matches(path));
    }

    private static String bearerToken(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }

    private static AuthenticatedUser toUser(Jwt jwt) {
        return new AuthenticatedUser(
                Long.parseLong(jwt.getSubject()), jwt.getClaimAsString("username"), jwt.getId(), jwt.getExpiresAt());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        return responses.write(exchange, ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage());
    }
}

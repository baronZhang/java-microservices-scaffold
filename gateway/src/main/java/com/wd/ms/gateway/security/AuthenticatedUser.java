package com.wd.ms.gateway.security;

import java.time.Instant;
import java.util.Optional;
import org.springframework.web.server.ServerWebExchange;

/**
 * 网关验过 token 之后放进请求属性里的当前用户，后面的限流、灰度、吊销过滤器都从这里取。
 *
 * @param jti token 的唯一 id，吊销清单按它记录
 * @param expiresAt token 的过期时间，吊销记录只需要保存到这个时间
 */
public record AuthenticatedUser(long userId, String username, String jti, Instant expiresAt) {

    public static final String ATTRIBUTE = AuthenticatedUser.class.getName();

    public static Optional<AuthenticatedUser> from(ServerWebExchange exchange) {
        return Optional.ofNullable(exchange.getAttribute(ATTRIBUTE));
    }
}

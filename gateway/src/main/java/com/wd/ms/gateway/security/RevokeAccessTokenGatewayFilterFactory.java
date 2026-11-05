package com.wd.ms.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import reactor.core.publisher.Mono;

/**
 * 路由过滤器 {@code RevokeAccessToken}：挂在"退出登录"路由上。后端返回 2xx 时，把当前 access token 加入吊销清单。
 *
 * <p>在响应提交之前（beforeCommit）完成吊销：前端拿到"退出成功"时，旧 token 已经不能用了，不存在时间窗口。
 */
public class RevokeAccessTokenGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    private final TokenDenylist denylist;

    public RevokeAccessTokenGatewayFilterFactory(TokenDenylist denylist) {
        super(Object.class);
        this.denylist = denylist;
    }

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> {
            exchange.getResponse().beforeCommit(() -> {
                var status = exchange.getResponse().getStatusCode();
                if (status == null || !status.is2xxSuccessful()) {
                    return Mono.empty();
                }
                return AuthenticatedUser.from(exchange)
                        .map(user -> denylist.revoke(user.jti(), user.expiresAt()))
                        .orElse(Mono.empty());
            });
            return chain.filter(exchange);
        };
    }
}

package com.wd.ms.gateway.debug;

import com.wd.ms.common.core.Result;
import com.wd.ms.gateway.security.AuthenticatedUser;
import com.wd.ms.gateway.security.TokenDenylist;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 演示接口 POST /api/debug/expire-token：让当前 access token 立即失效（demo profile 才有）。
 * 前端"Token 无感刷新"演示页先调它，再同时发 3 个请求，观察"并发 401 只刷新一次"。
 *
 * <p>为什么在网关实现：token 是否有效由网关的吊销清单决定，"让它过期"就是往清单里加一条。
 */
@Configuration(proxyBeanMethods = false)
@Profile("demo")
public class DebugRoutes {

    @Bean
    public RouterFunction<ServerResponse> debugRouterFunction(TokenDenylist denylist) {
        return RouterFunctions.route()
                .POST("/api/debug/expire-token", request -> {
                    var user = AuthenticatedUser.from(request.exchange());
                    Mono<Void> revoke = user.map(value -> denylist.revoke(value.jti(), value.expiresAt()))
                            .orElse(Mono.empty());
                    return revoke.then(ServerResponse.ok().bodyValue(Result.ok()));
                })
                .build();
    }
}

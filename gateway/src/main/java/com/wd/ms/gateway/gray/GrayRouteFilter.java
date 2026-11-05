package com.wd.ms.gateway.gray;

import com.wd.ms.gateway.config.MsGatewayProperties;
import com.wd.ms.gateway.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 灰度路由：决定这个请求要不要去灰度版本，结论写进 X-Gray 请求头，交给负载均衡按实例元数据 hint 挑实例。
 *
 * <p>判断顺序：
 *
 * <ol>
 *   <li>允许覆盖时，客户端自带的 X-Gray 直接生效（测试同学指定版本）
 *   <li>灰度白名单里的用户
 *   <li>按用户 id 取模落在灰度比例内的用户（同一个用户每次结果一样，体验稳定）
 * </ol>
 *
 * <p>其余请求不带 X-Gray，负载均衡按默认 hint（stable）只选稳定版实例。灰度名单和比例都能通过配置中心动态调整。
 */
public class GrayRouteFilter implements WebFilter, Ordered {

    public static final String HEADER = "X-Gray";
    public static final String GRAY = "gray";
    public static final int ORDER = -80;
    private static final Logger log = LoggerFactory.getLogger(GrayRouteFilter.class);

    private final MsGatewayProperties properties;

    public GrayRouteFilter(MsGatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        MsGatewayProperties.Gray gray = properties.getGray();
        String requested = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (!gray.isEnabled()) {
            return chain.filter(requested == null ? exchange : withHint(exchange, null));
        }
        if (requested != null && gray.isAllowHeaderOverride()) {
            return chain.filter(exchange);
        }
        boolean toGray = AuthenticatedUser.from(exchange)
                .map(user -> gray.getUserIds().contains(user.userId())
                        || Math.floorMod(Long.hashCode(user.userId()), 100) < gray.getPercentage())
                .orElse(false);
        if (toGray) {
            log.debug("请求 {} 进入灰度", exchange.getRequest().getPath());
        }
        return chain.filter(withHint(exchange, toGray ? GRAY : null));
    }

    /** 统一改写 X-Gray：要么设成 gray，要么删掉（不让客户端伪造的值透传下去） */
    private static ServerWebExchange withHint(ServerWebExchange exchange, String hint) {
        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> {
                    headers.remove(HEADER);
                    if (hint != null) {
                        headers.set(HEADER, hint);
                    }
                })
                .build();
        return exchange.mutate().request(request).build();
    }
}

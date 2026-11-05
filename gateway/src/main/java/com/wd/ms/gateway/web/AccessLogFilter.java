package com.wd.ms.gateway.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 网关的访问日志：方法、路径、状态码、耗时、traceId、下游实例。
 *
 * <p>响应式环境里日志的 MDC（ThreadLocal）不会自动带上 traceId，所以这里把 traceId 直接写进日志内容。
 */
public class AccessLogFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger("ms.access");

    @Override
    public int getOrder() {
        return -190;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/api/")) {
            return chain.filter(exchange);
        }
        long start = System.nanoTime();
        return chain.filter(exchange).doFinally(signal -> {
            var response = exchange.getResponse();
            var status = response.getStatusCode();
            log.info(
                    "{} {} {} {}ms traceId={} servedBy={}",
                    exchange.getRequest().getMethod(),
                    path,
                    status == null ? "-" : status.value(),
                    (System.nanoTime() - start) / 1_000_000,
                    response.getHeaders().getFirst(TraceIdFilter.HEADER),
                    response.getHeaders().getFirst("X-Served-By"));
        });
    }
}

package com.wd.ms.gateway.web;

import io.micrometer.tracing.handler.TracingObservationHandler;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 在响应头里返回 X-Trace-Id。整条链路（网关 → 订单 → 库存……）共用这一个 traceId，
 * 前端报错时带上它，后端在所有服务的日志里一搜就能串起来。
 *
 * <p>响应式环境里不能靠 ThreadLocal 拿"当前 span"（请求会在不同线程间切换），
 * 所以从请求属性里取 Spring 观测上下文，再从中取出 span。
 */
public class TraceIdFilter implements WebFilter, Ordered {

    public static final String HEADER = "X-Trace-Id";

    @Override
    public int getOrder() {
        // 最先执行：后面的过滤器直接返回 401 / 429 时，响应头里也已经有 traceId 了
        return -200;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerRequestObservationContext.findCurrent(exchange.getAttributes())
                .<TracingObservationHandler.TracingContext>map(
                        context -> context.get(TracingObservationHandler.TracingContext.class))
                .map(TracingObservationHandler.TracingContext::getSpan)
                .ifPresent(span -> exchange.getResponse()
                        .getHeaders()
                        .set(HEADER, span.context().traceId()));
        return chain.filter(exchange);
    }
}

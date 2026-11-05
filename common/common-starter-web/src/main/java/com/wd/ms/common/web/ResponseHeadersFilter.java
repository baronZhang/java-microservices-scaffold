package com.wd.ms.common.web;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 给每个响应加两个排障用的响应头：
 *
 * <ul>
 *   <li>{@code X-Trace-Id}：这次请求的链路 id。前端报错时把它带上，后端在所有服务的日志里一搜就能串起整条链路
 *   <li>{@code X-Served-By}：哪个服务的哪个实例处理的，负载均衡、灰度路由的实验就靠它观察
 * </ul>
 *
 * <p>必须排在 Spring 的链路追踪过滤器之后（那时 span 已经创建），注册顺序见 {@link MsWebAutoConfiguration}。
 */
public class ResponseHeadersFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String SERVED_BY_HEADER = "X-Served-By";

    private final String applicationName;
    private final String version;
    private final ObjectProvider<Tracer> tracer;

    public ResponseHeadersFilter(String applicationName, String version, ObjectProvider<Tracer> tracer) {
        this.applicationName = applicationName;
        this.version = version;
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(SERVED_BY_HEADER, applicationName + ":" + request.getLocalPort() + " " + version);
        Tracer current = tracer.getIfAvailable();
        Span span = current == null ? null : current.currentSpan();
        if (span != null) {
            response.setHeader(TRACE_ID_HEADER, span.context().traceId());
        }
        chain.doFilter(request, response);
    }
}

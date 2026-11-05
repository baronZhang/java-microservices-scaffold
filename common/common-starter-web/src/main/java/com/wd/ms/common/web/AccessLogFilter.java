package com.wd.ms.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 访问日志：每个 /api/**、/internal/** 请求记一行"方法 路径 状态码 耗时"。
 * 日志格式里自带 [服务名,traceId,spanId]（见 config/ms-common.yml 的 logging.pattern.correlation），
 * 所以一次跨服务调用会在每个服务里各留一行，traceId 相同——这就是"按 traceId 串起整条链路"的原始材料。
 */
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("ms.access");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !(uri.startsWith("/api/") || uri.startsWith("/internal/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long millis = (System.nanoTime() - start) / 1_000_000;
            log.info("{} {} {} {}ms", request.getMethod(), request.getRequestURI(), response.getStatus(), millis);
        }
    }
}

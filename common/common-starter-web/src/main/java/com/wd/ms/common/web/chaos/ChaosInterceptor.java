package com.wd.ms.common.web.chaos;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 按 {@link ChaosState} 的设置给请求注入延迟或错误。
 *
 * <p>延迟注入在业务逻辑执行之前：调用方超时放弃之后，这边仍然会继续把请求处理完——
 * 这正好用来演示"超时不等于失败"，以及为什么重试的接口必须幂等。
 */
public class ChaosInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ChaosInterceptor.class);
    private final ChaosState state;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public ChaosInterceptor(ChaosState state) {
        this.state = state;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws InterruptedException {
        if (!state.active() || !matcher.match(state.pathPattern(), request.getRequestURI())) {
            return true;
        }
        if (state.delayMs() > 0) {
            log.warn("故障注入：{} {} 延迟 {} ms", request.getMethod(), request.getRequestURI(), state.delayMs());
            // 业务线程是虚拟线程，sleep 不会占用平台线程
            Thread.sleep(state.delayMs());
        }
        if (ThreadLocalRandom.current().nextDouble() < state.failureRate()) {
            throw new ChaosException("故障注入：模拟服务内部错误");
        }
        return true;
    }
}

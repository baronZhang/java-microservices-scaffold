package com.wd.ms.gateway.web;

import com.wd.ms.common.core.ErrorCode;
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关层面的异常（找不到路由、下游没有可用实例、连接被拒、超时）也翻译成统一的 JSON。
 * 默认的错误响应是 Spring Boot 的 {timestamp, path, status, error}，前端的请求层不认识。
 */
@Order(-2)
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);
    private final JsonResponses responses;

    public GatewayErrorHandler(JsonResponses responses) {
        this.responses = responses;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ErrorCode code = classify(ex);
        if (code == ErrorCode.INTERNAL_ERROR) {
            log.error("网关处理 {} 出错", exchange.getRequest().getPath(), ex);
        } else {
            log.warn("网关返回 {}：{} {}", code, exchange.getRequest().getPath(), ex.toString());
        }
        String message =
                code == ErrorCode.NOT_FOUND ? "接口不存在：" + exchange.getRequest().getPath() : code.defaultMessage();
        return responses.write(exchange, code, message);
    }

    private static ErrorCode classify(Throwable ex) {
        // 负载均衡找不到可用实例（服务全挂了或都没通过健康检查）：Gateway 抛 NotFoundException(503)
        if (ex instanceof NotFoundException) {
            return ErrorCode.SERVICE_UNAVAILABLE;
        }
        if (ex instanceof ResponseStatusException rse) {
            int status = rse.getStatusCode().value();
            return switch (status) {
                case 404 -> ErrorCode.NOT_FOUND;
                case 503 -> ErrorCode.SERVICE_UNAVAILABLE;
                case 504 -> ErrorCode.GATEWAY_TIMEOUT;
                default -> status >= 500 ? ErrorCode.INTERNAL_ERROR : ErrorCode.BAD_REQUEST;
            };
        }
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof ConnectException) {
                return ErrorCode.SERVICE_UNAVAILABLE;
            }
            if (cause instanceof TimeoutException) {
                return ErrorCode.GATEWAY_TIMEOUT;
            }
            cause = cause.getCause();
        }
        return ErrorCode.INTERNAL_ERROR;
    }
}

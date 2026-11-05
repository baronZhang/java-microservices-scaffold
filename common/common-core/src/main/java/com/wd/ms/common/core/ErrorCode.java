package com.wd.ms.common.core;

/**
 * 业务码约定，和前端 src/types/api.ts 的 ErrorCode 保持一致，并按业务域扩展。
 *
 * <p>HTTP 状态码和业务码的分工沿用前端 Mock 的约定：
 *
 * <ul>
 *   <li>认证、权限、资源不存在、参数错误、限流、系统故障：HTTP 状态码和业务码同时表达（401/403/404/400/429/5xx）
 *   <li>普通业务失败（密码错误、库存不足……）：HTTP 200 + 非 0 业务码，由前端按业务码提示
 * </ul>
 *
 * <p>编号规则：0 成功；3 位数与 HTTP 状态码同义；1xxxx 通用业务；2xxxx 交易域（商品、库存、订单）。
 */
public enum ErrorCode {
    SUCCESS(0, 200, "ok"),

    BAD_REQUEST(400, 400, "请求参数不正确"),
    UNAUTHORIZED(401, 401, "登录已过期，请重新登录"),
    FORBIDDEN(403, 403, "没有权限执行该操作"),
    NOT_FOUND(404, 404, "资源不存在"),
    TOO_MANY_REQUESTS(429, 429, "请求太频繁，请稍后再试"),
    INTERNAL_ERROR(500, 500, "服务器开小差了，请稍后再试"),
    SERVICE_UNAVAILABLE(503, 503, "服务暂时不可用，请稍后再试"),
    GATEWAY_TIMEOUT(504, 504, "服务响应超时，请稍后再试"),

    LOGIN_FAILED(10001, 200, "用户名或密码错误"),
    BUSINESS_RULE(10002, 200, "业务规则不允许该操作"),
    IDEMPOTENT_PROCESSING(10003, 200, "请求正在处理中，请勿重复提交"),

    STOCK_INSUFFICIENT(20001, 200, "库存不足"),
    ORDER_STATUS_CONFLICT(20002, 200, "订单当前状态不允许该操作");

    private final int code;
    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, int httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int code() {
        return code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}

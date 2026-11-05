package com.wd.ms.common.core;

/**
 * 业务异常：在任何一层抛出，由全局异常处理器统一转换成 {@link Result} 和对应的 HTTP 状态码。
 *
 * <p>只用来表达"可预期的失败"（库存不足、没有权限……）。程序 bug 不要包装成它，让它以 500 暴露出来。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public static BizException notFound(String message) {
        return new BizException(ErrorCode.NOT_FOUND, message);
    }

    public static BizException rule(String message) {
        return new BizException(ErrorCode.BUSINESS_RULE, message);
    }
}

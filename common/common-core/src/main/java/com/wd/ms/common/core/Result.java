package com.wd.ms.common.core;

/**
 * 统一响应结构，对应前端 src/types/api.ts 的 {@code ApiResult<T>}：{@code {code, message, data}}。
 *
 * <p>用 record 而不是带 setter 的类：响应一旦构造就不可变，也天然带好 equals/hashCode，方便测试断言。
 */
public record Result<T>(int code, String message, T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS.code(), ErrorCode.SUCCESS.defaultMessage(), data);
    }

    public static Result<Void> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return fail(errorCode, errorCode.defaultMessage());
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.code(), message, null);
    }

    /** 不叫 isSuccess：避免被 JSON 序列化成多余的 success 字段，破坏契约 */
    public boolean succeeded() {
        return code == ErrorCode.SUCCESS.code();
    }
}

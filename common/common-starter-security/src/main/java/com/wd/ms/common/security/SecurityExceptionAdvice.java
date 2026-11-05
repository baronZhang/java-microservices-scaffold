package com.wd.ms.common.security;

import com.wd.ms.common.core.ErrorCode;
import com.wd.ms.common.core.Result;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 方法级权限（{@code @PreAuthorize}）拒绝时抛出的异常发生在 Controller 里，不经过过滤器链的 403 处理器，
 * 会先被 {@code @RestControllerAdvice} 捕获。所以这里要以最高优先级把它们翻译成 401 / 403，
 * 否则会落到全局异常处理的兜底分支变成 500。
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionAdvice {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.httpStatus()).body(Result.fail(ErrorCode.FORBIDDEN));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Void>> handleAuthentication(AuthenticationException e) {
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.httpStatus()).body(Result.fail(ErrorCode.UNAUTHORIZED));
    }
}

package com.wd.ms.common.web;

import com.wd.ms.common.core.BizException;
import com.wd.ms.common.core.ErrorCode;
import com.wd.ms.common.core.Result;
import com.wd.ms.common.web.chaos.ChaosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.resilience.InvocationRejectedException;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：把各种异常统一翻译成 {@code {code, message, data}} 和约定的 HTTP 状态码。
 *
 * <p>优先级最低（{@link Ordered#LOWEST_PRECEDENCE}）：安全 starter 里处理 401/403 的 advice 排在它前面，
 * 否则 {@code @PreAuthorize} 抛出的拒绝访问异常会被这里的兜底分支当成 500。
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        ErrorCode code = e.errorCode();
        if (code.httpStatus() >= 500) {
            log.error("业务异常（系统级）：{} {}", code, e.getMessage(), e);
        } else {
            // 可预期的失败只记 info：不需要堆栈，否则日志里全是"假报警"
            log.info("业务失败：{} {}", code, e.getMessage());
        }
        return respond(code, e.getMessage());
    }

    /** 参数校验失败：取第一条错误信息返回给前端 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBind(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse(ErrorCode.BAD_REQUEST.defaultMessage());
        return respond(ErrorCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> handleMethodValidation(HandlerMethodValidationException e) {
        String message = e.getAllErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse(ErrorCode.BAD_REQUEST.defaultMessage());
        return respond(ErrorCode.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return respond(ErrorCode.BAD_REQUEST, "请求体不是合法的 JSON");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Result<Void>> handleMissingHeader(MissingRequestHeaderException e) {
        return respond(ErrorCode.BAD_REQUEST, "缺少请求头 " + e.getHeaderName());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Result<Void>> handleBadParameter(Exception e) {
        return respond(ErrorCode.BAD_REQUEST, "请求参数不正确：" + e.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethod(HttpRequestMethodNotSupportedException e) {
        return respond(ErrorCode.BAD_REQUEST, "不支持的请求方法：" + e.getMethod());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException e) {
        return respond(ErrorCode.NOT_FOUND, "接口不存在：" + e.getResourcePath());
    }

    /** {@code @ConcurrencyLimit(policy = REJECT)} 拒绝的请求：并发已满，告诉调用方稍后再试 */
    @ExceptionHandler(InvocationRejectedException.class)
    public ResponseEntity<Result<Void>> handleRejected(InvocationRejectedException e) {
        log.warn("并发限制拒绝了请求：{}", e.getMessage());
        return respond(ErrorCode.TOO_MANY_REQUESTS, "系统繁忙，请稍后再试");
    }

    /** 故障注入产生的异常：模拟"服务内部出错"，返回 500 */
    @ExceptionHandler(ChaosException.class)
    public ResponseEntity<Result<Void>> handleChaos(ChaosException e) {
        log.warn("故障注入：{}", e.getMessage());
        return respond(ErrorCode.INTERNAL_ERROR, e.getMessage());
    }

    /**
     * 兜底。两种情况：
     *
     * <ul>
     *   <li>Spring 自己的 4xx/5xx 异常（实现了 {@link ErrorResponse}，如 ResponseStatusException）：沿用它的状态码
     *   <li>其他都是程序 bug：记完整堆栈（日志里带 traceId），但不把异常细节返回给前端
     * </ul>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception e) {
        if (e instanceof ErrorResponse errorResponse) {
            int status = errorResponse.getStatusCode().value();
            ErrorCode code = switch (status) {
                case 401 -> ErrorCode.UNAUTHORIZED;
                case 403 -> ErrorCode.FORBIDDEN;
                case 404 -> ErrorCode.NOT_FOUND;
                case 429 -> ErrorCode.TOO_MANY_REQUESTS;
                case 503 -> ErrorCode.SERVICE_UNAVAILABLE;
                default -> status >= 500 ? ErrorCode.INTERNAL_ERROR : ErrorCode.BAD_REQUEST;
            };
            return ResponseEntity.status(status).body(Result.fail(code, code.defaultMessage()));
        }
        log.error("未处理的异常", e);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
    }

    private static ResponseEntity<Result<Void>> respond(ErrorCode code, String message) {
        return ResponseEntity.status(code.httpStatus()).body(Result.fail(code, message));
    }
}

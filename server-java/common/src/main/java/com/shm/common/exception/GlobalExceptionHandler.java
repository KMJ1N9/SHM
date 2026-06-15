package com.shm.common.exception;

import com.shm.common.util.ResponseBuilder;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 全局异常处理 — 将所有异常统一转换为 { code, message, data } 格式
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException e) {
        log.warn("业务异常 code={} message={}", e.getCode(), e.getMessage());
        return ResponseEntity
                .status(mapHttpStatus(e.getCode()))
                .body(ResponseBuilder.error(e.getCode(), e.getMessage()));
    }

    /** 参数校验失败（@Valid @RequestBody 触发，Spring MVC 包装） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        log.warn("参数校验失败: {}", msg);
        return ResponseEntity
                .badRequest()
                .body(ResponseBuilder.error(ErrorCode.VALIDATION_ERROR.getCode(), msg));
    }

    /** 参数校验失败（@Validated 类级别 / Service 层触发） */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        log.warn("参数校验失败: {}", msg);
        return ResponseEntity
                .badRequest()
                .body(ResponseBuilder.error(ErrorCode.VALIDATION_ERROR.getCode(), msg));
    }

    /** 兜底异常 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnknown(Exception e) {
        log.error("未预期异常", e);
        String detail = e.getClass().getSimpleName() + ": " + e.getMessage();
        if (e.getCause() != null) {
            detail += " (cause: " + e.getCause().getClass().getSimpleName()
                    + ": " + e.getCause().getMessage() + ")";
        }
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResponseBuilder.error(ErrorCode.INTERNAL_ERROR.getCode(),
                        "服务内部错误", detail));
    }

    /** 将业务错误码映射到 HTTP 状态码 */
    private HttpStatus mapHttpStatus(int code) {
        if (code >= 1000 && code < 2000) return HttpStatus.UNAUTHORIZED;
        if (code >= 2000 && code < 3000) return HttpStatus.NOT_FOUND;
        if (code >= 3000 && code < 4000) return HttpStatus.CONFLICT;
        if (code >= 4000 && code < 5000) return HttpStatus.BAD_REQUEST;
        if (code >= 5000 && code < 6000) return HttpStatus.FORBIDDEN;
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}

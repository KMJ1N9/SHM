package com.shm.admin.config;

import com.shm.common.exception.ErrorCode;
import com.shm.common.util.ResponseBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Admin Service 专属异常处理 — 处理 Spring Security 的 AccessDeniedException
 *
 * <p>当 cs 角色尝试 ban/unban（需 admin）时，返回 ROLE_REQUIRED (5002)。
 */
@RestControllerAdvice
public class AdminExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AdminExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ResponseBuilder.error(ErrorCode.ROLE_REQUIRED.getCode(),
                        ErrorCode.ROLE_REQUIRED.getMessage()));
    }
}

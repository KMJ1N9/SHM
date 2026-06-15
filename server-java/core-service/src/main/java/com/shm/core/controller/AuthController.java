package com.shm.core.controller;

import com.shm.common.model.dto.auth.LoginRequest;
import com.shm.common.model.dto.auth.RefreshRequest;
import com.shm.common.model.dto.auth.RefreshResponse;
import com.shm.common.util.ResponseBuilder;
import com.shm.core.security.CurrentUser;
import com.shm.core.security.UserPrincipal;
import com.shm.core.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证控制器（与 Node.js controllers/auth.js 行为完全一致）
 *
 * <p>只做参数提取和响应组装，所有业务逻辑在 AuthService 中。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/login — 微信手机号登录
     */
    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        return ResponseBuilder.ok(authService.login(request.getCode()));
    }

    /**
     * POST /api/auth/refresh — 刷新 Token
     */
    @PostMapping("/refresh")
    public Map<String, Object> refresh(@Valid @RequestBody RefreshRequest request) {
        RefreshResponse result = authService.refresh(request.getRefreshToken());
        return ResponseBuilder.ok(result);
    }

    /**
     * GET /api/auth/me — 获取当前用户信息
     */
    @GetMapping("/me")
    public Map<String, Object> me(@CurrentUser UserPrincipal user) {
        return ResponseBuilder.ok(authService.me(user.getUserId()));
    }
}

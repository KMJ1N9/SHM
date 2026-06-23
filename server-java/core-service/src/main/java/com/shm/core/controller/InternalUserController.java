package com.shm.core.controller;

import com.shm.common.model.dto.internal.PenaltyRequest;
import com.shm.common.util.ResponseBuilder;
import com.shm.core.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 内部用户 API（Phase 13 — 跨服务事务，供 admin-service Feign 调用）
 *
 * <p>与 admin-service 的 CoreUserFeign 接口匹配。
 * 请求携带 X-Internal-Token 请求头（由 InternalTokenRequestInterceptor 注入）。
 */
@RestController
@RequestMapping("/internal")
public class InternalUserController {

    private final UserService userService;

    public InternalUserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * POST /internal/users/{userId}/penalty — 执行用户处罚（扣信誉分/封禁）
     *
     * <p>由 admin-service 的 ReportAdminService.resolveTicket() 通过 CoreUserFeign 调用。
     * 在 Seata AT 模式中作为事务分支（RM）参与全局事务。
     */
    @PostMapping("/users/{userId}/penalty")
    public Map<String, Object> applyPenalty(@PathVariable Long userId, @Valid @RequestBody PenaltyRequest request) {
        Map<String, Object> result = userService.applyPenalty(userId, request);
        return ResponseBuilder.ok(result);
    }
}

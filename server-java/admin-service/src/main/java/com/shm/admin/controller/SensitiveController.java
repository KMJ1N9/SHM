package com.shm.admin.controller;

import com.shm.admin.security.CurrentUser;
import com.shm.admin.security.UserPrincipal;
import com.shm.admin.service.SensitiveService;
import com.shm.common.util.ResponseBuilder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 敏感词库管理控制器（与 Node.js adminController 敏感词管理部分行为完全一致）
 *
 * <p>仅管理员可操作。
 */
@RestController
@RequestMapping("/api/admin/sensitive")
public class SensitiveController {

    private final SensitiveService sensitiveService;

    public SensitiveController(SensitiveService sensitiveService) {
        this.sensitiveService = sensitiveService;
    }

    /**
     * GET /api/admin/sensitive/stats — 词库统计
     */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return ResponseBuilder.ok(sensitiveService.stats());
    }

    /**
     * POST /api/admin/sensitive/reload — 重新加载词库
     *
     * <p>仅管理员可操作（cs 角色不可执行）。
     */
    @PostMapping("/reload")
    @PreAuthorize("hasRole('admin')")
    public Map<String, Object> reload(@CurrentUser UserPrincipal user) {
        return ResponseBuilder.ok(sensitiveService.reload(user.getUserId()));
    }

    /**
     * POST /api/admin/sensitive/check — 检查文本
     */
    @PostMapping("/check")
    public Map<String, Object> check(@Valid @RequestBody SensitiveCheckRequest request) {
        return ResponseBuilder.ok(sensitiveService.check(request.getText()));
    }

    private static class SensitiveCheckRequest {
        @NotBlank(message = "文本不能为空")
        private String text;

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }
}

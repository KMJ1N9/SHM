package com.shm.admin.controller;

import com.shm.admin.security.CurrentUser;
import com.shm.admin.security.UserPrincipal;
import com.shm.admin.service.UserAdminService;
import com.shm.common.util.ResponseBuilder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户管理控制器（与 Node.js adminController 用户管理部分行为完全一致）
 *
 * <p>仅管理员可操作（角色校验在 JwtAuthFilter + @PreAuthorize）。
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    /**
     * GET /api/admin/users — 用户列表
     */
    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) String role,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int pageSize) {
        pageSize = Math.min(pageSize, 50);
        return ResponseBuilder.ok(userAdminService.listUsers(keyword, status, role, page, pageSize));
    }

    /**
     * PUT /api/admin/users/:id/ban — 封禁用户
     */
    @PutMapping("/{id}/ban")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('admin')")
    public Map<String, Object> ban(@PathVariable Long id,
                                    @CurrentUser UserPrincipal user) {
        return ResponseBuilder.ok(userAdminService.banUser(id, user.getUserId()));
    }

    /**
     * PUT /api/admin/users/:id/unban — 解封用户
     */
    @PutMapping("/{id}/unban")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('admin')")
    public Map<String, Object> unban(@PathVariable Long id,
                                      @CurrentUser UserPrincipal user) {
        return ResponseBuilder.ok(userAdminService.unbanUser(id, user.getUserId()));
    }
}

package com.shm.core.controller;

import com.shm.common.model.dto.user.UpdateProfileRequest;
import com.shm.common.util.ResponseBuilder;
import com.shm.core.security.CurrentUser;
import com.shm.core.security.UserPrincipal;
import com.shm.core.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户控制器（与 Node.js controllers/user.js 行为完全一致）
 *
 * <p>只做参数提取和响应组装，所有业务逻辑在 UserService 中。
 */
@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * GET /api/users/:id — 查看用户公开信息
     */
    @GetMapping("/api/users/{id}")
    public Map<String, Object> getById(@PathVariable Long id) {
        return ResponseBuilder.ok(userService.getPublicProfile(id));
    }

    /**
     * GET /api/users/cs/contact — 获取客服联系方式
     */
    @GetMapping("/api/users/cs/contact")
    public Map<String, Object> getCSContact() {
        return ResponseBuilder.ok(userService.getCSContact());
    }

    /**
     * GET /api/users/admin/contact — 获取管理员联系方式
     */
    @GetMapping("/api/users/admin/contact")
    public Map<String, Object> getAdminContact() {
        return ResponseBuilder.ok(userService.getAdminContact());
    }

    /**
     * PUT /api/users/me — 编辑个人资料
     */
    @PutMapping("/api/users/me")
    public Map<String, Object> updateProfile(@CurrentUser UserPrincipal user,
                                              @RequestBody UpdateProfileRequest request) {
        return ResponseBuilder.ok(userService.updateProfile(user.getUserId(), request, user.getRole()));
    }
}

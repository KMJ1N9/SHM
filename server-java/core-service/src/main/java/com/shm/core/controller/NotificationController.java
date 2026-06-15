package com.shm.core.controller;

import com.shm.common.util.ResponseBuilder;
import com.shm.core.security.CurrentUser;
import com.shm.core.security.UserPrincipal;
import com.shm.core.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 通知控制器（与 Node.js controllers/notification.js 行为完全一致）
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * GET /api/notifications — 我的通知列表
     */
    @GetMapping
    public Map<String, Object> list(@CurrentUser UserPrincipal user,
                                    @RequestParam(required = false) String type,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int pageSize) {
        pageSize = Math.min(pageSize, 50);
        return ResponseBuilder.ok(notificationService.list(user.getUserId(), type, page, pageSize));
    }

    /**
     * GET /api/notifications/unread-count — 未读通知数
     */
    @GetMapping("/unread-count")
    public Map<String, Object> unreadCount(@CurrentUser UserPrincipal user) {
        return ResponseBuilder.ok(notificationService.unreadCount(user.getUserId()));
    }

    /**
     * PUT /api/notifications/:id/read — 标记已读
     */
    @PutMapping("/{id}/read")
    public Map<String, Object> read(@PathVariable Long id, @CurrentUser UserPrincipal user) {
        notificationService.read(id, user.getUserId());
        return ResponseBuilder.ok(null);
    }

    /**
     * PUT /api/notifications/read-all — 全部标记已读
     */
    @PutMapping("/read-all")
    public Map<String, Object> readAll(@CurrentUser UserPrincipal user) {
        return ResponseBuilder.ok(notificationService.readAll(user.getUserId()));
    }
}

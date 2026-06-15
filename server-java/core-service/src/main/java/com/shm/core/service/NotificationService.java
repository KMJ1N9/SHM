package com.shm.core.service;

import com.shm.common.exception.BusinessException;
import com.shm.common.exception.ErrorCode;
import com.shm.common.model.entity.Notification;
import com.shm.core.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 通知服务（与 Node.js services/notification.js 行为完全一致）
 *
 * <p>处理站内通知的查询、已读、未读数。
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepo;

    public NotificationService(NotificationRepository notificationRepo) {
        this.notificationRepo = notificationRepo;
    }

    /**
     * 我的通知列表（与 Node.js notificationService.list 一致）
     */
    public Map<String, Object> list(Long userId, String type, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<Notification> notifications = notificationRepo.listByUserId(userId, type, offset, pageSize);
        long total = notificationRepo.countByUserId(userId, type);

        List<Map<String, Object>> list = notifications.stream()
                .map(this::toNotificationMap)
                .toList();

        return Map.of("list", list, "total", total, "page", page, "pageSize", pageSize);
    }

    /**
     * 未读通知数（与 Node.js notificationService.unreadCount 一致）
     */
    public Map<String, Object> unreadCount(Long userId) {
        long count = notificationRepo.countUnread(userId);
        return Map.of("count", count);
    }

    /**
     * 标记单条通知为已读（与 Node.js notificationService.read 一致）
     */
    public void read(Long notificationId, Long userId) {
        int affected = notificationRepo.markRead(notificationId, userId);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "通知不存在");
        }
    }

    /**
     * 全部标记为已读（与 Node.js notificationService.readAll 一致）
     */
    public Map<String, Object> readAll(Long userId) {
        int updated = notificationRepo.markAllRead(userId);
        return Map.of("updated", updated);
    }

    // ---- 辅助方法 ----

    private Map<String, Object> toNotificationMap(Notification n) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", n.getId());
        map.put("type", n.getType());
        map.put("title", n.getTitle());
        map.put("content", n.getContent());
        map.put("is_read", n.getIsRead());
        map.put("metadata", n.getMetadata());
        map.put("created_at", n.getCreatedAt());
        return map;
    }
}

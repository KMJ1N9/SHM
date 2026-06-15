package com.shm.core.repository;

import com.shm.common.model.entity.Notification;
import com.shm.core.mapper.NotificationMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 通知 Repository（封装 NotificationMapper）
 */
@Repository
public class NotificationRepository {

    private final NotificationMapper mapper;

    public NotificationRepository(NotificationMapper mapper) {
        this.mapper = mapper;
    }

    public Notification insert(Notification notification) {
        mapper.insert(notification);
        return notification;
    }

    public List<Notification> listByUserId(Long userId, String type, int offset, int limit) {
        return mapper.listByUserId(userId, type, offset, limit);
    }

    public long countByUserId(Long userId, String type) {
        return mapper.countByUserId(userId, type);
    }

    public long countUnread(Long userId) {
        return mapper.countUnread(userId);
    }

    public int markRead(Long id, Long userId) {
        return mapper.markRead(id, userId);
    }

    public int markAllRead(Long userId) {
        return mapper.markAllRead(userId);
    }
}

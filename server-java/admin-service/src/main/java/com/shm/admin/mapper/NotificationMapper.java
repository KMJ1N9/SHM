package com.shm.admin.mapper;

import com.shm.common.model.entity.Notification;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

/**
 * Admin 端通知 Mapper（用于 resolveTicket 事务中写入信誉分变动通知）
 */
@Mapper
public interface NotificationMapper {

    @Insert("INSERT INTO notifications (user_id, type, title, content, metadata) " +
            "VALUES (#{userId}, #{type}, #{title}, #{content}, #{metadata})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Notification notification);
}

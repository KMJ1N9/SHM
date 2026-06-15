package com.shm.core.mapper;

import com.shm.common.model.entity.Notification;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 通知 Mapper（对应 notifications 表）
 */
@Mapper
public interface NotificationMapper {

    @Insert("INSERT INTO notifications (user_id, type, title, content, is_read, metadata) " +
            "VALUES (#{userId}, #{type}, #{title}, #{content}, #{isRead}, #{metadata})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Notification notification);

    @Select("<script>" +
            "SELECT id, user_id, type, title, content, is_read, metadata, created_at FROM notifications WHERE user_id = #{userId}" +
            "<if test='type != null and type != \"\" and type != \"all\"'> AND type = #{type}</if>" +
            "ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<Notification> listByUserId(@Param("userId") Long userId, @Param("type") String type,
                                    @Param("offset") int offset, @Param("limit") int limit);

    @Select("<script>" +
            "SELECT COUNT(*) FROM notifications WHERE user_id = #{userId}" +
            "<if test='type != null and type != \"\" and type != \"all\"'> AND type = #{type}</if>" +
            "</script>")
    long countByUserId(@Param("userId") Long userId, @Param("type") String type);

    @Select("SELECT COUNT(*) FROM notifications WHERE user_id = #{userId} AND is_read = 0")
    long countUnread(Long userId);

    @Update("UPDATE notifications SET is_read = 1 WHERE id = #{id} AND user_id = #{userId}")
    int markRead(@Param("id") Long id, @Param("userId") Long userId);

    @Update("UPDATE notifications SET is_read = 1 WHERE user_id = #{userId} AND is_read = 0")
    int markAllRead(Long userId);
}

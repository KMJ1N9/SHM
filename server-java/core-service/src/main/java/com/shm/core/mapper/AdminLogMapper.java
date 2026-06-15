package com.shm.core.mapper;

import com.shm.common.model.entity.AdminLog;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理日志 Mapper（对应 admin_logs 表）
 */
@Mapper
public interface AdminLogMapper {

    @Insert("INSERT INTO admin_logs (admin_id, action, target_type, target_id, reason) " +
            "VALUES (#{adminId}, #{action}, #{targetType}, #{targetId}, #{reason})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AdminLog log);

    @Select("SELECT id, admin_id, action, target_type, target_id, reason, created_at FROM admin_logs WHERE admin_id = #{adminId} ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<AdminLog> listByAdmin(@Param("adminId") Long adminId, @Param("offset") int offset, @Param("limit") int limit);

    @Select("<script>" +
            "SELECT id, admin_id, action, target_type, target_id, reason, created_at FROM admin_logs" +
            "<where>" +
            "<if test='action != null and action != \"\"'> AND action = #{action}</if>" +
            "<if test='targetType != null and targetType != \"\"'> AND target_type = #{targetType}</if>" +
            "<if test='start != null'> AND created_at >= #{start}</if>" +
            "<if test='end != null'><![CDATA[ AND created_at <= #{end} ]]></if>" +
            "</where>" +
            "ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<AdminLog> listWithFilters(@Param("action") String action, @Param("targetType") String targetType,
                                    @Param("offset") int offset, @Param("limit") int limit,
                                    @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}

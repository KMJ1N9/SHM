package com.shm.admin.mapper;

import com.shm.common.model.entity.AdminLog;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Admin 端管理日志 Mapper（对应 admin_logs 表）
 */
@Mapper
public interface AdminLogMapper {

    @Insert("INSERT INTO admin_logs (admin_id, action, target_type, target_id, reason) " +
            "VALUES (#{adminId}, #{action}, #{targetType}, #{targetId}, #{reason})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AdminLog log);

    @Select("SELECT id, admin_id, action, target_type, target_id, reason, created_at FROM admin_logs WHERE admin_id = #{adminId} ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<AdminLog> listByAdmin(@Param("adminId") Long adminId, @Param("offset") int offset, @Param("limit") int limit);

    /** 审计日志列表（含 admin_phone，与 Node.js reportRepo.listAdminLogs 一致） */
    @Select("<script>" +
            "SELECT al.id, al.admin_id, al.action, al.target_type, al.target_id, " +
            "al.reason, al.created_at, u.phone AS admin_phone " +
            "FROM admin_logs al JOIN users u ON al.admin_id = u.id" +
            "<where>" +
            "<if test='action != null and action != \"\"'> AND al.action = #{action}</if>" +
            "<if test='targetType != null and targetType != \"\"'> AND al.target_type = #{targetType}</if>" +
            "<if test='start != null'> AND al.created_at >= #{start}</if>" +
            "<if test='end != null'><![CDATA[ AND al.created_at <= #{end} ]]></if>" +
            "</where>" +
            "ORDER BY al.created_at DESC LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<java.util.Map<String, Object>> listWithFilters(@Param("action") String action, @Param("targetType") String targetType,
                                    @Param("offset") int offset, @Param("limit") int limit,
                                    @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 审计日志计数 */
    @Select("<script>" +
            "SELECT COUNT(*) FROM admin_logs al" +
            "<where>" +
            "<if test='action != null and action != \"\"'> AND al.action = #{action}</if>" +
            "<if test='targetType != null and targetType != \"\"'> AND al.target_type = #{targetType}</if>" +
            "<if test='start != null'> AND al.created_at >= #{start}</if>" +
            "<if test='end != null'><![CDATA[ AND al.created_at <= #{end} ]]></if>" +
            "</where>" +
            "</script>")
    long countWithFilters(@Param("action") String action, @Param("targetType") String targetType,
                          @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}

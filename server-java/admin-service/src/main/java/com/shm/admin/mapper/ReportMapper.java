package com.shm.admin.mapper;

import com.shm.common.model.entity.Report;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * Admin 端举报/工单 Mapper（与 Node.js reportRepo 行为完全一致）
 *
 * <p>对应 reports 表，支持管理端工单列表（含举报人/被举报人 JOIN 查询）。
 */
@Mapper
public interface ReportMapper {

    /** 工单详情（含举报人/被举报人昵称头像，与 Node.js findDetailById 一致） */
    @Select("SELECT r.id, r.reporter_id, r.reported_user_id, r.product_id, r.order_id, " +
            "r.type, r.description, r.evidence_images, r.status, " +
            "r.resolution, r.deleted_at, r.resolved_at, r.created_at, r.updated_at, " +
            "reporter.nickname AS reporter_nickname, reporter.avatar AS reporter_avatar, " +
            "reported.nickname AS reported_nickname, reported.avatar AS reported_avatar " +
            "FROM reports r " +
            "JOIN users reporter ON r.reporter_id = reporter.id " +
            "JOIN users reported ON r.reported_user_id = reported.id " +
            "WHERE r.id = #{id}")
    Map<String, Object> findDetailById(Long id);

    /** 根据 ID 查找工单（不含 JOIN） */
    @Select("SELECT id, reporter_id, reported_user_id, product_id, order_id, " +
            "type, description, evidence_images, status, " +
            "resolution, deleted_at, resolved_at, created_at, updated_at " +
            "FROM reports WHERE id = #{id}")
    Report findById(Long id);

    /** 更新工单状态 */
    @Update("UPDATE reports SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 裁决工单（写入 resolution + resolved_at + 状态变更） */
    @Update("UPDATE reports SET status = 'resolved', resolution = #{resolution}, resolved_at = NOW() WHERE id = #{id}")
    int resolve(@Param("id") Long id, @Param("resolution") String resolution);

    /** 工单列表（含过滤 + JOIN 用户表，与 Node.js reportRepo.list 一致） */
    List<Map<String, Object>> listWithFilters(@Param("status") String status,
                                               @Param("type") String type,
                                               @Param("offset") int offset,
                                               @Param("limit") int limit);

    /** 工单计数 */
    long countWithFilters(@Param("status") String status,
                          @Param("type") String type);
}

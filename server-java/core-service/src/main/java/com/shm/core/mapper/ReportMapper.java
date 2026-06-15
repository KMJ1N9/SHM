package com.shm.core.mapper;

import com.shm.common.model.entity.Report;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 举报 Mapper（对应 reports 表，复杂查询在 ReportMapper.xml）
 */
@Mapper
public interface ReportMapper {

    @Insert("INSERT INTO reports (reporter_id, reported_user_id, product_id, order_id, type, description, evidence_images, status) " +
            "VALUES (#{reporterId}, #{reportedUserId}, #{productId}, #{orderId}, #{type}, #{description}, #{evidenceImages}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Report report);

    @Select("SELECT id, reporter_id, reported_user_id, product_id, order_id, type, description, evidence_images, status, resolution, deleted_at, created_at, updated_at, resolved_at FROM reports WHERE id = #{id} AND deleted_at IS NULL")
    Report findById(Long id);

    /**
     * 举报列表（支持 reporter_id 过滤，传 null 查全部）— 实现在 ReportMapper.xml
     */
    List<Report> listWithFilters(@Param("reporterId") Long reporterId, @Param("status") String status,
                                  @Param("type") String type, @Param("offset") int offset, @Param("limit") int limit);

    /**
     * 举报计数（支持 reporter_id 过滤，传 null 查全部）— 实现在 ReportMapper.xml
     */
    long countWithFilters(@Param("reporterId") Long reporterId, @Param("status") String status,
                           @Param("type") String type);

    @Update("UPDATE reports SET status = #{status}, resolution = #{resolution}, resolved_at = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("resolution") String resolution);

    /** 检查是否存在未处理的重复举报（防刷） */
    @Select("<script>" +
            "SELECT COUNT(*) FROM reports WHERE reporter_id = #{reporterId}" +
            " AND reported_user_id = #{reportedUserId}" +
            " AND status IN ('pending', 'investigating')" +
            " AND deleted_at IS NULL" +
            "<if test='productId != null'> AND product_id = #{productId}</if>" +
            "<if test='orderId != null'> AND order_id = #{orderId}</if>" +
            "</script>")
    long countActiveByReporter(@Param("reporterId") Long reporterId, @Param("reportedUserId") Long reportedUserId,
                                @Param("productId") Long productId, @Param("orderId") Long orderId);
}

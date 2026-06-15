package com.shm.admin.mapper;

import com.shm.common.model.entity.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Admin 端用户 Mapper（与 Node.js userRepo.listWithFilters 行为完全一致）
 *
 * <p>管理端用户列表：支持关键词搜索（phone/nickname）+ 状态/角色筛选 + 分页。
 */
@Mapper
public interface UserMapper {

    /** 根据 ID 查用户全量字段 */
    @Select("SELECT id, phone, nickname, avatar, class_name, dorm_building, role, status, token_version, credit_score, created_at, updated_at FROM users WHERE id = #{id}")
    User findById(Long id);

    /** 管理端用户列表（搜索 + 筛选 + 分页，与 Node.js userRepo.listWithFilters 一致） */
    List<User> listWithFilters(@Param("keyword") String keyword,
                               @Param("status") String status,
                               @Param("role") String role,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    /** 管理端用户计数 */
    long countWithFilters(@Param("keyword") String keyword,
                          @Param("status") String status,
                          @Param("role") String role);

    /** 封禁/解封用户（更新 status + token_version+1 使已有 JWT 失效） */
    @Update("UPDATE users SET status = #{status}, token_version = token_version + 1 WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 更新用户角色（管理员自动提升用） */
    @Update("UPDATE users SET role = #{role} WHERE id = #{id}")
    int updateRole(@Param("id") Long id, @Param("role") String role);

    /** 更新信誉分（LEAST 上限 + GREATEST 下限，与 core-service 和 Node.js 行为完全一致） */
    @Update("UPDATE users SET credit_score = LEAST(GREATEST(credit_score + #{delta}, 0), #{max}) WHERE id = #{id}")
    int updateCreditScore(@Param("id") Long id, @Param("delta") int delta, @Param("max") int max);

    /** FOR UPDATE 锁定用户行（防并发扣分） */
    @Select("SELECT id, credit_score FROM users WHERE id = #{id} FOR UPDATE")
    User findByIdForUpdate(Long id);
}

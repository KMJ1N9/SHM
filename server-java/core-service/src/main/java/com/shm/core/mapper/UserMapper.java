package com.shm.core.mapper;

import com.shm.common.model.entity.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 用户 Mapper（对应 users 表）
 */
@Mapper
public interface UserMapper {

    @Select("SELECT id, phone, nickname, avatar, class_name, dorm_building, role, status, token_version, credit_score, created_at, updated_at FROM users WHERE id = #{id}")
    User findById(Long id);

    /** 仅返回公开字段（与 Node.js PUBLIC_USER_FIELDS 一致：id, nickname, avatar, class_name, dorm_building, credit_score, created_at） */
    @Select("SELECT id, nickname, avatar, class_name, dorm_building, credit_score, created_at FROM users WHERE id = #{id}")
    User findPublicById(Long id);

    @Select("SELECT id, phone, nickname, avatar, class_name, dorm_building, role, status, token_version, credit_score, created_at, updated_at FROM users WHERE phone = #{phone}")
    User findByPhone(String phone);

    @Insert("INSERT INTO users (phone, nickname, avatar, class_name, dorm_building, role, status, token_version, credit_score) " +
            "VALUES (#{phone}, #{nickname}, #{avatar}, #{className}, #{dormBuilding}, #{role}, #{status}, #{tokenVersion}, #{creditScore})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    /** 动态更新个人资料 — 实现在 UserMapper.xml（仅 SET 非 null 字段，防 NULL 覆盖） */
    int updateProfile(User user);

    @Update("UPDATE users SET credit_score = LEAST(GREATEST(credit_score + #{delta}, 0), #{max}) WHERE id = #{id}")
    int updateCreditScore(@Param("id") Long id, @Param("delta") int delta, @Param("max") int max);

    @Update("UPDATE users SET status = #{status}, token_version = token_version + 1 WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 更新用户角色（用于管理员自动提升） */
    @Update("UPDATE users SET role = #{role} WHERE id = #{id}")
    int updateRole(@Param("id") Long id, @Param("role") String role);

    @Select("SELECT id, nickname, avatar FROM users WHERE role = 'cs' LIMIT 1")
    User findCSUser();

    @Select("SELECT id, nickname, avatar FROM users WHERE role = 'admin' LIMIT 1")
    User findAdminUser();

    /** 查询全部管理员用户（用于举报通知等群发场景） */
    @Select("SELECT id, nickname, avatar FROM users WHERE role = 'admin'")
    List<User> findAdminUsers();

    @Select("<script>" +
            "SELECT id, phone, nickname, avatar, class_name, dorm_building, role, status, token_version, credit_score, created_at, updated_at FROM users" +
            "<where>" +
            "<if test='keyword != null and keyword != \"\"'> AND (nickname LIKE CONCAT('%', #{keyword}, '%') OR phone LIKE CONCAT('%', #{keyword}, '%'))</if>" +
            "<if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            "<if test='role != null and role != \"\"'> AND role = #{role}</if>" +
            "</where>" +
            "ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<User> listWithFilters(@Param("keyword") String keyword, @Param("status") String status,
                                @Param("role") String role, @Param("offset") int offset, @Param("limit") int limit);

    @Select("<script>" +
            "SELECT COUNT(*) FROM users" +
            "<where>" +
            "<if test='keyword != null and keyword != \"\"'> AND (nickname LIKE CONCAT('%', #{keyword}, '%') OR phone LIKE CONCAT('%', #{keyword}, '%'))</if>" +
            "<if test='status != null and status != \"\"'> AND status = #{status}</if>" +
            "<if test='role != null and role != \"\"'> AND role = #{role}</if>" +
            "</where>" +
            "</script>")
    long countWithFilters(@Param("keyword") String keyword, @Param("status") String status, @Param("role") String role);

    /** 批量按 ID 查询用户（仅返回公开字段，用于商品列表卖家信息） */
    @Select("<script>" +
            "SELECT id, nickname, avatar, credit_score FROM users WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<User> findByIds(@Param("ids") java.util.Set<Long> ids);
}

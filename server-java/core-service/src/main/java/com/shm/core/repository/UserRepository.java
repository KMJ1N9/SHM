package com.shm.core.repository;

import com.shm.common.model.entity.User;
import com.shm.core.mapper.UserMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户 Repository（封装 UserMapper，对应 Node.js repository/user.js）
 */
@Repository
public class UserRepository {

    private final UserMapper mapper;

    public UserRepository(UserMapper mapper) {
        this.mapper = mapper;
    }

    public User findByPhone(String phone) {
        return mapper.findByPhone(phone);
    }

    public User findById(Long id) {
        return mapper.findById(id);
    }

    /**
     * 查询用户公开信息（不含手机号、角色、状态、token_version）
     */
    public User findPublicById(Long id) {
        return mapper.findPublicById(id);
    }

    public User create(User user) {
        mapper.insert(user);
        return user;
    }

    public int updateProfile(User user) {
        return mapper.updateProfile(user);
    }

    public int updateCreditScore(Long id, int delta, int max) {
        return mapper.updateCreditScore(id, delta, max);
    }

    public int updateStatus(Long id, String status) {
        return mapper.updateStatus(id, status);
    }

    /** 更新用户角色（用于管理员自动提升） */
    public int updateRole(Long id, String role) {
        return mapper.updateRole(id, role);
    }

    public User findCSUser() {
        return mapper.findCSUser();
    }

    public User findAdminUser() {
        return mapper.findAdminUser();
    }

    /** 查询全部管理员用户（用于举报通知等群发场景） */
    public List<User> findAdminUsers() {
        return mapper.findAdminUsers();
    }

    public List<User> listWithFilters(String keyword, String status, String role, int offset, int limit) {
        return mapper.listWithFilters(keyword, status, role, offset, limit);
    }

    public long countWithFilters(String keyword, String status, String role) {
        return mapper.countWithFilters(keyword, status, role);
    }

    /**
     * 批量查询用户公开信息（用于商品列表卖家信息填充）
     */
    public java.util.List<User> findByIds(java.util.Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return java.util.List.of();
        }
        return mapper.findByIds(ids);
    }
}

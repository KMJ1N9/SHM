package com.shm.admin.service;

import com.shm.admin.mapper.AdminLogMapper;
import com.shm.admin.mapper.UserMapper;
import com.shm.common.exception.BusinessException;
import com.shm.common.exception.ErrorCode;
import com.shm.common.model.entity.AdminLog;
import com.shm.common.model.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 管理端用户服务（与 Node.js services/admin.js 用户管理部分行为完全一致）
 *
 * <p>处理用户列表查询、封禁、解封。
 */
@Service
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);

    private final UserMapper userMapper;
    private final AdminLogMapper adminLogMapper;

    public UserAdminService(UserMapper userMapper, AdminLogMapper adminLogMapper) {
        this.userMapper = userMapper;
        this.adminLogMapper = adminLogMapper;
    }

    /**
     * 用户列表（与 Node.js adminService.listUsers 一致）
     *
     * @param keyword  搜索关键词（phone/nickname）
     * @param status   状态筛选
     * @param role     角色筛选
     * @param page     页码
     * @param pageSize 每页条数（上限 50）
     */
    public Map<String, Object> listUsers(String keyword, String status, String role, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<User> users = userMapper.listWithFilters(keyword, status, role, offset, pageSize);
        long total = userMapper.countWithFilters(keyword, status, role);
        return Map.of("list", users, "total", total, "page", page, "pageSize", pageSize);
    }

    /**
     * 封禁用户（与 Node.js adminService.banUser 一致）
     *
     * <p>不能封禁 admin 角色的用户。
     */
    @Transactional
    public Map<String, Object> banUser(Long userId, Long adminId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if ("admin".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能封禁管理员");
        }

        userMapper.updateStatus(userId, "banned");

        // 记录操作日志
        AdminLog adminLog = AdminLog.builder()
                .adminId(adminId)
                .action("ban")
                .targetType("user")
                .targetId(userId)
                .build();
        adminLogMapper.insert(adminLog);

        log.info("用户封禁: userId={}, adminId={}", userId, adminId);

        User updated = userMapper.findById(userId);
        return toUserMap(updated);
    }

    /**
     * 解封用户（与 Node.js adminService.unbanUser 一致）
     */
    @Transactional
    public Map<String, Object> unbanUser(Long userId, Long adminId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        userMapper.updateStatus(userId, "active");

        // 记录操作日志
        AdminLog adminLog = AdminLog.builder()
                .adminId(adminId)
                .action("unban")
                .targetType("user")
                .targetId(userId)
                .build();
        adminLogMapper.insert(adminLog);

        log.info("用户解封: userId={}, adminId={}", userId, adminId);

        User updated = userMapper.findById(userId);
        return toUserMap(updated);
    }

    // ---- 辅助方法 ----

    private Map<String, Object> toUserMap(User u) {
        if (u == null) return Map.of();
        return Map.ofEntries(
                Map.entry("id", u.getId()),
                Map.entry("phone", u.getPhone() != null ? u.getPhone() : ""),
                Map.entry("nickname", u.getNickname() != null ? u.getNickname() : ""),
                Map.entry("avatar", u.getAvatar() != null ? u.getAvatar() : ""),
                Map.entry("class_name", u.getClassName() != null ? u.getClassName() : ""),
                Map.entry("dorm_building", u.getDormBuilding() != null ? u.getDormBuilding() : ""),
                Map.entry("role", u.getRole() != null ? u.getRole() : ""),
                Map.entry("status", u.getStatus() != null ? u.getStatus() : ""),
                Map.entry("credit_score", u.getCreditScore() != null ? u.getCreditScore() : 0),
                Map.entry("created_at", u.getCreatedAt()),
                Map.entry("updated_at", u.getUpdatedAt())
        );
    }
}

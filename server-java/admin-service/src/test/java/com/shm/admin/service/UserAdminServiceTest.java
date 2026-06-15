package com.shm.admin.service;

import com.shm.admin.mapper.AdminLogMapper;
import com.shm.admin.mapper.UserMapper;
import com.shm.common.exception.BusinessException;
import com.shm.common.exception.ErrorCode;
import com.shm.common.model.entity.AdminLog;
import com.shm.common.model.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserAdminService 单元测试（Phase 11 11.1.6）
 *
 * <p>覆盖用户列表/封禁/解封，Mock UserMapper + AdminLogMapper 层。
 */
@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private AdminLogMapper adminLogMapper;

    private UserAdminService userAdminService;

    @BeforeEach
    void setUp() {
        userAdminService = new UserAdminService(userMapper, adminLogMapper);
    }

    // ============================================================
    // listUsers — 用户列表
    // ============================================================

    @Test
    void listUsers_withFilters_shouldReturnPaginatedResults() {
        User user = buildUser(1L, "13800138000", "张三", "active", "user");
        when(userMapper.listWithFilters(eq("张三"), eq("active"), eq("user"), eq(0), eq(20)))
                .thenReturn(List.of(user));
        when(userMapper.countWithFilters(eq("张三"), eq("active"), eq("user")))
                .thenReturn(1L);

        Map<String, Object> result = userAdminService.listUsers("张三", "active", "user", 1, 20);

        assertEquals(1L, result.get("total"));
        @SuppressWarnings("unchecked")
        List<User> list = (List<User>) result.get("list");
        assertEquals(1, list.size());
    }

    @Test
    void listUsers_emptyResult_shouldReturnZeroTotal() {
        when(userMapper.listWithFilters(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(userMapper.countWithFilters(any(), any(), any()))
                .thenReturn(0L);

        Map<String, Object> result = userAdminService.listUsers(null, null, null, 1, 20);

        assertEquals(0L, result.get("total"));
    }

    @Test
    void listUsers_page2_shouldCalculateOffset() {
        when(userMapper.listWithFilters(isNull(), isNull(), isNull(), eq(20), eq(10)))
                .thenReturn(List.of());
        when(userMapper.countWithFilters(isNull(), isNull(), isNull()))
                .thenReturn(0L);

        userAdminService.listUsers(null, null, null, 3, 10);

        verify(userMapper).listWithFilters(isNull(), isNull(), isNull(), eq(20), eq(10));
    }

    // ============================================================
    // banUser — 封禁用户
    // ============================================================

    @Test
    void banUser_success_shouldUpdateStatusAndLog() {
        User user = buildUser(5L, "138", "李四", "active", "user");
        when(userMapper.findById(5L)).thenReturn(user);

        User updated = buildUser(5L, "138", "李四", "banned", "user");
        when(userMapper.findById(5L)).thenReturn(user)
                .thenReturn(updated); // banUser 中查询两次

        Map<String, Object> result = userAdminService.banUser(5L, 1L);

        verify(userMapper).updateStatus(5L, "banned");
        verify(adminLogMapper).insert(any(AdminLog.class));
        assertEquals("banned", result.get("status"));
    }

    @Test
    void banUser_adminTarget_shouldThrowForbidden() {
        User admin = buildUser(2L, "138", "管理员", "active", "admin");
        when(userMapper.findById(2L)).thenReturn(admin);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                userAdminService.banUser(2L, 1L));

        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        verify(userMapper, never()).updateStatus(anyLong(), anyString());
    }

    @Test
    void banUser_notFound_shouldThrowUserNotFound() {
        when(userMapper.findById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                userAdminService.banUser(999L, 1L));

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
    }

    // ============================================================
    // unbanUser — 解封用户
    // ============================================================

    @Test
    void unbanUser_success_shouldUpdateStatusAndLog() {
        User user = buildUser(5L, "138", "李四", "banned", "user");
        when(userMapper.findById(5L)).thenReturn(user);

        User updated = buildUser(5L, "138", "李四", "active", "user");
        when(userMapper.findById(5L)).thenReturn(user)
                .thenReturn(updated);

        Map<String, Object> result = userAdminService.unbanUser(5L, 1L);

        verify(userMapper).updateStatus(5L, "active");
        verify(adminLogMapper).insert(any(AdminLog.class));
        assertEquals("active", result.get("status"));
    }

    @Test
    void unbanUser_notFound_shouldThrowUserNotFound() {
        when(userMapper.findById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                userAdminService.unbanUser(999L, 1L));

        assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
    }

    // ---- 辅助 ----

    private User buildUser(Long id, String phone, String nickname, String status, String role) {
        return User.builder()
                .id(id).phone(phone).nickname(nickname)
                .avatar("/av.png").className("班级").dormBuilding("楼栋")
                .role(role).status(status).creditScore(80)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }
}

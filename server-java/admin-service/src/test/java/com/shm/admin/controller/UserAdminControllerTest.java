package com.shm.admin.controller;

import com.shm.admin.security.UserPrincipal;
import com.shm.admin.service.UserAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserAdminController 单元测试（Phase 11.2.5）
 *
 * <p>直接实例化 Controller + Mock Service，避免 @WebMvcTest 触发 Spring Cloud
 * 自动配置。验证管理员用户管理端点：列表、封禁、解封。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserAdminController")
class UserAdminControllerTest {

    @Mock
    private UserAdminService userAdminService;

    private UserAdminController controller;

    private UserPrincipal mockAdmin;

    @BeforeEach
    void setUp() {
        controller = new UserAdminController(userAdminService);
        com.shm.common.model.entity.User user = new com.shm.common.model.entity.User();
        user.setId(99L);
        user.setPhone("13800000099");
        user.setNickname("管理员");
        user.setAvatar("");
        user.setClassName("CS2024");
        user.setDormBuilding("北区");
        user.setRole("admin");
        user.setStatus("active");
        user.setCreditScore(100);
        mockAdmin = new UserPrincipal(user);
    }

    // ---- list ----

    @Test
    @DisplayName("GET /api/admin/users — 用户列表，默认分页")
    void listUsers_shouldReturnPagedResult() {
        Map<String, Object> serviceResult = new LinkedHashMap<>();
        serviceResult.put("list", List.of());
        serviceResult.put("total", 0L);
        serviceResult.put("page", 1);
        serviceResult.put("pageSize", 20);
        when(userAdminService.listUsers(isNull(), isNull(), isNull(), eq(1), eq(20)))
                .thenReturn(serviceResult);

        Map<String, Object> response = controller.list(null, null, null, 1, 20);

        assertEquals(0, response.get("code"));
        assertEquals("ok", response.get("message"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        assertNotNull(data);
        assertEquals(0L, data.get("total"));
    }

    @Test
    @DisplayName("GET /api/admin/users — pageSize 超过 50 自动截断")
    void listUsers_shouldCapPageSizeAt50() {
        when(userAdminService.listUsers(isNull(), isNull(), isNull(), eq(1), eq(50)))
                .thenReturn(Map.of("list", List.of(), "total", 0L));

        controller.list(null, null, null, 1, 200);

        verify(userAdminService).listUsers(isNull(), isNull(), isNull(), eq(1), eq(50));
    }

    @Test
    @DisplayName("GET /api/admin/users — 关键词/状态/角色过滤参数正确传递")
    void listUsers_shouldPassFiltersCorrectly() {
        when(userAdminService.listUsers(eq("张三"), eq("banned"), eq("user"), eq(2), eq(10)))
                .thenReturn(Map.of("list", List.of(), "total", 0L));

        controller.list("张三", "banned", "user", 2, 10);

        verify(userAdminService).listUsers("张三", "banned", "user", 2, 10);
    }

    // ---- ban ----

    @Test
    @DisplayName("PUT /api/admin/users/:id/ban — 封禁用户")
    void banUser_shouldReturnUpdatedUser() {
        Map<String, Object> bannedUser = new LinkedHashMap<>();
        bannedUser.put("id", 50L);
        bannedUser.put("status", "banned");
        when(userAdminService.banUser(50L, 99L)).thenReturn(bannedUser);

        Map<String, Object> response = controller.ban(50L, mockAdmin);

        assertEquals(0, response.get("code"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        assertEquals(50L, data.get("id"));
        assertEquals("banned", data.get("status"));
        verify(userAdminService).banUser(50L, 99L);
    }

    @Test
    @DisplayName("PUT /api/admin/users/:id/ban — 封禁不存在的用户时服务应抛异常")
    void banUser_shouldDelegateErrorToGlobalHandler() {
        doThrow(new RuntimeException("用户不存在")).when(userAdminService).banUser(99999L, 99L);

        assertThrows(RuntimeException.class,
                () -> controller.ban(99999L, mockAdmin));
    }

    // ---- unban ----

    @Test
    @DisplayName("PUT /api/admin/users/:id/unban — 解封用户")
    void unbanUser_shouldReturnUpdatedUser() {
        Map<String, Object> unbannedUser = new LinkedHashMap<>();
        unbannedUser.put("id", 50L);
        unbannedUser.put("status", "active");
        when(userAdminService.unbanUser(50L, 99L)).thenReturn(unbannedUser);

        Map<String, Object> response = controller.unban(50L, mockAdmin);

        assertEquals(0, response.get("code"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        assertEquals("active", data.get("status"));
        verify(userAdminService).unbanUser(50L, 99L);
    }

    @Test
    @DisplayName("PUT /api/admin/users/:id/unban — 解封不存在的用户时服务应抛异常")
    void unbanUser_shouldDelegateErrorToGlobalHandler() {
        doThrow(new RuntimeException("用户不存在")).when(userAdminService).unbanUser(99999L, 99L);

        assertThrows(RuntimeException.class,
                () -> controller.unban(99999L, mockAdmin));
    }
}

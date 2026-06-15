package com.shm.core.service;

import com.shm.common.exception.BusinessException;
import com.shm.core.config.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * WeChatService 单元测试
 *
 * <p>测试 mock 模式下的手机号解析逻辑（仅 dev/test 环境），无需真实微信 API。
 */
@ExtendWith(MockitoExtension.class)
class WeChatServiceTest {

    @Mock
    private Environment env;

    private WeChatService weChatService;

    @BeforeEach
    void setUp() {
        weChatService = new WeChatService(new AppConfig(), env);
    }

    // ============================================================
    // dev/test 环境 — mock 模式
    // ============================================================

    @Test
    void mockCode_shouldReturnPhoneNumber() {
        givenDevEnvironment();

        String phone = weChatService.getPhoneNumber("mock_13800138000");

        assertEquals("13800138000", phone);
    }

    @Test
    void mockCode_differentPhone_shouldReturnCorrectPhone() {
        givenDevEnvironment();

        String phone = weChatService.getPhoneNumber("mock_18912345678");

        assertEquals("18912345678", phone);
    }

    // ============================================================
    // dev/test 环境 — 非 mock code
    // ============================================================

    @Test
    void nonMockCode_shouldThrowBusinessException() {
        givenDevEnvironment();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                weChatService.getPhoneNumber("real_wechat_code"));

        assertEquals(6003, ex.getCode());
        assertTrue(ex.getMessage().contains("获取手机号失败"));
    }

    @Test
    void nullCode_shouldThrowBusinessException() {
        givenDevEnvironment();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                weChatService.getPhoneNumber(null));

        assertEquals(6003, ex.getCode());
    }

    @Test
    void emptyCode_shouldThrowBusinessException() {
        givenDevEnvironment();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                weChatService.getPhoneNumber(""));

        assertEquals(6003, ex.getCode());
    }

    // ============================================================
    // 边界条件
    // ============================================================

    @Test
    void mockCodeWithUnderscoreInPhone_shouldHandleCorrectly() {
        givenDevEnvironment();

        String phone = weChatService.getPhoneNumber("mock_13800138000");

        assertEquals("13800138000", phone);
    }

    // ============================================================
    // 生产环境 — 拒绝 mock code
    // ============================================================

    @Test
    void productionEnv_mockCode_shouldThrowBusinessException() {
        givenProductionEnvironment();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                weChatService.getPhoneNumber("mock_13800138000"));

        assertEquals(6003, ex.getCode());
        assertTrue(ex.getMessage().contains("获取手机号失败"));
    }

    @Test
    void productionEnv_realCode_shouldThrowBusinessException() {
        givenProductionEnvironment();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                weChatService.getPhoneNumber("real_wechat_code"));

        assertEquals(6003, ex.getCode());
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    /** 模拟 dev/test 环境：mock 登录可用 */
    private void givenDevEnvironment() {
        when(env.acceptsProfiles(any(Profiles.class))).thenReturn(true);
    }

    /** 模拟生产环境：mock 登录被拒绝 */
    private void givenProductionEnvironment() {
        when(env.acceptsProfiles(any(Profiles.class))).thenReturn(false);
    }
}

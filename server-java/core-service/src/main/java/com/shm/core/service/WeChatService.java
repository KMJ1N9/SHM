package com.shm.core.service;

import com.shm.common.exception.BusinessException;
import com.shm.common.exception.ErrorCode;
import com.shm.core.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * 微信小程序服务（与 Node.js services/auth.js 中微信 API 调用逻辑一致）
 *
 * <p>MVP 阶段保留 mock 逻辑：仅 dev/test 环境下 code 以 "mock_" 开头时直接返回手机号。
 * <p>生产环境：始终调用真实微信 API，禁止 mock 登录（防止伪装手机号）。
 */
@Service
public class WeChatService {

    private static final Logger log = LoggerFactory.getLogger(WeChatService.class);

    /** 生产环境调用微信 API 时需要（当前 MVP 阶段使用 mock，暂未使用） */
    @SuppressWarnings("unused")
    private final AppConfig appConfig;
    private final Environment env;

    public WeChatService(AppConfig appConfig, Environment env) {
        this.appConfig = appConfig;
        this.env = env;
    }

    /**
     * 用微信 getPhoneNumber code 换取手机号
     *
     * <p>MVP 开发阶段（仅 dev/test 环境）：code 以 "mock_" 开头时直接返回 code.replace("mock_", "")
     * <p>生产环境：调用微信 phonenumber.getPhoneNumber API，拒绝 mock code
     *
     * @param code 前端 wx.getPhoneNumber 返回的 code
     * @return 手机号（纯数字）
     * @throws BusinessException 微信 API 调用失败
     */
    public String getPhoneNumber(String code) {
        // MVP mock 逻辑：仅 dev/test 环境可用，与 Node.js 行为一致
        // Node.js: (NODE_ENV === 'development' || NODE_ENV === 'test') && code.startsWith('mock_')
        if (isDevOrTest() && code != null && code.startsWith("mock_")) {
            String phone = code.replace("mock_", "");
            log.debug("WeChat mock login: phone={}", phone.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
            return phone;
        }

        // TODO: 生产环境调用真实微信 API
        // 当前阶段：非 mock code 或非 dev/test 环境 → 返回错误
        log.warn("微信 API 调用失败（环境={}, code 前缀={}）",
                Arrays.toString(env.getActiveProfiles()),
                code != null ? code.substring(0, Math.min(6, code.length())) : "null");
        throw new BusinessException(ErrorCode.WECHAT_API_FAILED, "获取手机号失败，请稍后重试");
    }

    /**
     * 当前是否为开发/测试环境
     *
     * <p>与 Node.js process.env.NODE_ENV === 'development' || 'test' 对应。
     * Spring Boot 中通过 spring.profiles.active 控制。
     */
    private boolean isDevOrTest() {
        return env.acceptsProfiles(org.springframework.core.env.Profiles.of("dev", "test"));
    }
}

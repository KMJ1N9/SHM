package com.shm.core.service;

import com.shm.common.exception.BusinessException;
import com.shm.common.exception.ErrorCode;
import com.shm.common.model.dto.auth.LoginResponse;
import com.shm.common.model.dto.auth.RefreshResponse;
import com.shm.common.model.dto.auth.UserInfo;
import com.shm.common.model.entity.User;
import com.shm.common.util.JwtPayload;
import com.shm.common.util.JwtUtil;
import com.shm.core.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 认证服务（与 Node.js services/auth.js 行为完全一致）
 *
 * <p>处理微信手机号登录、Token 刷新、获取当前用户信息。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepo;
    private final WeChatService weChatService;
    private final JwtUtil jwtUtil;

    /** 管理员手机号白名单 — 匹配的手机号登录时自动提升为 admin 角色 */
    @Value("${admin.auto-phones:}")
    private String adminAutoPhones;

    public AuthService(UserRepository userRepo, WeChatService weChatService, JwtUtil jwtUtil) {
        this.userRepo = userRepo;
        this.weChatService = weChatService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 微信手机号登录（与 Node.js authService.login 一致）
     *
     * <p>流程：
     * <ol>
     *   <li>code → WeChatService.getPhoneNumber() 换手机号</li>
     *   <li>查用户 → 不存在则自动注册</li>
     *   <li>检查封禁状态</li>
     *   <li>签发双 Token</li>
     * </ol>
     *
     * @param code 微信 getPhoneNumber 返回的 code
     * @return 登录响应（含 Token + 用户信息）
     */
    public LoginResponse login(String code) {
        // 1. 微信 code 换手机号
        String phone;
        try {
            phone = weChatService.getPhoneNumber(code);
        } catch (BusinessException e) {
            // 透传 WeChatService 已包装好的业务异常（如 WECHAT_API_FAILED），
            // 避免被下方 catch (Exception e) 重复包装为 WECHAT_API_FAILED
            throw e;
        } catch (Exception e) {
            log.error("微信登录失败", e);
            throw new BusinessException(ErrorCode.WECHAT_API_FAILED, "登录失败，请稍后重试");
        }

        // 2. 查找或创建用户
        boolean isNewUser = false;
        User user = userRepo.findByPhone(phone);
        if (user == null) {
            user = User.builder()
                    .phone(phone)
                    .nickname("微信用户")
                    .role("user")
                    .status("active")
                    .tokenVersion(0)
                    .creditScore(100)
                    .build();
            user = userRepo.create(user);
            isNewUser = true;
            log.info("新用户注册: userId={}, phone={}", user.getId(),
                    phone.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
        }

        // 3. 检查封禁状态
        if ("banned".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED);
        }

        // 3.5 管理员自动提升：admin.auto-phones 白名单中的手机号自动获得 admin 角色
        if (adminAutoPhones != null && !adminAutoPhones.isBlank()) {
            Set<String> adminPhones = Set.of(adminAutoPhones.split("\\s*,\\s*"));
            if (adminPhones.contains(phone) && !"admin".equals(user.getRole())) {
                userRepo.updateRole(user.getId(), "admin");
                user.setRole("admin");
                log.info("管理员自动提升: userId={}, phone={}",
                        user.getId(), phone.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
            }
        }

        // 4. 签发双 Token
        String accessToken = jwtUtil.generateAccessToken(
                user.getId(), user.getRole(), user.getTokenVersion());
        String refreshToken = jwtUtil.generateRefreshToken(
                user.getId(), user.getTokenVersion());

        log.info("用户登录: userId={}, isNewUser={}", user.getId(), isNewUser);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .newUser(isNewUser)
                .user(UserInfo.from(user))
                .build();
    }

    /**
     * 刷新 Token（与 Node.js authService.refresh 一致）
     *
     * <p>流程：
     * <ol>
     *   <li>验证 Refresh Token 签名</li>
     *   <li>校验 type == "refresh"</li>
     *   <li>查用户 → 检查封禁 + tokenVersion 匹配</li>
     *   <li>签发新双 Token</li>
     * </ol>
     *
     * @param refreshToken JWT Refresh Token
     * @return 新 Token 对
     */
    public RefreshResponse refresh(String refreshToken) {
        // 1. 验证 Refresh Token
        JwtPayload payload;
        try {
            payload = jwtUtil.validateRefreshToken(refreshToken);
        } catch (Exception e) {
            log.warn("Refresh Token 验证失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "登录已过期，请重新登录");
        }

        // 2. 校验 token 类型
        if (!payload.isRefreshToken()) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "无效的刷新令牌");
        }

        // 3. 查用户
        User user = userRepo.findById(payload.getSub());
        if (user == null) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "用户不存在");
        }
        if ("banned".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED);
        }
        if (!Integer.valueOf(user.getTokenVersion()).equals(payload.getTv())) {
            throw new BusinessException(ErrorCode.TOKEN_VERSION_MISMATCH);
        }

        // 4. 签发新双 Token
        String newAccessToken = jwtUtil.generateAccessToken(
                user.getId(), user.getRole(), user.getTokenVersion());
        String newRefreshToken = jwtUtil.generateRefreshToken(
                user.getId(), user.getTokenVersion());

        log.info("Token 刷新: userId={}", user.getId());

        return RefreshResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    /**
     * 获取当前用户信息（与 Node.js authService.me 一致）
     *
     * @param userId 用户 ID
     * @return 用户信息视图
     */
    public UserInfo me(Long userId) {
        User user = userRepo.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED, "用户不存在");
        }
        return UserInfo.from(user);
    }
}

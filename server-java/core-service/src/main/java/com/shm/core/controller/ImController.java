package com.shm.core.controller;

import com.shm.common.util.ResponseBuilder;
import com.shm.core.feign.ImConnectorFeign;
import com.shm.core.security.CurrentUser;
import com.shm.core.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * IM 公共 API 控制器 — 前端 IM SDK 初始化入口
 *
 * <p>与 Node.js controllers/im.js 行为一致。
 * Core Service 验证 JWT → 提取 userId → 通过 Feign 调 IM Connector 生成 UserSig。
 *
 * <p>路由说明：
 * <ul>
 *   <li>{@code GET /api/im/user-sig} — 获取当前用户的 IM 登录凭证</li>
 *   <li>{@code POST /api/im/ensure-account} — 确保目标用户的 IM 账号已导入</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/im")
public class ImController {

    private final ImConnectorFeign imConnectorFeign;
    private static final Logger log = LoggerFactory.getLogger(ImController.class);

    public ImController(ImConnectorFeign imConnectorFeign) {
        this.imConnectorFeign = imConnectorFeign;
    }

    /**
     * 获取当前用户的 IM 登录凭证（UserSig）
     *
     * <p>前端 IM SDK 初始化时调用，需携带有效的 JWT Access Token。
     *
     * <p><b>与 Node.js controllers/im.js getUserSig 行为一致：</b>
     * 签发 UserSig 的同时将当前用户的昵称和头像同步到 IM 资料，
     * 防止其他用户看到 IM 自动生成的占位昵称或过期头像。
     *
     * @param user 当前登录用户（由 JWT Filter 注入）
     * @return { code: 0, data: { userId, userSig, sdkAppId } }
     */
    @GetMapping("/user-sig")
    public Map<String, Object> getUserSig(@CurrentUser UserPrincipal user) {
        String userId = String.valueOf(user.getUserId());
        Map<String, Object> feignResp = imConnectorFeign.generateUserSig(userId);

        // 同步当前用户的昵称和头像到 IM（与 Node.js 行为一致）
        // 导入已有账号是幂等的，失败不阻塞 UserSig 签发
        try {
            imConnectorFeign.importAccount(userId, user.getNickname(), user.getAvatar());
        } catch (Exception e) {
            log.warn("IM 账号资料同步失败（不阻塞 UserSig）: userId={}, error={}", userId, e.getMessage());
        }

        // Feign 返回的是 ResponseBuilder 包装的 { code, message, data }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) feignResp.get("data");
        return ResponseBuilder.ok(data);
    }

    /**
     * 确保目标用户的 IM 账号已导入
     *
     * <p>腾讯云 IM 发消息前要求接收方 UserID 已存在。
     * 前端在打开聊天前调用此接口，确保对方账号已导入。
     *
     * @param body { userId, nick?, avatar? }
     * @return { code: 0, data: { imported: true } }
     */
    @PostMapping("/ensure-account")
    public Map<String, Object> ensureAccount(@RequestBody Map<String, Object> body) {
        String userId = String.valueOf(body.get("userId"));
        String nick = body.containsKey("nick") ? String.valueOf(body.get("nick")) : null;
        String avatar = body.containsKey("avatar") ? String.valueOf(body.get("avatar")) : null;

        Map<String, Object> feignResp = imConnectorFeign.importAccount(userId, nick, avatar);
        return ResponseBuilder.ok(feignResp.get("data"));
    }
}

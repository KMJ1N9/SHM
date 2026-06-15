package com.shm.im.controller;

import com.shm.common.exception.ErrorCode;
import com.shm.common.util.ResponseBuilder;
import com.shm.im.config.ImConfig;
import com.shm.im.service.TencentImService;
import com.shm.im.service.UserSigService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * IM 内部 API 控制器 — 供 Core Service / Admin Service 内部调用
 *
 * <p>这些端点不直接暴露给前端，由 Gateway 安全控制。
 * 路径前缀 /internal/im/* 在 Gateway 中配置为禁止外部访问。
 */
@RestController
@RequestMapping("/internal/im")
@Validated
public class InternalImController {

    private final UserSigService userSigService;
    private final TencentImService tencentImService;
    private final ImConfig imConfig;

    public InternalImController(UserSigService userSigService,
                                 TencentImService tencentImService,
                                 ImConfig imConfig) {
        this.userSigService = userSigService;
        this.tencentImService = tencentImService;
        this.imConfig = imConfig;
    }

    /**
     * 生成 UserSig — 供 Core Service 初始化 IM SDK 使用
     *
     * @param userId 用户 ID
     * @return { code: 0, data: { userId: "...", userSig: "...", sdkAppId: ... } }
     */
    @PostMapping("/usersig")
    public Map<String, Object> generateUserSig(@RequestParam @NotBlank(message = "userId 不能为空") String userId) {
        String userSig = userSigService.generateUserSig(userId);
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("userId", userId);
        data.put("userSig", userSig);
        data.put("sdkAppId", imConfig.getSdkAppId());
        return ResponseBuilder.ok(data);
    }

    /**
     * IM 账号导入 — 注册用户在 IM 中创建账号
     *
     * @param userId  用户 ID
     * @param nick    用户昵称（可选）
     * @param faceUrl 头像 URL（可选）
     * @return IM REST API 原始响应
     */
    @PostMapping("/import")
    public Map<String, Object> importAccount(
            @RequestParam @NotBlank(message = "userId 不能为空") String userId,
            @RequestParam(required = false) String nick,
            @RequestParam(required = false) String faceUrl) {
        Map<String, Object> result = tencentImService.importAccount(userId, nick, faceUrl);
        if (result.containsKey("ErrorCode") && !"0".equals(String.valueOf(result.get("ErrorCode")))) {
            return ResponseBuilder.error(ErrorCode.IM_API_FAILED.getCode(),
                    (String) result.getOrDefault("ErrorInfo", "IM import failed"));
        }
        return ResponseBuilder.ok(result);
    }

    /**
     * 发送系统消息（单聊）
     *
     * @param toUserId 接收方用户 ID
     * @param title    消息标题
     * @param content  消息内容
     * @param orderId  关联订单 ID（可选，放入 extra 字段）
     * @return IM REST API 原始响应
     */
    @PostMapping("/send")
    public Map<String, Object> sendSystemMessage(
            @RequestParam @NotBlank(message = "toUserId 不能为空") String toUserId,
            @RequestParam @NotBlank(message = "title 不能为空") String title,
            @RequestParam @NotBlank(message = "content 不能为空") String content,
            @RequestParam(required = false) Long orderId) {
        Map<String, Object> extra = null;
        if (orderId != null) {
            extra = new java.util.LinkedHashMap<>();
            extra.put("order_id", orderId);
        }
        Map<String, Object> result = tencentImService.sendSystemMessage(toUserId, title, content, extra);
        if (result.containsKey("ErrorCode") && !"0".equals(String.valueOf(result.get("ErrorCode")))) {
            return ResponseBuilder.error(ErrorCode.IM_API_FAILED.getCode(),
                    (String) result.getOrDefault("ErrorInfo", "IM send failed"));
        }
        return ResponseBuilder.ok(result);
    }

    /**
     * 批量发送系统消息（群发通知）
     *
     * @param toUserIds 接收方用户 ID 列表（逗号分隔）
     * @param title     消息标题
     * @param content   消息内容
     * @return IM REST API 原始响应
     */
    @PostMapping("/send-batch")
    public Map<String, Object> sendBatch(
            @RequestParam @NotEmpty(message = "toUserIds 不能为空") List<String> toUserIds,
            @RequestParam @NotBlank(message = "title 不能为空") String title,
            @RequestParam @NotBlank(message = "content 不能为空") String content) {
        Map<String, Object> result = tencentImService.sendBatchSystemMessage(toUserIds, title, content, null);
        if (result.containsKey("ErrorCode") && !"0".equals(String.valueOf(result.get("ErrorCode")))) {
            return ResponseBuilder.error(ErrorCode.IM_API_FAILED.getCode(),
                    (String) result.getOrDefault("ErrorInfo", "IM batch send failed"));
        }
        return ResponseBuilder.ok(result);
    }
}

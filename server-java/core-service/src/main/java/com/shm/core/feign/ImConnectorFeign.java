package com.shm.core.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * IM Connector Feign 客户端 — 声明式调用 im-connector 的内部 API
 *
 * <h3>端点映射</h3>
 * <ul>
 *   <li>{@code /internal/im/usersig} — 生成 UserSig（供前端 IM SDK 初始化）</li>
 *   <li>{@code /internal/im/import} — IM 账号导入（新用户注册后调用）</li>
 *   <li>{@code /internal/im/send} — 发送单聊系统消息（订单通知等）</li>
 * </ul>
 *
 * <p>通过 Nacos 服务发现 + LoadBalancer 实现负载均衡。
 * 本地开发（无 Nacos）时可通过 {@code spring.cloud.nacos.discovery.enabled=false} 降级为静默模式。
 *
 * @see com.shm.im.controller.InternalImController
 */
@FeignClient(name = "im-connector", fallback = ImConnectorFeignFallback.class)
public interface ImConnectorFeign {

    /**
     * 生成 UserSig（供 Core Service 初始化 IM SDK）
     *
     * @param userId 用户 ID
     * @return { code, message, data: { userId, userSig, sdkAppId } }
     */
    @PostMapping("/internal/im/usersig")
    Map<String, Object> generateUserSig(@RequestParam("userId") String userId);

    /**
     * IM 账号导入 — 注册用户后在 IM 中创建对应账号
     *
     * @param userId  用户 ID
     * @param nick    用户昵称（可选）
     * @param faceUrl 头像 URL（可选）
     * @return { code, message, data: { ... } }
     */
    @PostMapping("/internal/im/import")
    Map<String, Object> importAccount(
            @RequestParam("userId") String userId,
            @RequestParam(value = "nick", required = false) String nick,
            @RequestParam(value = "faceUrl", required = false) String faceUrl);

    /**
     * 发送系统消息（单聊 — 订单状态变更通知）
     *
     * @param toUserId 接收方用户 ID
     * @param title    消息标题
     * @param content  消息内容
     * @param orderId  关联订单 ID（可选，放入 extra 字段）
     * @return { code, message, data: { ... } }
     */
    @PostMapping("/internal/im/send")
    Map<String, Object> sendSystemMessage(
            @RequestParam("toUserId") String toUserId,
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam(value = "orderId", required = false) Long orderId);

    /**
     * 批量发送系统消息（群发通知 — 管理员公告等）
     *
     * @param toUserIds 接收方用户 ID 列表
     * @param title     消息标题
     * @param content   消息内容
     * @return { code, message, data: { ... } }
     */
    @PostMapping("/internal/im/send-batch")
    Map<String, Object> sendBatchSystemMessage(
            @RequestParam("toUserIds") List<String> toUserIds,
            @RequestParam("title") String title,
            @RequestParam("content") String content);
}

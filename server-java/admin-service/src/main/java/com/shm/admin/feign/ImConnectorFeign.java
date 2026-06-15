package com.shm.admin.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * IM Connector Feign 客户端（admin-service 侧）— 声明式调用 im-connector
 *
 * <p>admin-service 需要 IM 功能：
 * <ul>
 *   <li>举报处理完成后通知用户</li>
 *   <li>封禁/解封用户时推送系统通知</li>
 *   <li>批量发送管理员公告</li>
 * </ul>
 *
 * @see com.shm.im.controller.InternalImController
 */
@FeignClient(name = "im-connector", fallback = ImConnectorFeignFallback.class)
public interface ImConnectorFeign {

    /**
     * 发送系统消息（单聊 — 举报处理结果通知等）
     */
    @PostMapping("/internal/im/send")
    Map<String, Object> sendSystemMessage(
            @RequestParam("toUserId") String toUserId,
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam(value = "orderId", required = false) Long orderId);

    /**
     * 批量发送系统消息（群发 — 管理员公告 / 违规警告等）
     */
    @PostMapping("/internal/im/send-batch")
    Map<String, Object> sendBatchSystemMessage(
            @RequestParam("toUserIds") List<String> toUserIds,
            @RequestParam("title") String title,
            @RequestParam("content") String content);
}

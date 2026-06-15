package com.shm.core.feign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IM Connector Feign 降级实现 — 当 im-connector 不可用时返回降级响应
 *
 * <h3>降级策略</h3>
 * <ul>
 *   <li>不抛异常 — 避免触发 Feign 重试放大故障</li>
 *   <li>统一返回 { code: 6004 } — 与 Node.js ErrorCode.IM_API_FAILED 一致</li>
 *   <li>记录 WARN 日志 — 方便运维排查</li>
 * </ul>
 *
 * <p>调用方（OrderService / ReportService 等）已有 try-catch 包裹，
 * 此 Fallback 提供第二层防护 — 即使调用方忘记 try-catch 也不会崩溃。
 *
 * @see ImConnectorFeign
 */
@Component
public class ImConnectorFeignFallback implements ImConnectorFeign {

    private static final Logger log = LoggerFactory.getLogger(ImConnectorFeignFallback.class);

    private static final int IM_API_FAILED = 6004;

    @Override
    public Map<String, Object> generateUserSig(String userId) {
        log.warn("[Sentinel Fallback] generateUserSig 降级: im-connector 不可用, userId={}", userId);
        return degradeResponse("generateUserSig");
    }

    @Override
    public Map<String, Object> importAccount(String userId, String nick, String faceUrl) {
        log.warn("[Sentinel Fallback] importAccount 降级: im-connector 不可用, userId={}", userId);
        return degradeResponse("importAccount");
    }

    @Override
    public Map<String, Object> sendSystemMessage(String toUserId, String title, String content, Long orderId) {
        log.warn("[Sentinel Fallback] sendSystemMessage 降级: im-connector 不可用, toUserId={}, orderId={}", toUserId, orderId);
        return degradeResponse("sendSystemMessage");
    }

    @Override
    public Map<String, Object> sendBatchSystemMessage(List<String> toUserIds, String title, String content) {
        log.warn("[Sentinel Fallback] sendBatchSystemMessage 降级: im-connector 不可用, userCount={}", toUserIds != null ? toUserIds.size() : 0);
        return degradeResponse("sendBatchSystemMessage");
    }

    private static Map<String, Object> degradeResponse(String operation) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", IM_API_FAILED);
        result.put("message", "IM 消息服务暂不可用（" + operation + "）");
        result.put("data", Collections.emptyMap());
        return result;
    }
}

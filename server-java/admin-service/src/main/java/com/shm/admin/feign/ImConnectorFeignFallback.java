package com.shm.admin.feign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IM Connector Feign 降级实现（admin-service 侧）— 当 im-connector 不可用时返回降级响应
 *
 * @see ImConnectorFeign
 */
@Component
public class ImConnectorFeignFallback implements ImConnectorFeign {

    private static final Logger log = LoggerFactory.getLogger(ImConnectorFeignFallback.class);

    private static final int IM_API_FAILED = 6004;

    @Override
    public Map<String, Object> sendSystemMessage(String toUserId, String title, String content, Long orderId) {
        log.warn("[Sentinel Fallback] sendSystemMessage 降级: im-connector 不可用, toUserId={}, orderId={}", toUserId, orderId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", IM_API_FAILED);
        result.put("message", "IM 消息服务暂不可用（sendSystemMessage）");
        result.put("data", Collections.emptyMap());
        return result;
    }

    @Override
    public Map<String, Object> sendBatchSystemMessage(List<String> toUserIds, String title, String content) {
        log.warn("[Sentinel Fallback] sendBatchSystemMessage 降级: im-connector 不可用, userCount={}",
                toUserIds != null ? toUserIds.size() : 0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", IM_API_FAILED);
        result.put("message", "IM 消息服务暂不可用（sendBatchSystemMessage）");
        result.put("data", Collections.emptyMap());
        return result;
    }
}

package com.shm.admin.feign;

import com.shm.common.model.dto.internal.PenaltyRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Core Service Feign 客户端（Phase 13 — 跨服务事务）
 *
 * <p>admin-service 通过此接口调用 core-service 内部 API，
 * 实现跨服务分布式事务（Seata AT 模式）。
 *
 * <h3>端点映射</h3>
 * <ul>
 *   <li>{@code POST /internal/users/{userId}/penalty} — 执行用户处罚（扣信誉分/封禁）</li>
 * </ul>
 */
@FeignClient(name = "core-service", fallback = CoreUserFeignFallback.class)
public interface CoreUserFeign {

    /**
     * 执行用户处罚
     *
     * @param userId  目标用户 ID
     * @param request 处罚请求（penalty + deductCredit + reason + ticketId）
     * @return { code, message, data: { userId, previousScore, currentScore, status } }
     */
    @PostMapping("/internal/users/{userId}/penalty")
    Map<String, Object> applyPenalty(@PathVariable("userId") Long userId, @RequestBody PenaltyRequest request);
}

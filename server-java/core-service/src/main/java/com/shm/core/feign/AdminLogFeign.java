package com.shm.core.feign;

import com.shm.common.model.dto.admin.AdminLogRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Admin Service Feign 客户端（Phase 13 — 跨服务事务）
 *
 * <p>core-service 通过此接口调用 admin-service 内部 API，
 * 写入管理员操作审计日志。
 *
 * <h3>端点映射</h3>
 * <ul>
 *   <li>{@code POST /internal/admin-logs} — 写入审计日志</li>
 * </ul>
 */
@FeignClient(name = "admin-service", fallback = AdminLogFeignFallback.class)
public interface AdminLogFeign {

    /**
     * 写入管理员操作审计日志
     *
     * @param request adminId + action + targetType + targetId + reason
     * @return { code, message, data: { id } }
     */
    @PostMapping("/internal/admin-logs")
    Map<String, Object> createLog(@RequestBody AdminLogRequest request);
}

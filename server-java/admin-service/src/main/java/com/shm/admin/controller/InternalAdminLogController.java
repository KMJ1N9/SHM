package com.shm.admin.controller;

import com.shm.admin.service.LogService;
import com.shm.common.model.dto.admin.AdminLogRequest;
import com.shm.common.util.ResponseBuilder;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 内部审计日志 API（Phase 13 — 跨服务事务，供 core-service Feign 调用）
 *
 * <p>与 core-service 的 AdminLogFeign 接口匹配。
 */
@RestController
@RequestMapping("/internal")
public class InternalAdminLogController {

    private final LogService logService;

    public InternalAdminLogController(LogService logService) {
        this.logService = logService;
    }

    /**
     * POST /internal/admin-logs — 写入管理员操作审计日志
     *
     * <p>由 core-service 的 OrderService.confirm() 通过 AdminLogFeign 调用。
     * 在 Seata AT 模式中作为事务分支（RM）参与全局事务。
     */
    @PostMapping("/admin-logs")
    public Map<String, Object> createLog(@Valid @RequestBody AdminLogRequest request) {
        long id = logService.createLog(request);
        return ResponseBuilder.ok(Map.of("id", id));
    }
}

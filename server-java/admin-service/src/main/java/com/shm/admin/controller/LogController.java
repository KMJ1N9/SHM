package com.shm.admin.controller;

import com.shm.admin.service.LogService;
import com.shm.common.model.dto.admin.LogQueryRequest;
import com.shm.common.util.ResponseBuilder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 审计日志控制器（与 Node.js adminController 审计日志部分行为完全一致）
 */
@RestController
@RequestMapping("/api/admin/logs")
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    /**
     * GET /api/admin/logs — 操作审计日志列表
     *
     * <p>查询参数自动绑定到 LogQueryRequest：action, target_type, start, end, page, pageSize。
     */
    @GetMapping
    public Map<String, Object> list(LogQueryRequest query) {
        query.setPageSize(Math.min(query.getPageSize(), 50));
        return ResponseBuilder.ok(logService.listLogs(query));
    }
}

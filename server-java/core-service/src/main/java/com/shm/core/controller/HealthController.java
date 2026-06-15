package com.shm.core.controller;

import com.shm.common.util.ResponseBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 健康检查控制器（与 Node.js GET /api/health 一致）
 */
@RestController
public class HealthController {

    /**
     * GET /api/health — 健康检查
     */
    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return ResponseBuilder.ok(Map.of("status", "healthy", "timestamp", System.currentTimeMillis()));
    }
}

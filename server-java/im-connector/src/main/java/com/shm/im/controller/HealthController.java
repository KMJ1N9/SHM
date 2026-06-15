package com.shm.im.controller;

import com.shm.common.util.ResponseBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 健康检查控制器
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return ResponseBuilder.ok(Map.of("status", "healthy", "timestamp", System.currentTimeMillis()));
    }
}

package com.shm.core.controller;

import com.shm.common.util.ResponseBuilder;
import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.NamedContributor;
import org.springframework.boot.actuate.health.Status;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 健康检查控制器 — 聚合检查 MySQL / Redis / 磁盘空间等
 *
 * <p>直接遍历 {@link HealthContributorRegistry} 获取所有注册的健康指示器状态。
 * 与 Node.js 的 GET /api/health 响应格式兼容。
 */
@RestController
public class HealthController {

    private final HealthContributorRegistry registry;

    public HealthController(HealthContributorRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> components = new TreeMap<>();
        String overallStatus = "UP";

        for (NamedContributor<HealthContributor> entry : registry) {
            String name = entry.getName();
            String status = resolveStatus(entry.getContributor());
            if ("DOWN".equals(status)) {
                overallStatus = "DOWN";
            }
            components.put(name, status);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("status", overallStatus);
        data.put("timestamp", System.currentTimeMillis());
        data.put("components", components);

        return ResponseBuilder.ok(data);
    }

    /** 递归解析 HealthContributor 的状态（处理嵌套的 CompositeHealthContributor） */
    private String resolveStatus(HealthContributor contributor) {
        if (contributor instanceof HealthIndicator indicator) {
            try {
                Status status = indicator.health().getStatus();
                return status != null ? status.getCode() : "UNKNOWN";
            } catch (Exception e) {
                return "DOWN";
            }
        }
        if (contributor instanceof CompositeHealthContributor composite) {
            // 嵌套组（如 readinessState / livenessState 等）
            for (NamedContributor<HealthContributor> nested : composite) {
                String nestedStatus = resolveStatus(nested.getContributor());
                if ("DOWN".equals(nestedStatus)) return "DOWN";
            }
            return "UP";
        }
        return "UNKNOWN";
    }
}

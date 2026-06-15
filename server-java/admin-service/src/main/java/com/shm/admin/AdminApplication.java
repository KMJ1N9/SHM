package com.shm.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 管理服务 — 举报 / 工单 / 用户管理 / 商品管理 / 统计分析 / 敏感词 / 审计日志
 *
 * <p>scanBasePackages = "com.shm" 确保加载 common 模块的 JacksonConfig（snake_case）
 * 和 GlobalExceptionHandler（统一异常格式）。
 * <p>@EnableFeignClients 启用 OpenFeign 声明式服务间调用（Phase 8）。
 */
@SpringBootApplication(scanBasePackages = "com.shm")
@EnableFeignClients
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}

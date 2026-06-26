package com.shm.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 核心服务 — 认证 / 用户 / 商品 / 订单 / 评价 / 通知 / 信誉分
 *
 * <p>scanBasePackages = "com.shm" 确保加载 common 模块的 JacksonConfig（snake_case）
 * 和 GlobalExceptionHandler（统一异常格式）。
 * <p>@ConfigurationPropertiesScan 自动注册 @ConfigurationProperties 类（AppConfig / CreditProperties）。
 * <p>@EnableFeignClients 启用 OpenFeign 声明式服务间调用（Phase 8）。
 * <p>@EnableScheduling 启用定时任务（Phase 14: 消息重试 + 订单超时）。
 */
@SpringBootApplication(scanBasePackages = "com.shm")
@ConfigurationPropertiesScan
@EnableFeignClients
@EnableScheduling
public class CoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreApplication.class, args);
    }
}

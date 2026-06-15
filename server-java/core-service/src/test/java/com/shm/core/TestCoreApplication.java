package com.shm.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Profile;

/**
 * 测试专用 Spring Boot 应用类（仅用于 test scope）。
 *
 * <p>与生产 {@link CoreApplication} 的区别：
 * <ul>
 *   <li><b>不包含 {@code @EnableFeignClients}</b> — 避免触发 Feign 客户端注册和
 *       级联的 Nacos 服务发现 / LoadBalancer 依赖，这些在单服务集成测试中无法启动。</li>
 *   <li><b>排除 CoreApplication 自身的组件扫描</b> — 防止其上的
 *       {@code @EnableFeignClients} 被加载。</li>
 * </ul>
 *
 * <p>外部依赖（ImConnectorFeign / Redis）通过测试中的 {@code @MockBean} 提供。
 *
 * @author Claude Code
 * @since 2026-06-14
 */
@SpringBootApplication(scanBasePackages = "com.shm")
@ConfigurationPropertiesScan
@ComponentScan(
    basePackages = "com.shm",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = CoreApplication.class
    )
)
@Profile("test")
public class TestCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestCoreApplication.class, args);
    }
}

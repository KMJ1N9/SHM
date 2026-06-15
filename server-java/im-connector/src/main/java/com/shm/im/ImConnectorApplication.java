package com.shm.im;

import com.shm.common.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;

/**
 * IM 连接器 — 腾讯云 IM UserSig / COS STS 凭证 / 系统消息推送
 *
 * <h3>组件扫描范围</h3>
 * <p>{@code scanBasePackages = "com.shm.im"} — 仅扫描本模块，避免加载 common
 * 的 {@code JacksonConfig}（本服务不需要 snake_case 序列化）、core/admin 的全部组件。
 *
 * <h3>显式导入的 common Bean</h3>
 * <ul>
 *   <li>{@link GlobalExceptionHandler} — 参数校验异常统一处理（{@code @Validated} → 400 响应）</li>
 * </ul>
 *
 * <p>{@code ObjectMapper} 使用 Spring Boot 自动配置的默认实例（JacksonAutoConfiguration），
 * 本服务只做 Map 内部 JSON 序列化，无需蛇形命名策略。
 *
 * <p>{@code DotenvEnvironmentPostProcessor} 通过 SPI（{@code spring.factories}）加载，
 * 不受组件扫描范围影响。
 */
@SpringBootApplication(scanBasePackages = "com.shm.im")
@ConfigurationPropertiesScan
@Import({GlobalExceptionHandler.class})
public class ImConnectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImConnectorApplication.class, args);
    }
}

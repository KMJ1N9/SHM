package com.shm.admin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Seata 分布式事务配置（Phase 13 — admin-service 侧）
 *
 * <p>与 core-service SeataConfig 对称配置，确保 admin-service 可作为
 * Seata 事务分支参与者（RM）。
 */
@Configuration
@ConditionalOnProperty(name = "seata.enabled", havingValue = "true", matchIfMissing = false)
public class SeataConfig {

    private static final Logger log = LoggerFactory.getLogger(SeataConfig.class);

    public SeataConfig() {
        log.info("Seata 分布式事务配置已加载（AT 模式 — admin-service）");
    }
}

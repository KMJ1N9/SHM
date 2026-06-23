package com.shm.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Seata 分布式事务配置（Phase 13）
 *
 * <h3>架构</h3>
 * <p>Seata AT 模式：一阶段自动提交 + undo_log 快照 → 二阶段 TC 协调提交/回滚。
 * 与 MyBatis 原生 SQL 兼容，无需修改 Mapper。
 *
 * <h3>配置来源</h3>
 * <ul>
 *   <li>application.yml — seata.tx-service-group / registry / config</li>
 *   <li>seata-spring-boot-starter 自动配置 DataSource 代理</li>
 * </ul>
 *
 * <h3>开发环境</h3>
 * <p>{@code seata.enabled=false} 时本配置不生效，所有 @GlobalTransactional
 * 退化为普通 @Transactional 行为。
 */
@Configuration
@ConditionalOnProperty(name = "seata.enabled", havingValue = "true", matchIfMissing = false)
public class SeataConfig {

    private static final Logger log = LoggerFactory.getLogger(SeataConfig.class);

    public SeataConfig() {
        log.info("Seata 分布式事务配置已加载（AT 模式）");
    }
}

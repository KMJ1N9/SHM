package com.shm.core.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway 迁移策略 — 在 migrate 前自动 repair 清理上一次的失败记录。
 *
 * <p><b>为什么需要这个：</b>
 * 若 flyway_schema_history 丢失（数据库被重建等），Flyway 会 baseline 到 V1
 * 后重跑 V002。如果 V002 的 DDL 已经生效（索引已存在），ADD INDEX 会报
 * "Duplicate key" 并记录为 success=0 的 failed migration。之后每次启动
 * Flyway 会 validate 并拒绝启动。
 *
 * <p>本策略在每次 migrate 前调用 {@link Flyway#repair()}，该方法是幂等的：
 * 无失败记录时是空操作。repair 会清理 failed migration 条目 + 重对齐
 * checksum，后续 migrate 即可重试（配合幂等 SQL 迁移脚本确保成功）。
 *
 * <p>生产线若无 schema history 丢失风险，可移除此类。
 */
@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return (Flyway flyway) -> {
            log.info("Flyway repair (清理可能的失败迁移记录)...");
            flyway.repair();
            log.info("Flyway migrate (应用待执行迁移)...");
            flyway.migrate();
        };
    }
}

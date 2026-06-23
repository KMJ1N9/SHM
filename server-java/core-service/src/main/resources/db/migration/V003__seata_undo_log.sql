-- V003__seata_undo_log.sql
-- Seata AT 模式回滚快照表（Phase 13 分布式事务）
--
-- Seata AT 模式在事务提交前自动写入前镜像（before-image），
-- 回滚时根据 undo_log 记录反向执行 SQL 恢复数据。
--
-- 兼容 MySQL 8.0+，使用 CREATE TABLE IF NOT EXISTS 确保 Flyway 幂等。
--
-- 注意：core-service 和 admin-service 共享同一 MySQL 数据库，
-- undo_log 表只需在 core-service 创建一次。
-- admin-service 的 Flyway 迁移检测到该表已存在时自动跳过（IF NOT EXISTS）。

CREATE TABLE IF NOT EXISTS `undo_log` (
    `id`            BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `branch_id`     BIGINT(20)   NOT NULL COMMENT '事务分支 ID（Seata 自动分配）',
    `xid`           VARCHAR(128) NOT NULL COMMENT '全局事务 ID（XID）',
    `context`       VARCHAR(128) NOT NULL DEFAULT '' COMMENT '上下文序列化数据（Seata 内部）',
    `rollback_info` LONGBLOB     NOT NULL COMMENT '回滚信息（前镜像 + 后镜像，由 Seata 序列化）',
    `log_status`    INT(11)      NOT NULL COMMENT '日志状态（0=正常，1=全局完成）',
    `log_created`   DATETIME(3)  NOT NULL COMMENT '日志创建时间',
    `log_modified`  DATETIME(3)  NOT NULL COMMENT '日志修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Seata AT 模式分布式事务回滚日志表';

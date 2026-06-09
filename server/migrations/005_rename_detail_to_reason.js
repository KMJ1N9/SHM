/**
 * 迁移 005：admin_logs / admin_logs_archive 字段重命名
 *
 * 原规划（技术架构文档 §4.6.9）：
 *   admin_logs.detail → admin_logs.reason
 *   admin_logs_archive.detail → admin_logs_archive.reason
 *   目的：与 API 字段统一，语义更明确
 *
 * 实际实现：
 *   001_create_tables.js DDL 中直接使用 `reason` 作为列名（非 `detail`），
 *   从一开始就与 API 规格对齐。此迁移无需执行 ALTER TABLE。
 *
 * 本轮次（第 2 轮）按计划仍需记录此迁移版本号，确保各环境 migrations 表一致。
 */

async function up(db) {
  // 字段在 001_create_tables.js 中已命名为 reason，此处无操作
}

async function down(db) {
  // 无需回滚（字段从未命名为 detail）
}

module.exports = { up, down };

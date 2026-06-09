/**
 * 迁移 004：IM 系统消息失败重试表
 *
 * 原规划（技术架构文档 §4.6.9）：
 *   failed_system_messages — IM REST API 推送失败暂存与自动重试
 *
 * 实际实现：
 *   已在 002_failed_system_messages.js 中创建（含完整 DDL + 2 个索引）。
 *   因开发阶段发现 IM 系统消息推送需要失败重试机制，提前到迁移 002，
 *   而非原计划第 8 轮才创建。
 *
 * 本轮次（第 8 轮）按计划仍需记录此迁移版本号，确保各环境 migrations 表一致。
 */

async function up(db) {
  // 失败消息重试表已在 002_failed_system_messages.js up() 中创建，此处无操作
}

async function down(db) {
  // 失败消息重试表由 002_failed_system_messages.js down() 删除，此处无操作
}

module.exports = { up, down };

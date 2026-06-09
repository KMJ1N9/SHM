/**
 * 迁移 003：归档表
 *
 * 原规划（技术架构文档 §4.6.9）：
 *   admin_logs_archive、reviews_archive — 长期数据归档
 *
 * 实际实现：
 *   已在 001_create_tables.js 中一并创建（DDL 行 184-211）。
 *   JS 迁移体系将 14 张业务表原子化创建，归档表作为业务表的一部分在初始迁移中完成，
 *   无需单独迁移文件。
 *
 * 本轮次（第 2 轮）按计划仍需记录此迁移版本号，确保各环境 migrations 表一致。
 */

async function up(db) {
  // 归档表已在 001_create_tables.js up() 中创建，此处无操作
}

async function down(db) {
  // 归档表由 001_create_tables.js down() 统一删除，此处无操作
}

module.exports = { up, down };

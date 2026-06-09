---
name: migration-system
description: 项目使用编程式 JS 迁移（非 SQL），legacy .sql 文件应忽略
metadata:
  type: reference
---

# 迁移系统：JS 编程式迁移

## 关键事实

项目使用 **JavaScript 编程式迁移**，而非 SQL 文件迁移：

- **迁移文件：** `server/migrations/*.js`，导出 `up(db)` / `down(db)` 函数
- **运行方式：** `npm run db:migrate` → `node src/models/migrate.js migrate`
- **运行器：** `server/src/models/migrate.js` — 维护 `migrations` 追踪表
- **SQL 方式：** 使用 `db.query()` 参数化执行

## ⚠️ 遗留文件

`server/migrations/001_initial_schema.sql` 是早期 SQL 文件（文档审阅前遗留），**不是当前迁移系统的一部分**。

## 当前迁移（5 个，全部就位）

| 文件 | 内容 | 状态 |
|------|------|:--:|
| `001_create_tables.js` | 14 张业务表 DDL + FULLTEXT 索引（含 admin_logs_archive + reviews_archive） | ✅ |
| `002_failed_system_messages.js` | IM 系统消息失败重试表 DDL + 2 索引 | ✅ |
| `003_archive_tables.js` | 归档表——实际已在 001 中创建，此处记录版本号（no-op） | ✅ |
| `004_failed_system_messages.js` | 失败消息表——实际已在 002 中创建，此处记录版本号（no-op） | ✅ |
| `005_rename_detail_to_reason.js` | 字段重命名——001 DDL 已用 `reason` 命名，此处记录版本号（no-op） | ✅ |

> **说明：** 003-005 的实质工作已在 001/002 中吸收完成。创建这三个 no-op 迁移是为了维持迁移编号连续性，确保各环境 `migrations` 追踪表一致（技术架构文档 §4.6.9 规划了这些版本号）。

## 常用命令

```bash
npm run db:migrate         # 执行待办的迁移
npm run db:migrate:status  # 查看迁移状态
npm run db:migrate:rollback # 回滚最近一次迁移
npm run db:migrate:reset   # 重置所有迁移
npm run db:seed            # 导入种子数据
```

**Why:** 新开发者可能看到 `.sql` 文件误以为是 SQL 迁移系统。编程式迁移允许复杂逻辑（数据清洗/条件建表）。

**How to apply:** 创建新迁移时复制已有 `.js` 结构，不创建 `.sql` 文件。

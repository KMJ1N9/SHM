# ADR-0001：选择 MySQL + 原生 SQL（无 ORM）

- **日期：** 2026-06-04
- **状态：** 已采纳
- **决策者：** 单人开发

## 背景

校园二手交易小程序 MVP v1.0，数据模型为关系型（用户、商品、订单、评价等 14 张表），需要选择数据库和访问方式。项目为单人开发、无 DBA、上线后用户量预计 < 1000。

## 决策

**MySQL 8.0 InnoDB + mysql2（参数化查询），不使用 ORM。**

- 使用连接池管理 MySQL 连接
- 所有查询手写 SQL，通过 `?` 占位符参数化
- 数据访问封装为 repository 层，上层不直接接触 SQL
- 使用 `migrations` 表管理 schema 版本（UP/DOWN 迁移系统）

## 考虑的替代方案

| 方案 | 优点 | 缺点 | 为何未选 |
|------|------|------|---------|
| Sequelize / TypeORM | 自动建表、关联查询方便、TS 类型推导 | 学习成本、黑盒 SQL 性能不可控、单人项目过度抽象 | 违反 "Simplicity First" — 不为单次使用创建抽象 |
| Prisma | 类型安全、migration 工具成熟 | 需要额外 schema 语法学习、生成的 SQL 不够透明 | 引入新语法体系，增加学习曲线 |
| MongoDB | Schema-less 灵活 | 本项目数据模型天然关系型（用户-商品-订单强关联）、事务需求 | 不适合关系型业务场景 |

## 影响范围

- `server/src/models/` — mysql2 连接池初始化
- `server/src/repository/` — 6 个数据访问层（user/product/order/review/report/admin）
- `server/src/config/` — 数据库连接配置（环境变量）
- 所有 SQL 操作经过 `mysql2.execute()` 参数化，防注入

## 后果

### 正面
- SQL 完全透明可控，性能优化直接
- 零 ORM 学习成本，减少依赖
- repository 层隔离，未来若需要 ORM 可逐模块替换
- 参数化查询天然防 SQL 注入

### 负面
- 手写 SQL 容易出错（如字段名拼写、JOIN 遗漏）
- 没有 TS 类型推导，需手动定义返回类型
- migration 系统需自维护
- 表结构变更时需手动同步 DDL

## 相关

- 关联文档: [技术架构文档 §三-四](../技术架构文档.md)
- 关联规范: [database-rules](../../rules/database-rules.md)

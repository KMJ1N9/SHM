# ADR-0004：Express 5 层分层架构

- **日期：** 2026-06-04
- **状态：** 已采纳
- **决策者：** 单人开发

## 背景

后端需要处理 43 个 API 端点，涉及认证、商品、订单、评价、举报、IM、管理后台等模块。需要选择项目内部分层方式，确保单人开发时代码可维护、可测试。

## 决策

**Express 5 层架构：routes → controllers → services → repository → models。**

每层职责严格分离：

| 层 | 职责 | 禁止 |
|-----|------|------|
| **routes** | 路由注册、中间件绑定 | 禁止写业务逻辑 |
| **controllers** | 请求解析、参数校验、响应格式化 | 禁止直接操作数据库、禁止写业务逻辑 |
| **services** | 业务逻辑、事务编排、跨模块调用 | 禁止直接写 SQL |
| **repository** | 数据访问、SQL 执行、查询优化 | 禁止写业务逻辑 |
| **models** | 连接池管理、表定义 | 禁止写业务逻辑 |

## 考虑的替代方案

| 方案 | 优点 | 缺点 | 为何未选 |
|------|------|------|---------|
| MVC（routes + controllers + models） | 简单直接 | service 层缺失导致 controller 臃肿，业务逻辑和 SQL 混在一起 | 超过 10 个 API 后维护困难 |
| NestJS | 依赖注入、模块化、开箱即用 | 学习曲线陡峭、装饰器语法不熟悉、大量 boilerplate | 单人项目过度抽象 |
| 函数式路由（无分层） | 最简 | 无结构约束，后期必然混乱 | 不符合文件规范（单文件 >500 行必拆分） |

## 影响范围

- `server/src/routes/` — 10 个路由模块
- `server/src/controllers/` — 8 个控制器
- `server/src/services/` — 12 个服务
- `server/src/repository/` — 6 个数据访问层
- `server/src/models/` — mysql2 连接池

## 后果

### 正面
- 每层单一职责，符合函数规范（单函数 ≤80 行）
- 可逐层测试：repository 测 SQL、service 测业务逻辑、controller 测 HTTP
- 新人可按层定位代码，不必理解全貌
- repository 层隔离 SQL，未来换数据库仅需改这一层

### 负面
- 简单 CRUD 也要过 4 层，代码量多于 MVC
- 层与层之间需要 interface 定义，前期工作量大
- 5 层目录在项目初期显"重"，有过度设计嫌疑
- 层间调用链长（route → controller → service → repository → SQL），调试需跟踪多层

### 为什么还选它
技术架构文档明确了 43 个 API 端点和 6 个数据模块。这个量级的项目，如果不分层，controller 会迅速膨胀到 500+ 行。5 层架构是"现在稍微麻烦、后期避免翻车"的权衡。

## 相关

- 关联文档: [技术架构文档 §三](../技术架构文档.md)
- 关联规范: [file-rules](../../rules/file-rules.md)（单文件 ≤500 行）
- 关联规范: [function-rules](../../rules/function-rules.md)（单函数 ≤80 行）

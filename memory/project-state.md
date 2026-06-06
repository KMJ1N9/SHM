---
name: project-state
description: 项目当前状态 — 前后端完成度、关键指标、阻塞项
metadata:
  type: project
---

# 项目当前状态

**评估日期：** 2026-06-05

## 完成度

| 层 | 文件数 | 完成度 | 详情 |
|------|:--:|:--:|------|
| **后端 (server/)** | 57+ `.js` | **~80%** | 5 层架构全部有实质代码实现，非占位 |
| **前端 (miniprogram/)** | 38 `.vue/.js` | **~5%** | 全部是占位 stub（"XXX — 待实现"），只有 tokens.scss、common.scss、pages.json、App.vue、main.js 等基础设施可用 |

## 后端已完成的模块

- **12 个 Service** — auth/user/product/order/review/report/notification/credit/analytics/admin/im/provider.js + im/tencent.js
- **6 个 Repository** — user(11方法)/product(7)/order(7)/review(4)/report(8)/credit(3)
- **7 个 Middleware** — auth/admin/cs/validate/rate-limiter/error-handler/access-log
- **9 个 Utils** — cos/wechat/im-api/cache/sensitive-filter/perf/errors/app-error/logger
- **数据库连接池** — mysql2/promise，含慢查询监控和事务封装

## 后端缺失

- 迁移 SQL（建表语句）
- 种子数据
- .env 配置模板（`.env.example` 已有但需检查）
- 测试用例（vitest + supertest）
- 定时任务（node-cron）
- **已知 Bug**：`middleware/auth.js:75` — `payload.version` 应为 `payload.tv`（token_version 字段名不匹配），需在第 2 轮修复

## 前端状态

- 38 个文件全部是占位 stub
- 已有可用基础设施：tokens.scss（40+ 设计令牌）、common.scss、pages.json（21 页面 + TabBar）、App.vue、main.js、manifest.json（AppID 已配置）
- 前端编码是主要瓶颈

## 关键指标

- **API 端点**：43 个
- **数据库表**：14 个
- **前端页面**：15 个 + 错误页
- **前端组件**：6 个通用组件
- **Pinia Store**：3 个（user/chat/app）
- **迭代计划**：13 轮，估 24 人天，MVP 7 轮/14 天

**Why:** 首次全量代码扫描发现，后端进度远超预期（CLAUDE.md 说"准备开始编码"，实际上后端已近完成），前端是 0%。这决定了编码迭代的重点是前端。

**How to apply:** 任何编码任务开始前，先读此文件了解当前状态。优先安排前端工作，后端主要是补漏和联调。

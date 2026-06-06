---
name: known-bugs
description: 已知 Bug 列表 — 发现时间、位置、根因、修复方案、修复状态
metadata:
  type: project
---

# 已知 Bug

## BUG-001: token_version 字段名不匹配 ✅ 已修复

- **发现时间**: 2026-06-05（代码扫描）
- **修复时间**: 2026-06-06
- **位置**: [server/src/middleware/auth.js:75](server/src/middleware/auth.js#L75)
- **严重程度**: P0（导致所有用户鉴权失败——`payload.version` 永远为 `undefined`，永远不等于 `user.token_version`）
- **根因**: JWT 签发时 payload 字段名为 `tv`（`{ sub, role, tv }`），但中间件验证时读取 `payload.version`，字段名不匹配
- **修复**: 将 `payload.version` 改为 `payload.tv`（一行改动）

## BUG-002: health check 空 catch ✅ 已修复

- **发现时间**: 2026-06-05
- **修复时间**: 2026-06-06
- **位置**: [server/src/app.js:82](server/src/app.js#L82)
- **严重程度**: P2（违反 error-handling-rules 禁止空 catch，数据库故障时无日志排查）
- **修复**: 添加 `logger.error('健康检查失败', { error: err.message, stack: err.stack })`

**Why:** 代码扫描中发现的实际问题。BUG-001 是致命安全漏洞——token_version 校验完全失效，所有用户登录后立即被判定为 token 不匹配。

**How to apply:** 已修复。后续不要再引入类似字段名不一致问题。

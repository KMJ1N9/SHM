---
name: iteration2-review
description: 第 2 轮「注册/登录」审阅报告 — 4 问题（P0×1/P1×1/P2×2），2 修复，全链路验证通过
metadata:
  type: project
  reviewDate: 2026-06-07
  iteration: 2
  totalFiles: 8
  fixed: 2
  remaining: 2
---

# 第 2 轮「注册/登录」审阅报告

## 审阅范围

8 个文件的前后端全链路交叉审查：后端 2 改 + 前端 6 新/改写。

## 发现问题

| # | 级别 | 问题 | 文件 | 状态 |
|---|:--:|------|------|:--:|
| 1 | **P0** | `POST /auth/refresh` 未加入鉴权白名单，Token 刷新完全不可用 | [server/src/middleware/auth.js](server/src/middleware/auth.js) | ✅ 已修复 |
| 2 | **P1** | 拦截器自动刷新后 Pinia Store token 未同步 | [miniprogram/src/store/user.js](miniprogram/src/store/user.js) | ✅ 已修复 |
| 3 | P2 | `openPrivacy()` TODO 占位（showToast "协议页面开发中"） | login.vue | 🟡 非阻塞 |
| 4 | P2 | Sass @import 弃用警告 | 全局 | 🟡 已知 |

## 问题详解

### P0 — POST /auth/refresh 不在 EXEMPT_PATHS

**调用链：**
```
前端 callRefreshAPI() → uniRequest POST /api/auth/refresh (无 Authorization header)
  → Express auth middleware → 不在白名单 → 无 token → throw unauthenticated() 1001
  → callRefreshAPI 不处理 1001 → throw "刷新失败"
  → 外层 catch → redirectToLogin() → 用户被踢出
```

**根因：** `EXEMPT_PATHS` 只含 `POST /auth/login` 和 `GET /health`，缺 `POST /auth/refresh`。refresh 端点从 body 取 `refresh_token` 验证，不走 Authorization header，必须在 JWT 中间件放行。

**修复：** `EXEMPT_PATHS` 加入 `{ method: 'POST', path: '/auth/refresh' }`

### P1 — Store 与 Storage token 不同步

**根因：** `api/index.js` 的 `callRefreshAPI()` 写 Storage 但不更新 Pinia store。`initAuth()` 中 getMe 触发自动刷新后，store 仍持有旧 token。

**修复：** `initAuth()` 中 getMe 成功后从 Storage 重新读取 token 同步回 store：
```js
const syncedAccess = uni.getStorageSync('accessToken');
const syncedRefresh = uni.getStorageSync('refreshToken');
if (syncedAccess) this.accessToken = syncedAccess;
if (syncedRefresh) this.refreshToken = syncedRefresh;
```

**Why:** 只在 initAuth 路径补齐。一般 API 调用中自动刷新导致的 store 滞后影响极小——Storage 始终是真实数据源，API 都从 Storage 取 token，不因 store 滞后而失败。

## 验证清单

| 检查项 | 维度 | 结果 |
|--------|------|:--:|
| 路由 ↔ 中间件链路完整 | 后端 | ✅ |
| JWT 中间件白名单覆盖 login/refresh | 后端 | ✅ |
| 参数校验覆盖所有端点 | 后端 | ✅ |
| 错误码与 API 文档一致 | 后端 | ✅ |
| 禁止 SELECT * — 显式字段列表 | 数据库 | ✅ |
| 参数化查询防 SQL 注入 | 安全 | ✅ |
| API 统一返回 `{ code, message, data }` | 规范 | ✅ |
| 控制器无业务逻辑 | 架构 | ✅ |
| 前端 HTTP 封装 skipAuth 差异化 | 前端 | ✅ |
| Pinia Store 结构完整 | 前端 | ✅ |
| login.vue SCSS 全部使用 tokens.scss | 前端 | ✅ |
| App.vue onLaunch 调用 initAuth | 前端 | ✅ |
| 前端 uni-app 编译 | 构建 | ✅ DONE |

## 第 2 轮改动清单

| # | 文件 | 操作 | 说明 |
|---|------|:--:|------|
| 1 | server/src/routes/auth.js | 改 | /login 加 sensitiveLimiter |
| 2 | server/src/services/auth.js | 改 | login() 返回 isNewUser |
| 3 | server/src/middleware/auth.js | 改 | EXEMPT_PATHS 加入 refresh（审查修复） |
| 4 | miniprogram/src/api/index.js | **新建** | HTTP 封装 + token 自动刷新 |
| 5 | miniprogram/src/api/auth.js | 重写 | login/refresh/me API |
| 6 | miniprogram/src/store/user.js | 重写 | 用户认证 Store + initAuth token 同步（审查修复） |
| 7 | miniprogram/src/store/app.js | 重写 | 全局应用 Store |
| 8 | miniprogram/src/pages/auth/login.vue | 重写 | 完整登录页 |
| 9 | miniprogram/src/App.vue | 改 | onLaunch 调用 initAuth |

## 关联

- [[iteration1-skeleton-status]] — 第 1 轮骨架
- [[known-bugs]] — BUG-003 + BUG-004 已记录

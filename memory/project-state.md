---
name: project-state
description: 项目当前状态 — 第 5 轮完成，IM+通知全栈实现，测试 7 文件 106 用例通过，准备进入第 6 轮交易流程
metadata:
  type: project
  updatedAt: 2026-06-08
  currentIteration: 5
  commit: 89b8a47
---

# 项目当前状态

**评估日期：** 2026-06-08
**阶段：** 第 5 轮（聊天 IM + 通知中心）全部完成 ✅

## 迭代进度总览

| 迭代 | 名称 | 状态 | 文件数 | Bug 发现/修复 | 完成日期 |
|:--:|------|:--:|:--:|:--:|:--:|
| 1 | 骨架补全 | ✅ | 115 (57 后端 + 58 前端 stub) | 2/2 (BUG-001~002) | 2026-06-07 |
| 2 | 注册/登录 | ✅ | 9 (3 后端 + 6 前端) | 2/2 (BUG-003~004) | 2026-06-07 |
| 3 | 商品发布与浏览 | ✅ | 14 (6 后端修复 + 8 前端) | 9/9 (BUG-005~013) | 2026-06-07 |
| 3A | 第 3 轮审计修复 | ✅ | 10 (4 后端 + 4 前端 + 2 基础设施) | 16/16 (BUG-014~020 + P2-1~P2-5) | 2026-06-08 |
| 3T | 第 3 轮测试补全 | ✅ | 4 测试文件 (75 用例) | — | 2026-06-08 |
| 4 | 搜索 + 筛选 | ✅ | 6 (3 新建后端测试 + 1 后端修改 + 2 前端) | — | 2026-06-08 |
| 5 | 聊天 (IM) | ✅ | 13 (4 后端 + 9 前端) | 9/9 (A5-001~009) | 2026-06-08 |
| 5A | 第 5 轮审计修复 | ✅ | 4 (3 前端 + 1 文档) | 8/8 (P1×2 + P2×6) | 2026-06-08 |
| 6 | 交易流程 | ⏳ | — | — | — |

## 第 1 轮：骨架补全 ✅

- 后端 5 层架构 58 文件全部有实质代码
- 前端 57 文件含 25 页面 stub + 基础设施
- 6 项出口验证全部通过（迁移/种子/health/编译/TabBar/敏感词）
- 发现 BUG-001 (token_version) + BUG-002 (空 catch)，均已修复

## 第 2 轮：注册/登录 ✅

**文件：** 后端 3 改 + 前端 6 新/改写
**关键产出：** HTTP 拦截器 (api/index.js) / Pinia Store (user+app) / 登录页面 UI / App.vue 启动鉴权恢复
**审查发现：** BUG-003 (refresh 未加入白名单, P0) + BUG-004 (Store token 未同步, P1)，均已修复

## 第 3 轮：商品发布与浏览 ✅

**文件：** 后端 6 修复 + 前端 8 新/改写
**关键产出：** COS 直传 / 瀑布流首页 / 商品详情 / 3 步发布 / 我的发布
**审查发现 7 个新 Bug：**

| Bug | 级别 | 简述 | 状态 |
|-----|:--:|------|:--:|
| BUG-005 | P0 | `pool.execute()` 不支持 LIMIT/OFFSET 参数化 | ✅ |
| BUG-006 | P1 | findBySeller page/pageSize 未解析整数 | ✅ |
| BUG-007 | P0 | chooseAndUpload 丢弃 ImageUploader 已选文件 | ✅ |
| BUG-008 | P0 | detail.vue 重试按钮传 event 为 id | ✅ |
| BUG-009 | P1 | COS Policy content-type 条件格式非法 | ✅ |
| BUG-010 | P1 | x-cos-security-token 错误传 HMAC 签名值 | ✅ |
| BUG-011 | P2 | db.js 注释未同步（execute→query） | ✅ |
| BUG-012 | P2 | create/update 返回值未嵌套 seller | ✅ |
| BUG-013 | P2 | ProductCard 占位图空 data URI | ✅ |

## 当前代码状态

| 层 | 完成度 | 说明 |
|------|:--:|------|
| 后端 server/src/ | **~85%** | 5 层 + 7 中间件 + 9 工具 + 6 Repository（含事务） |
| 前端 miniprogram/src/ | **~35%** | 8/25 页面完成（登录、首页、商品详情、发布、我的发布、搜索、聊天列表、聊天详情、通知中心），6 组件 + 6 API 模块 |
| 数据库 | **100%** | 14 表 + 5 迁移 + 种子数据 |
| 测试 | **~30%** | 7 文件 106 用例全部通过 |
| 文档 | **100%** | 16 份文档全部完成，7 轮审阅 95 问题清零 |

## 基础设施

- **PM2 部署配置**：ecosystem.config.js（fork 模式，256M 内存限制）
- **敏感词库**：437 词 DFA 算法，已集成到发布/编辑流程
- **COS 上传**：永久密钥 STS 风格凭证 + 前端直传，前后端均已修正
- **JWT 双 Token**：access + refresh，无感刷新拦截器已就绪
- **IM**：腾讯云 IM Provider 抽象层已就绪，前端 SDK 待集成

## 已知待办（非阻塞）

| 项 | 优先级 | 说明 |
|----|:--:|------|
| 编辑商品页面 | P1 | 复用 publish 组件 + 预填数据 |
| "我想要" 按钮 | P1 | 依赖迭代 6 订单 |
| 前端测试用例 | P1 | 编码阶段随功能补齐 |
| Sass @import → @use | P2 | 弃用警告，不阻塞 |
| Math.random() in COS key | P2 | MVP 可接受，后续改 UUID |

## 下一步

### 第 3 轮测试产出（2026-06-08）

| 文件 | 类型 | 用例 | 覆盖范围 |
|------|:--:|:--:|------|
| `server/__tests__/unit/utils/sensitive-filter.test.js` | 单元 | **18** | DFA 匹配/注释/空词库/超长文本/replace/真实词库验证 |
| `server/__tests__/unit/utils/cos.test.js` | 单元 | **8** | STS 凭证结构/过期时间/content-type 白名单/mock 模式/user path 隔离 |
| `server/__tests__/unit/services/product.test.js` | 单元 | **30** | 价格校验/信誉分阈值/图片数量/敏感词/状态守卫/分页容错/响应格式/软删除+TOCTOU |
| `server/__tests__/integration/products.test.js` | 集成 | **19** | 发布(201/4001/4008/1001/6002)/列表(cover_image/seller/分类/分页)/详情(正确+404+off_shelf)/编辑/删除/我的发布 |

**基础设施修复（6 项）：**
- `setup.js`：修复 `createTestUser()` INSERT 返回值解构 + `setupTestDb()` 加 DROP TABLE + FOREIGN_KEY_CHECKS
- `vitest.config.js`：添加 `fileParallelism: false` 防并行 DDL 冲突
- 测试文件全局修复：db.query() INSERT→ResultSetHeader / SELECT→rows[] / DECIMAL→string / JSON 自动解析 / supertest .set() 参数格式

**全部通过：** `Test Files 4 passed | Tests 75 passed | Duration 8.18s`

**与测试计划已知差异（3 项）：** SF-003（非中文字符）/ SF-004（大小写）/ SF-010（文件不存在错误码）— DFA 实现与测试计划预期不同，已标记 TODO。

---

## 第 4 轮：搜索 + 筛选 ✅（2026-06-08）

**文件：** 后端 1 改 (repository) + 2 新测试文件 + 前端 2 改写 (FilterSidebar + search 页) + 首页 1 改

**关键产出：**
- 后端：关键词搜索扩展至卖家昵称 + 分类名匹配（修复 COUNT 查询 JOIN 缺失）
- 测试：`search.test.js` (14 单元用例) + `integration/search.test.js` (9 集成用例) — 覆盖 SH-001~005, SE-001~003 + 卖家昵称/分类/组合筛选/排序
- 前端：FilterSidebar 抽屉组件 (450 行, 3 组筛选+价格校验) + 搜索页面 (480 行, 历史/热门/瀑布流/排序/筛选标签)
- 首页：筛选按钮 + FilterSidebar 集成

**验证结果：** `npx vitest run` 6 文件 98 用例全过，前端 ESLint 无新增错误
**详细计划：** [[../plans/iteration4-search-filter.md]]

---

## 第 5 轮：聊天 (IM) + 通知中心 ✅（2026-06-08）

**文件：** 后端 1 新建 + 1 重构 + 2 修改 + 前端 9 新建/改写

**关键产出：**

| 层 | 文件 | 操作 | 行数 | 关键功能 |
|:--:|------|:--:|:--:|------|
| 后端 | `controllers/im.js` | 新建 | 32 | UserSig 签发端点 |
| 后端 | `routes/im.js` | 新建 | 14 | GET /api/im/user-sig |
| 后端 | `app.js` | 修改 | +3 | 注册 IM 路由 |
| 后端 | `services/im/tencent.js` | 重构 | -80 | 消除重复，im-api.js 为唯一源 |
| 后端 | `.env.test` | 修改 | — | 真实 IM 凭证（已 gitignore） |
| 测试 | `__tests__/integration/im.test.js` | 新建 | 130 | 8 用例：鉴权/数据字段/格式 |
| 前端 | `api/im.js` | 新建 | 16 | getUserSig 封装 |
| 前端 | `api/notification.js` | 新建 | 46 | 4 端点封装 |
| 前端 | `utils/im.js` | 重写 | 230 | IM SDK 单例/登录/事件/会话/消息/退避重试 |
| 前端 | `pages/chat/list.vue` | 重写 | 470 | 会话列表：头像/摘要/未读红点/下拉刷新/长按删除/CONVERSATION_UPDATE 监听 |
| 前端 | `pages/chat/detail.vue` | 重写 | 350 | 聊天界面：气泡/时间标签/系统消息/历史加载/MESSAGE_RECEIVED 监听/自动滚底 |
| 前端 | `pages/notification/index.vue` | 重写 | 280 | 通知中心：类型筛选/已读未读/分页/全部已读 |
| 前端 | `pages/product/detail.vue` | 修改 | ~40 | "聊一聊"按钮接入 IS SDK → 跳转聊天页 |
| 前端 | `store/app.js` | 修改 | +20 | unreadMsgCount + unreadNotifyCount + totalUnread getter |
| 前端 | `App.vue` | 修改 | +50 | IM 初始化 + 通知轮询(30s) + TabBar 角标 watch |
| 前端 | `.eslintrc.js` | 修改 | +4 | uni/getCurrentPages 全局声明 |

**架构决策：**
- 聊天消息通过 IM SDK WebSocket 直连腾讯云，不经过 Express（DFD D3/D4）
- `utils/im-api.js` 为 UserSig/REST API 唯一事实源，`services/im/tencent.js` 改为引入方
- 会话数据由 IM SDK 管理（`getConversationList()`），服务端不存储会话
- 系统通知走 Express → MySQL `notifications` 表
- conversationID 格式：`C2C{对方userId}`（腾讯云 IM C2C 规范）

**验证结果：** `npx vitest run` 7 文件 106 用例全过，前端 ESLint 0 错误（仅 20 有意 console.warn）
**详细计划：** [[../plans/iteration5-chat-im.md]]

---

## 下一步

**第 6 轮：交易流程** — 订单状态机完整流转 / 超时自动处理（24h 取消 + 72h 确认）/ 双向评价（沟通/守时/描述三维度）

**Why:** IM 通信能力已就绪，下一步实现交易闭环使产品可用。

**How to apply:** 新一轮迭代开始时更新 currentIteration 字段。编码前先读此文件 + [[known-bugs]] + 对应 iteration review 了解上下文。

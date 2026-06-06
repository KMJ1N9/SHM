---
name: bug-fix
description: 校园二手交易小程序 — Bug 修复 Prompt
version: v1.0
created: 2026-06-05
triggers: 收到 Bug 报告 / 用户要求修复问题 / 测试失败
---

# Bug 修复 Prompt

## 角色

你是"校园二手交易小程序"的 **系统调试专家**。技术栈：uni-app (Vue 3) + Node.js/Express + MySQL + mysql2。你的方法论来自 `systematic-debugging` skill：4 阶段（根因→模式→假设→实现），3 次以上修复同一模块 = 架构问题。

---

## 输入

用户将提供以下一种或多种信息：

1. **Bug 描述**（发生了什么、期望什么）
2. **复现步骤**（如何触发）
3. **错误日志/截图**（如有）
4. **影响范围**（哪个页面/API/功能）

---

## 修复流程（4 阶段，严格按顺序）

### Phase 1 — 根因定位（禁止跳过）

**在写任何修复代码之前，必须先完成以下步骤：**

1. **复现 Bug**
   - 根据用户提供的步骤尝试复现
   - 如果无法复现，告知用户并要求更多信息
   - 不允许对无法复现的 Bug 做"推测性修复"

2. **定位根源**
   - 前端 Bug：用微信开发者工具调试器（Sources 面板设断点 + AppData 面板查数据 + Wxml 面板查 DOM）定位到具体组件/逻辑。注意小程序无 `document`/`window`，不能用浏览器 DevTools
   - 后端 Bug：检查 winston 错误日志 → 追踪到具体 Service/Repository 方法
   - 数据库 Bug：用 `EXPLAIN` 检查慢查询 → 检查索引
   - 确认是代码逻辑错误、数据异常还是环境配置问题

3. **输出根因分析**（格式固定）：
   ```markdown
   ## 根因分析
   - **直接原因：** [代码中哪里出错了]
   - **根本原因：** [为什么会写出这个错误 — 边界条件遗漏/API 理解错误/状态管理混乱/并发问题]
   - **影响范围：** [影响了哪些功能、哪些用户]
   - **是否已有类似问题：** [检查代码库中是否有相同模式的其他位置]
   ```

### Phase 2 — 模式识别

| 模式 | 特征 | 常见根因 |
|------|------|---------|
| **状态不一致** | 前端显示和后端数据不匹配 | 缓存未失效、事务未提交、乐观锁冲突 |
| **空指针/undefined** | `Cannot read property 'x' of undefined` | API 返回结构与前端期望不一致、异步数据未到就先渲染 |
| **状态机错误** | 订单/商品状态转换到非法状态 | Service 层缺少状态前置检查、并发请求竞态 |
| **死循环** | 页面卡死 / CPU 100% | `watch` 循环依赖、`computed` 互相引用 |
| **Token 问题** | 反复跳登录页 | Token 刷新逻辑错误、token_version 校验失败 |
| **权限绕过** | 非管理员能访问管理接口 | 中间件未挂载或角色检查不完整 |
| **并发冲突** | 同一商品被重复下单 | 缺少幂等检查、乐观锁未生效 |

### Phase 3 — 修复方案

制定修复方案时遵循以下约束：

1. **最小修改原则**
   - 只改引发 Bug 的最小代码单元
   - 不"顺手优化"相邻代码
   - 不重构整个模块（除非 Bug 确实由架构问题引起）

2. **修复范围**
   - 前端 Bug：通常只改 1 个 `.vue` 文件或 1 个 `.js` 模块
   - 后端 Bug：通常只改 1 个 Service 或 Repository 方法
   - 数据库 Bug：通常是加索引或修正 SQL

3. **方案输出格式**：
   ```markdown
   ## 修复方案
   **文件：** `server/src/services/order.js:142`
   **修改内容：** 在 `confirmOrder` 方法中增加 `status === 'met'` 的前置检查
   **修改原因：** 原代码只检查了 `status !== 'completed'`，遗漏了 `cancelled` 和 `timeout` 状态下的非法确认
   **风险评估：** 低风险 — 仅增加一个 if 条件，不影响正常流程
   ```

### Phase 4 — 实现与验证

1. **写复现测试**（在修复代码之前）
   ```js
   // 先写一个能复现 Bug 的测试
   it('已取消的订单不应能确认收货', async () => {
     const order = await createTestOrder({ status: 'cancelled' })
     const res = await request(app).put(`/api/orders/${order.id}/confirm`)
     expect(res.body.code).toBe(3001) // 订单状态不允许此操作
   })
   ```

2. **实施修复**
   - 后端：在 Service 或 Repository 层修复
   - 前端：在组件 `<script setup>` 或 API 封装层修复

3. **验证修复**（依次执行）：
   ```bash
   npx vitest run -t "已取消的订单不应能确认收货"  # 新测试通过
   npx vitest run                                     # 全部已有测试仍然通过
   ```
   - 手动验证 Bug 不再复现
   - 检查修复是否引入新问题（回归测试）

4. **提交记录**：
   ```bash
   git commit -m "fix: 修复已取消订单仍可确认收货的问题"
   ```
   - 使用 `fix:` 前缀（commitlint 要求）
   - 描述清晰，便于 CHANGELOG 生成

---

## 常见 Bug 速查表

| 症状 | 优先检查位置 | 常见原因 |
|------|-------------|---------|
| API 返回 2001 | Repository 的 SQL 查询条件 | 软删除未过滤 (`deleted_at IS NULL`) |
| API 返回 3001 | Service 的订单状态机检查 | 状态转换的 if 条件不完整 |
| API 返回 4001 | Controller 的参数提取 / validate 中间件规则 | 前端传参格式与后端期望不一致 |
| API 返回 4008/4009 | `auth` 中间件的信誉分检查 | 信誉分边界值（恰好 60/30）判断符号错误（`<` vs `<=`） |
| API 返回 6999 | 任意未知异常 → winston error 日志 | 先查日志！常见：MySQL 连接超时、外键约束违反 |
| 小程序白屏 | App.vue / main.js / pages.json | 页面路径配置错误、组件导入失败 |
| 页面数据不刷新 | Store / onShow 生命周期 | 未在 `onShow` 中重新请求数据、缓存未失效 |
| 图片上传失败 | COS STS / `utils/cos.js` | Token 过期（30min）、格式校验失败（非 jpg/png/webp） |

---

## 数据库 Bug 专项

当 Bug 涉及数据库查询时：

```sql
-- 1. 先用 EXPLAIN 检查执行计划
EXPLAIN SELECT ... FROM products WHERE status = 'active' ORDER BY created_at DESC;

-- 2. 检查索引使用情况
SHOW INDEX FROM products;

-- 3. 检查慢查询日志（>200ms 会 warn，>1000ms 会 error）
-- winston 日志中搜索 "slow query"
```

**常见数据库 Bug：**
- 没有索引 → 全表扫描 → 加索引
- N+1 查询 → 循环内查询数据库 → 改 JOIN
- 死锁 → 事务中多表操作顺序不一致 → 统一操作顺序
- 字符集问题 → emoji 写入报错 → 确认表使用 `utf8mb4`

---

## 禁止事项

- ❌ 不写复现测试就直接修改代码
- ❌ 不在日志中定位根因而做"猜测性修复"
- ❌ 修改与 Bug 无关的代码（Surgical Changes 原则）
- ❌ 修复了 Bug 但不确认已有测试是否仍然通过
- ❌ 3 次以上修复同一模块的 Bug 却不考虑架构问题
- ❌ 后端使用 `console.log` 调试 — 必须用 winston logger
- ❌ 前端遗留 `console.log` 到生产代码 — 调试完成后必须删除

# 第 10 轮编码计划：工程化基础审计 + 补漏

> **状态：** ✅ 已完成
> **完成日期：** 2026-06-11
> **预估工时：** ~5 h ≈ 0.5 天（实际 ~2.5 h，基础设施第 1 轮已就绪）
> **目标：** winston 日志完善 + AppError 统一错误码覆盖率验证 + .env 分层审计 + 敏感信息泄露扫描
> **依据文档：** 编码迭代计划 §第 10 轮、技术架构文档 §九（工程化基础设施）、API 接口文档 §四（错误码规范）、rules/error-handling-rules、rules/security-rules

---

## 〇、本轮特殊性

编码迭代计划原文（§第 10 轮 L1521）：

> **本轮重点不是写新代码，而是审查 + 补漏 + 验证**

第 10 轮的核心基础设施（winston / AppError / error-handler / access-log / .env 分层）在第 1 轮骨架补全中已全部搭建。本轮实际工作为 **4 项审计 + 4 项补漏 + 5 项验证**，不涉及新功能编码。

---

## 一、现状分析

### 1.1 已就绪的基础设施 ✅

| 文件 | 状态 | 行数 | 完成于 | 关键能力 |
|------|:--:|:--:|:--:|------|
| `utils/logger.js` | ✅ | 103 | 第 1 轮 | winston 3 级日志（access/error/business），开发→控制台彩色输出，生产→DailyRotateFile JSON 格式 |
| `utils/app-error.js` | ✅ | 34 | 第 1 轮 | `AppError(code, httpStatus, message, detail)` 统一业务异常类 |
| `utils/errors.js` | ✅ | 163 | 第 1 轮 | 30 个错误码工厂函数（1xxx~6xxx），业务层禁止直接 `new AppError()` |
| `middleware/error-handler.js` | ✅ | 85 | 第 1 轮 | 4 类异常处理（AppError / Joi / JSON 解析 / 未知），production 隐藏 detail |
| `middleware/access-log.js` | ✅ | 41 | 第 1 轮 | 请求级 method/url/status/duration 日志，500→error 400→warn 成功→info |
| `config/index.js` | ✅ | 157 | 第 1 轮 | 5 环境分层（development/staging/production/test + example），80+ 配置项 |
| `.env.development` | ✅ | — | 第 1 轮 | 开发环境变量（本地测试用） |
| `.env.staging` | ✅ | — | 第 1 轮 | 预发布环境变量模板 |
| `.env.production` | ✅ | 77 行 | 第 1 轮 | 生产环境变量模板（NODE_ENV=production, LOG_LEVEL=warn, 5 处必填密钥占位） |
| `.env.test` | ✅ | — | 第 1 轮 | 测试环境变量 |
| `.env.example` | ✅ | — | 第 1 轮 | 环境变量模板（开发起点） |

### 1.2 四项审计发现（grep 结果）

#### 审计 A：console.log 残余

```
server/src/app.js:135      console.log(`\n🏫 校园二手交易平台 — 服务端`);
server/src/app.js:136      console.log(`   环境: ${config.nodeEnv}`);
server/src/app.js:137      console.log(`   端口: ${PORT}`);
server/src/app.js:138      console.log(`   健康检查: http://localhost:${PORT}/api/health\n`);
server/src/models/db.js:26 console.log(`[DB] MySQL 连接成功: ...`);
server/src/utils/sensitive-filter.js:66 console.log(`[SENSITIVE] 词库加载完成: ...`);
server/src/models/migrate.js:67~162  共 16 处 console.log（CLI 工具输出）
```

| 位置 | 数量 | 判断 | 处理 |
|------|:--:|:--:|------|
| `app.js` startup banner | 4 | **保留** — Node.js 进程启动消息，非运行时日志，符合惯例 |
| `migrate.js` CLI | 16 | **保留** — 命令行工具输出，不是 Express 请求链路日志 |
| `db.js` 连接成功 | 1 | **替换** — 改为 `logger.business.info()` |
| `sensitive-filter.js` 词库加载 | 1 | **替换** — 改为 `logger.business.info()` |

#### 审计 B：裸 Error 抛出（throw new Error 而非 AppError）

```
server/src/config/index.js:37     throw new Error(`[CONFIG] 缺少必填...`)
server/src/models/migrate.js:83   throw new Error(`迁移文件 ${file} 缺少 up() 函数`)
server/src/services/im/provider.js:36 throw new Error(`[IM Provider] ...`)
```

| 位置 | 判断 | 处理 |
|------|:--:|------|
| `config/index.js:37` required() | **保留** — 进程启动配置校验失败必须 crash，非请求链路，不需要 AppError |
| `migrate.js:83` 迁移文件校验 | **保留** — CLI 工具，非 HTTP 请求链路 |
| `services/im/provider.js:36` | **替换** — 虽然是 require 时同步执行，但位于 services 层，统一为 `internal()` AppError |

#### 审计 C：business 日志覆盖率

**关键发现：`logger.business` 在整个 `server/src/` 中零调用。**

所有 service 方法的关键业务操作均无业务日志记录。以下为需要补全的关键路径：

| Service | 方法 | 关键操作 | 需补日志 |
|------|------|------|:--:|
| `auth.js` | `login()` | 用户登录成功 | ✅ |
| `auth.js` | `refresh()` | Token 刷新 | ✅ |
| `product.js` | `create()` | 商品发布 | ✅ |
| `product.js` | `update()` | 商品编辑 | ✅ |
| `product.js` | `delete()` | 商品软删除 | ✅ |
| `order.js` | `create()` | 下单 | ✅ |
| `order.js` | `confirmOrder()` | 确认收货 | ✅ |
| `order.js` | `cancelOrder()` | 取消订单 | ✅ |
| `order.js` | `markAsMet()` | 标记面交 | ✅ |
| `report.js` | `create()` | 举报创建 | ✅ |
| `admin.js` | `resolveReport()` | 工单裁决 | ✅ |
| `admin.js` | `banUser()` | 封禁用户 | ✅ |
| `review.js` | `create()` | 创建评价 | ✅ |

#### 审计 D：错误码覆盖率

**对照 API 接口文档 §四 完整错误码表 与 `utils/errors.js`：**

| 分段 | API 文档定义 | errors.js 实现 | 匹配 |
|------|:--:|:--:|:--:|
| 1xxx | 1001~1005 (5 个) | 1001~1005 (5 个) | ✅ 完全匹配 |
| 2xxx | 2001, 2002 (2 个) | 2001, 2002, 2004 (3 个) | ⚠️ 2004 无文档对应 |
| 3xxx | 3001~3006 (6 个) | 3001, 3002, 3004~3006 (5 个) | ✅ 3003 文档注为"幂等 code=0"，非错误码 |
| 4xxx | 4001~4009 (9 个) | 4001~4009 (9 个) | ✅ 完全匹配 |
| 5xxx | 5001~5003 (3 个) | 5001~5003 (3 个) | ✅ 完全匹配 |
| 6xxx | 6001~6004 + 6999 (5 个) | 6001~6004 + 6999 (5 个) | ✅ 完全匹配 |

**结论：30 个文档错误码 100% 覆盖。errors.js 中多出 2004（`invalidPagination`），需确认实际使用情况。**

### 1.3 额外审计项

#### E：敏感信息泄露扫描

| 检查项 | 位置 | 结果 |
|------|------|:--:|
| 密码明文出现在日志 | 全量 grep `password` in logger 调用 | ✅ 无泄露 — 所有密码仅存在于 config/.env 中 |
| 手机号明文出现在日志 | 全量 grep `phone` in logger 调用 | ✅ 无泄露 — 日志中无 phone 字段 |
| Token 明文出现在日志 | 全量 grep `token` in logger 调用 | ✅ 无泄露 |
| `phone` 字段暴露在 API 响应 | `middleware/auth.js:89` `req.user.phone` | ✅ 仅注入 req 对象供下游，非日志 |
| `admin_phone` 暴露在审计日志 API | `repository/report.js:296` | ⚠️ 管理端审计日志返回管理员手机号，需确认是否为设计意图 |

#### F：production 环境 detail 泄露检查

`error-handler.js:46` 已正确实现：
```js
detail: isDev ? err.detail : null
```
`.env.production` 中 `NODE_ENV=production` — production 环境下 `detail` 字段为 `null` ✅

---

## 二、架构关键决策

### 决策 1：business 日志的粒度

```
原则：只记录业务关键操作，不记录查询操作

✅ 需要记录：
  - 状态变更：下单、确认收货、取消、面交确认、工单裁决
  - 资源创建：发布商品、提交举报、创建评价
  - 高风险操作：封禁用户、商品下架、Token 刷新
  - 异常降级：IM 初始化失败、通知推送失败

❌ 不需要记录：
  - 查询操作（list/detail/search — access-log 已覆盖）
  - 分页参数校验失败（Joi 校验已有 error 日志）
  - 数据库连接成功（db.js 已有 info 日志）
```

### 决策 2：business 日志字段规范

```js
logger.business.info('操作描述', {
  action: 'order.create',        // 动作标识符（模块.操作）
  userId: 123,                    // 操作用户
  targetId: 456,                  // 操作目标（可选）
  result: 'success',              // 结果标识
  // 不在日志中记录：用户手机号、密码、Token、完整的 IM 消息内容
});
```

### 决策 3：2004 invalidPagination 的去留

- `invalidPagination` 在 BUG-017 修复中已从 `services/product.js` 移除死代码 import
- 该工厂函数仍在 `errors.js` 中定义但无调用方
- **处理：从 errors.js 中删除 2004（符合规则"不写 speculative 代码"），或保留作为向前兼容**
- **建议保留** — 其他模块的分页参数校验可能需要此错误码，且不影响功能

### 决策 4：config/index.js 中 bare Error 的处理

`required()` 函数在进程启动时执行。若环境变量配置错误（如缺少 JWT_ACCESS_SECRET），进程应 crash 并输出明确信息。这是标准的 fail-fast 模式，不属于 HTTP 请求链路的业务错误。**保留现状不修改。**

---

## 三、任务清单

### Phase 1：审计 grep（~30 min）

| # | 任务 | 文件 | 产出 |
|:--:|------|------|------|
| 1.1 | 确认 console.log 分类 | `server/src/` 全量 | 分类清单（保留 20 处 / 替换 2 处） |
| 1.2 | 确认 bare Error 分类 | `server/src/` 全量 | 分类清单（保留 2 处 / 替换 1 处） |
| 1.3 | 确认 business 日志缺口 | `server/src/services/` 全量 | 14 个方法需补日志 |
| 1.4 | 错误码覆盖率交叉比对 | `errors.js` ↔ API 文档 | 覆盖率 100%，2004 保留 |
| 1.5 | 敏感信息泄露 grep | `server/src/` — password/phone/token/secret | 无泄露，除 admin_phone 需确认 |

### Phase 2：补漏 — console.log → logger（~10 min）

| # | 文件 | 改动 | 行数 |
|:--:|------|------|:--:|
| 2.1 | `models/db.js:26` | `console.log` → `logger.business.info('[DB] MySQL 连接成功', { host, port, database })` | ~3 行 |
| 2.2 | `utils/sensitive-filter.js:66` | `console.log` → `logger.business.info('[SENSITIVE] 词库加载完成', { count: words.length })` | ~3 行 |

### Phase 3：补漏 — bare Error → AppError（~5 min）

| # | 文件 | 改动 | 行数 |
|:--:|------|------|:--:|
| 3.1 | `services/im/provider.js:36` | `throw new Error(...)` → `throw internal('[IM Provider] ...')` — 引入 `internal` from `utils/errors` | ~3 行 |

### Phase 4：补漏 — business 日志（~45 min）

| # | 文件 | 方法 | 日志点 |
|:--:|------|------|------|
| 4.1 | `services/auth.js` | `login()` | 登录成功后 → `logger.business.info('用户登录', { action: 'auth.login', userId, result: 'success' })` |
| 4.2 | `services/auth.js` | `refresh()` | Token 刷新后 → `logger.business.info('Token 刷新', { action: 'auth.refresh', userId })` |
| 4.3 | `services/product.js` | `create()` | 发布成功后 → `logger.business.info('商品发布', { action: 'product.create', userId, productId })` |
| 4.4 | `services/product.js` | `update()` | 编辑成功后 → `logger.business.info('商品编辑', { action: 'product.update', userId, productId })` |
| 4.5 | `services/product.js` | `delete()` | 软删除成功后 → `logger.business.info('商品删除', { action: 'product.delete', userId, productId })` |
| 4.6 | `services/order.js` | `create()` | 下单成功后 → `logger.business.info('订单创建', { action: 'order.create', userId, orderId, productId })` |
| 4.7 | `services/order.js` | `confirmOrder()` | 确认收货后 → `logger.business.info('订单确认', { action: 'order.confirm', userId, orderId })` |
| 4.8 | `services/order.js` | `cancelOrder()` | 取消订单后 → `logger.business.info('订单取消', { action: 'order.cancel', userId, orderId })` |
| 4.9 | `services/order.js` | `markAsMet()` | 标记面交后 → `logger.business.info('面交确认', { action: 'order.met', userId, orderId })` |
| 4.10 | `services/report.js` | `create()` | 举报创建后 → `logger.business.info('举报创建', { action: 'report.create', userId, reportId })` |
| 4.11 | `services/admin.js` | `resolveReport()` | 工单裁决后 → `logger.business.info('工单裁决', { action: 'report.resolve', userId, reportId, resolution })` |
| 4.12 | `services/admin.js` | `banUser()` | 封禁用户后 → `logger.business.info('用户封禁', { action: 'user.ban', userId: adminId, targetUserId })` |
| 4.13 | `services/review.js` | `create()` | 评价创建后 → `logger.business.info('评价创建', { action: 'review.create', userId, reviewId, orderId })` |
| 4.14 | `services/auth.js` | `login()` | 登录失败后 → `logger.business.warn('登录失败', { action: 'auth.login_fail', phone: maskedPhone, reason })`（手机号脱敏：138****1234） |

> **日志字段一致性：** 所有 business 日志统一使用 `action`（`模块.操作` 格式）、`userId`、目标 ID、`result` 字段。禁止在 business 日志中输出明文手机号、密码、Token。

### Phase 5：敏感信息审计确认（~10 min）

| # | 检查项 | 方法 |
|:--:|------|------|
| 5.1 | `admin_phone` 在审计日志 API 的暴露 | 确认 `GET /api/admin/logs` 的授权（仅 admin）→ 设计意图合理，保留 |
| 5.2 | 所有 logger 调用中无 phone/password/token 明文 | `grep -E "(logger\.|console\.).*phone|password|token" server/src/ --include="*.js"` |

### Phase 6：文档更新（~15 min）

| # | 产出 | 说明 |
|:--:|------|------|
| 6.1 | 更新 `memory/project-state.md` | 第 10 轮完成状态 |
| 6.2 | 更新 `README.md` | 更新迭代进度表（第 1~9 轮 actual status） |
| 6.3 | `memory/known-bugs.md` | 如有新发现 |
| 6.4 | `CHANGELOG` | 第 10 轮变更摘要 |

---

## 四、修改文件清单

| 文件 | 操作 | 改动量 | 说明 |
|------|:--:|:--:|------|
| `server/src/models/db.js` | 修改 | ~3 行 | console.log → logger.business.info |
| `server/src/utils/sensitive-filter.js` | 修改 | ~3 行 | console.log → logger.business.info |
| `server/src/services/im/provider.js` | 修改 | ~3 行 | throw new Error → internal() |
| `server/src/services/auth.js` | 修改 | ~8 行 | 添加 business 日志（login 成功+失败 / refresh） |
| `server/src/services/product.js` | 修改 | ~12 行 | 添加 business 日志（create / update / delete） |
| `server/src/services/order.js` | 修改 | ~16 行 | 添加 business 日志（create / confirm / cancel / markAsMet） |
| `server/src/services/report.js` | 修改 | ~4 行 | 添加 business 日志（create） |
| `server/src/services/admin.js` | 修改 | ~8 行 | 添加 business 日志（resolveReport / banUser） |
| `server/src/services/review.js` | 修改 | ~4 行 | 添加 business 日志（create） |
| `memory/project-state.md` | 修改 | ~30 行 | 更新第 10 轮状态 |
| `README.md` | 修改 | ~5 行 | 更新迭代进度表 |

**总计：11 个文件修改，~96 行改动。无新增文件。**

---

## 五、验证清单

| # | 验证项 | 方法 | 预期结果 |
|:--:|------|------|------|
| 1 | 30 个错误码全部定义 | `grep -c "=> new AppError" server/src/utils/errors.js` | ≥ 29 个工厂函数 |
| 2 | 无 console.log 残余（除 CLi/server start） | `grep -r "console\.log" server/src/ --include="*.js" \| grep -v migrate.js \| grep -v "app.js:135"` | 仅 app.js startup banner 4 行（符合设计） |
| 3 | 无裸 `throw new Error`（除 config/migrate） | `grep -r "throw new Error" server/src/ --include="*.js" \| grep -v config/index.js \| grep -v migrate.js` | 返回空 |
| 4 | 错误响应格式统一 | `curl -X POST localhost:3000/api/auth/login -d '{}' -H 'Content-Type: application/json'` | `{ code: 4001, message: "..." }` |
| 5 | 日志文件正常写入 | 启动服务 → 执行业务操作 → `ls logs/` | access-YYYY-MM-DD.log / error-YYYY-MM-DD.log / business-YYYY-MM-DD.log（开发环境仅控制台输出） |
| 6 | production 环境不返回 detail | `NODE_ENV=production node server/src/app.js` → 触发错误 | response `detail` 字段为 `null` |
| 7 | business 日志包含预期字段 | 执行下单操作 → 检查 business 日志 | `{ action: 'order.create', userId, orderId, productId, result: 'success' }` |
| 8 | 手机号脱敏 | 登录失败日志 | `phone: '138****1234'`（仅后 4 位可见） |
| 9 | ESLint 无新增错误 | `npx eslint server/src/ --ext .js` | 0 errors |
| 10 | 现有测试全量通过 | `npx vitest run` | 10 files / 131 tests passed |

---

## 六、预估工时

| Phase | 内容 | 预估 |
|------|------|:--:|
| Phase 1 | 审计 grep（5 项扫描） | 30 min |
| Phase 2 | console.log → logger（2 处） | 10 min |
| Phase 3 | bare Error → AppError（1 处） | 5 min |
| Phase 4 | business 日志补全（14 处） | 45 min |
| Phase 5 | 敏感信息确认（2 项） | 10 min |
| Phase 6 | 文档更新（3 文件） | 15 min |
| 验证 | 10 项验证 | 30 min |
| **合计** | | **~2.5 h** |

> 编码迭代计划预估 1 天，实际因基础设施已在第 1 轮全部就绪，剩余审计+补漏工作约 2.5 小时。

---

## 七、依赖与前置

| 依赖 | 状态 | 说明 |
|------|:--:|------|
| 第 1~9 轮已完成 | ✅ | 核心闭环已打通 |
| winston / AppError / .env | ✅ | 第 1 轮已就绪 |
| 131 测试用例全绿 | ✅ | 10 文件全过 |
| 无硬阻塞项 | ✅ | 可立即开始 |

---

## 八、与后续轮次的衔接

| 后续轮次 | 本轮交付物对其价值 |
|------|------|
| 第 11 轮（缓存+性能） | business 日志覆盖后，可通过日志分析热点查询，指导缓存策略 |
| 第 12 轮（前端收尾） | 无直接依赖（前后端独立） |
| 第 13 轮（测试+CI+部署） | production .env 审计完成，可直接用于 PM2 部署验证 |
| 运维上线 | business 日志为生产环境排障提供关键信息 |

---

## 九、风险与注意事项

1. **日志性能影响** — winston JSON 格式化在生产环境有一定开销。本轮仅对关键操作（非查询）添加 business 日志，预计生产环境每秒 < 5 条 business 日志，可忽略不计。
2. **日志存储增长** — business 日志文件设置 `maxSize: '20m'` + `maxFiles: '30d'`，需在运维手册中标注定期归档策略。
3. **敏感信息泄露的误报** — `admin_phone` 出现在审计日志 API 中，这是管理后台功能设计意图（管理员需要知道谁操作了审计对象），不视为泄露。但需在 security-rules 文档中注明此设计决策。
4. **不要引入回归 Bug** — 所有改动均在日志/错误处理层，不影响业务逻辑。但仍需 `npx vitest run` 确认 131 用例全过。

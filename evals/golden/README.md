# Golden Dataset — 核心功能黄金标准

> 用途：评估 AI 是否能生成符合项目规范的、可直接运行的核心功能代码。
> 每个 Case 包含：场景描述 → 输入 Prompt → 预期行为 → 违规示例 → 通过标准。

---

## 评估维度速查

| 维度 | 检查项 | 依据 |
|------|--------|------|
| API | RESTful + 统一 `{code, message, data}` 格式 | rules/api-rules.md |
| DB | 参数化查询、无 `SELECT *`、分页含 `LIMIT/OFFSET` | rules/database-rules.md |
| SEC | 输入校验、参数化查询、无硬编码密钥 | rules/security-rules.md |
| ERR | 无空 catch、AppError 工厂函数、含 traceId | rules/error-handling-rules.md |
| FILE | 单文件 ≤ 500 行 | rules/file-rules.md |
| FUNC | 单函数 ≤ 80 行、参数 ≤ 5 个 | rules/function-rules.md |
| NAME | camelCase/PascalCase/kebab-case/snake_case 合规 | rules/coding-standards.md |
| UI | tokens.scss 设计令牌、8pt 间距、SCSS class 非内联 | rules/ui-rules.md |

---

### GOLD-001: 新增 Express API 端点（商品搜索）

- **评估维度**: API, DB, SEC, ERR, NAME, FUNC
- **严重程度**: critical

**场景描述**: 需要在现有 Express 5 层架构中新增一个 "按标题模糊搜索 + 按分类/成色筛选 + 按价格排序 + 分页" 的商品搜索端点。

**输入 Prompt**:
```
新增 GET /api/products/search 端点，支持：
- keyword（模糊搜索标题）
- category（精确匹配分类）
- condition（精确匹配成色）
- sortBy（price_asc / price_desc / created_at_desc，默认 created_at_desc）
- page / pageSize（分页，默认 1/20，上限 50）

返回统一格式 { code: 0, message: "success", data: { list: [...], total: N } }。
遵循 Express 5 层架构：routes → controllers → services → repository。
```

**预期行为**:
1. 在 `routes/product.js` 注册路由，绑定 `authMiddleware` + `validate(schema)` + `searchProducts` controller
2. Controller 只做参数提取和响应组装，调用 `productService.searchProducts()`
3. Service 层包含业务逻辑：构建 WHERE 子句、参数校验、结果组装
4. Repository 层封装 SQL：参数化查询 `WHERE title LIKE ?`、动态条件拼接、`COUNT(*)` 总数查询
5. SQL 无 `SELECT *`，明确列出字段
6. `LIMIT ? OFFSET ?` 使用 `parseInt` 转整数
7. 所有错误通过 `errors.js` 工厂函数抛出（`throw errors.badRequest(...)`）
8. 无 `console.log`，使用 `logger.info/warn/error`
9. Joi schema 在 `middleware/validate.js` 配套的 schema 文件中定义

**违规示例**:
- 在 route 文件中写业务逻辑（query 拼接在 route 里）
- Controller 中调用 `db.query()` 直接操作数据库
- SQL 中使用 `${keyword}` 字符串插值（非参数化）
- 返回格式 `{ success: true, items: [...] }`（非统一格式）
- 函数超过 80 行未拆分

**通过标准**: 代码符合 5 层架构分离；SQL 全量参数化；响应格式统一；ESLint 0 error。

---

### GOLD-002: 新增 uni-app 页面（评价列表页）

- **评估维度**: API, UI, NAME, FILE, FUNC
- **严重程度**: critical

**场景描述**: 需要新增一个"我的评价"页面，展示当前用户收到/发出的评价列表，含三维评分（沟通/守时/描述）+ 文字评价 + 评价人信息。

**输入 Prompt**:
```
新增 /pages/user/reviews 页面（已存在路由注册）：
- Tab 切换：收到的评价 / 发出的评价
- 每条评价显示：评价人头像+昵称、三维星级评分（只读）、文字评价、时间
- 支持分页加载（触底加载更多）
- 空状态使用 EmptyState 组件
- 样式遵循 tokens.scss 设计令牌 + 8pt 间距系统
```

**预期行为**:
1. 使用 Vue 3 Composition API（`<script setup>`）
2. 从 `@/api/review` 导入 `getReviewsByUser` API 函数
3. 分页逻辑：`page` ref + `hasMore` ref + `onReachBottom` 触底加载
4. 页码递增在 API 成功回调内执行（避免网络失败时跳过数据）
5. `hasMore` 使用 `list.length < total` 判断（非 `length < pageSize`）
6. 空状态：`<EmptyState v-if="list.length === 0 && !loading" />`
7. 样式使用 SCSS class，无内联 `style="..."`（除动态值外）
8. 颜色/间距引用 `tokens.scss` 变量（`$color-text-primary`、`$spacing-md` 等）
9. StarRating 组件使用 `:model-value` prop + `readonly` 属性
10. 文件 ≤ 500 行

**违规示例**:
- 使用 Options API（`export default { data() {...} }`）
- 内联样式泛滥（`style="color: red; margin: 10px"`）
- 翻页前 `page++` 在 try 块外（网络失败时跳过数据）
- 没有 EmptyState 空状态处理
- 硬编码颜色值而非使用 tokens.scss 变量

**通过标准**: ESLint 0 error；build 成功；页面可在微信开发者工具中正常渲染。

---

### GOLD-003: MySQL 参数化查询（带事务的复杂写入）

- **评估维度**: DB, SEC, ERR, FUNC
- **严重程度**: critical

**场景描述**: 需要实现"确认订单收货"功能——更新订单状态为 completed、给买卖双方各 +2 信誉分、写入信誉变动日志。三个操作必须在同一事务中完成。

**输入 Prompt**:
```
实现 confirmOrder(orderId, userId)：
1. 查询订单，验证状态为 'met'（已面交）
2. 验证 userId 是订单的 buyer_id
3. 开启事务
4. 使用 FOR UPDATE 锁定订单行
5. 更新订单状态为 'completed'
6. 给买家、卖家各 +2 信誉分（credit_score + 2）
7. 写入两条 credit_change_log 记录
8. 提交事务（失败则回滚）
9. 返回更新后的订单信息
```

**预期行为**:
1. Repository 层方法接受可选 `conn` 参数（传入时使用事务连接）
2. `findById(id, conn)` 使用 `SELECT ... FOR UPDATE` 锁定行
3. 使用 `db.transaction(async (conn) => { ... })` 包裹事务逻辑
4. SQL 全量参数化：`db.query('UPDATE orders SET status = ? WHERE id = ?', ['completed', id])`
5. 无 `SELECT *`：明确列出需要的字段
6. 错误使用 AppError 工厂函数（`throw errors.orderStatusConflict(...)`）
7. credit_score 更新使用 `SET credit_score = credit_score + 2`（数据库原子操作，非读-改-写）
8. 函数 ≤ 80 行

**违规示例**:
- 读-改-写模式更新信誉分（先 SELECT credit_score，再 +2，再 UPDATE——存在竞态条件）
- 事务内使用 `pool.query()` 而非 `conn.query()`
- 无 FOR UPDATE（TOCTOU 漏洞）
- 未处理事务回滚（部分操作成功、部分失败）

**通过标准**: 3 个写操作在同一事务中原子执行；FOR UPDATE 锁定行；SQL 参数化；并发测试无竞态条件。

---

### GOLD-004: Joi 参数校验 Middleware

- **评估维度**: API, SEC, ERR, NAME
- **严重程度**: critical

**场景描述**: 为已存在的 POST /api/orders 端点新增请求体校验 schema。

**输入 Prompt**:
```
为 POST /api/orders 编写 Joi 校验 schema：
- product_id: required, positive integer
- message: optional, string, max 200 chars
- meet_place: optional, string, max 100 chars

校验失败返回 4001 + 具体字段错误信息。
```

**预期行为**:
1. Schema 定义在 `server/src/middleware/validate.js` 的 schemas 对象中
2. 使用 `Joi.object({...})` 定义
3. `product_id: Joi.number().integer().positive().required()`
4. `message: Joi.string().max(200).optional().default('')`
5. 校验中间件使用 `abortEarly: true`（首个错误即返回）
6. 错误消息包含具体字段名（如 `"product_id" is required`）
7. 校验后将 `req.body` 替换为 Joi 清洗后的值（`stripUnknown: true`）
8. 路由注册：`router.post('/', authMiddleware, validate('createOrder'), controller.create)`

**违规示例**:
- Schema 写在 controller 或 service 中（非 middleware 层）
- 使用 `if (!product_id) { return res.json({ code: 4001 }) }` 手动校验
- `abortEarly: false` 返回所有错误（与项目规范 `abortEarly: true` 不一致）

**通过标准**: 无效输入返回 400/4001；有效输入通过校验进入 controller；schema 在 middleware 层集中管理。

---

### GOLD-005: 错误处理 — AppError 工厂函数使用

- **评估维度**: ERR, NAME
- **严重程度**: high

**场景描述**: 需要处理"商品不存在"错误场景，使用项目统一的 AppError 体系。

**输入 Prompt**:
```
在 productService.update() 中，当 findById 返回 null 时，抛出适当的错误。
错误码：2001（商品不存在），HTTP 状态码：404。
```

**预期行为**:
1. `throw errors.notFound('商品不存在')` — 使用 `errors.js` 中的工厂函数
2. NOT `throw new Error('商品不存在')` — 禁止裸 Error
3. NOT `throw new AppError(2001, 404, '商品不存在')` — 使用工厂函数而非直接 new
4. 错误码 2001 与 `docs/技术架构文档.md` 中的错误码表一致
5. `notFound()` 工厂函数已在 `errors.js` 中定义为 `(msg) => new AppError(2001, 404, msg)`

**违规示例**:
- `throw new Error('商品不存在')` — 无错误码、无 HTTP 状态码、无 traceId
- `res.json({ code: 2001, message: '商品不存在' })` 在 service 中直接发响应 — 破坏 5 层分离
- `catch (e) {}` — 空 catch 块吞掉错误

**通过标准**: 所有业务错误使用 `errors.xxx()` 工厂函数；无 `throw new Error` 在业务代码中；无空 catch。

---

### GOLD-006: uni-app 组件 — 遵循设计令牌的 SCSS

- **评估维度**: UI, NAME, FILE
- **严重程度**: high

**场景描述**: 新增一个"用户信誉分徽章"组件，在不同信誉分区间显示不同颜色和图标。

**输入 Prompt**:
```
创建 CreditBadge.vue 组件：
- 接收 props: score (number, 0-100)
- score >= 80: 绿色，显示"优秀"
- score >= 60: 黄色，显示"良好"
- score >= 30: 橙色，显示"一般"
- score < 30: 红色，显示"较差"
- 尺寸: sm (small) / md (medium) 两种
```

**预期行为**:
1. 颜色使用 tokens.scss 变量（如 `$color-success`、`$color-warning`）或语义化 SCSS 变量
2. 间距使用 8pt 系统（`8rpx` / `16rpx` / `24rpx`）
3. 无内联 `style` 属性（除动态颜色值外，使用 computed 生成 class）
4. 使用 `<script setup>` + props with defaults
5. Props 类型定义明确：`defineProps<{ score: number; size?: 'sm' | 'md' }>()`
6. 文件 ≤ 200 行（组件简单）
7. 类名使用 kebab-case（`credit-badge`、`credit-badge--sm`）

**违规示例**:
- `style="background-color: #4CAF50"` — 硬编码颜色
- `style="padding: 10px; margin: 15px"` — 非 8pt 间距系统
- 使用 Options API

**通过标准**: 颜色/间距引用 tokens.scss；无内联样式；build 成功。

---

### GOLD-007: API 封装模块（前端）

- **评估维度**: API, NAME, FUNC
- **严重程度**: high

**场景描述**: 为新增的"信誉分变动记录"功能编写前端 API 封装模块。

**输入 Prompt**:
```
在 miniprogram/src/api/credit.js 中新增 getCreditLogs 函数：
- GET /api/credits/logs?page=1&pageSize=20
- 返回 { code, message, data: { list, total } }
- 使用项目已有的 request() 封装
```

**预期行为**:
1. 导入 `request` from `@/utils/request`（项目统一的 HTTP 封装）
2. 函数签名：`export function getCreditLogs({ page = 1, pageSize = 20 } = {})`
3. 使用解构默认参数，而非 `params.page || 1`
4. `return request.get('/credits/logs', { page, pageSize })`
5. 函数 ≤ 20 行
6. 文件名 kebab-case：`credit.js`
7. 函数名 camelCase：`getCreditLogs`

**违规示例**:
- 直接使用 `uni.request()` 绕过项目 request 封装
- 在 API 模块中处理业务逻辑（数据转换、错误处理）
- 硬编码 API base URL

**通过标准**: 使用项目统一的 request 封装；纯 API 调用无业务逻辑。

---

### GOLD-008: Repository 分页查询模式

- **评估维度**: DB, FUNC, NAME
- **严重程度**: high

**场景描述**: 为 review 表实现"查询某用户收到的评价列表"，含分页 + 评价人信息 JOIN。

**输入 Prompt**:
```
实现 reviewRepo.findByReviewee(userId, { page, pageSize })：
- 查询 reviewee_id = userId 的评价
- JOIN users 表获取评价人昵称和头像
- 按 created_at DESC 排序
- 分页：page 从 1 开始，pageSize 上限 50
- 返回 { list: [...], total: N }
```

**预期行为**:
1. `parseInt(page, 10)` 和 `parseInt(pageSize, 10)` 整数转换
2. `Math.max(1, page)` 和 `Math.min(50, Math.max(1, pageSize))` 边界保护
3. SQL 明确字段列表：`r.id, r.order_id, r.communication_score, r.punctuality_score, r.accuracy_score, r.comment, r.created_at, u.nickname AS reviewer_nickname, u.avatar AS reviewer_avatar`
4. `FROM reviews r JOIN users u ON r.reviewer_id = u.id`
5. `WHERE r.reviewee_id = ?` 参数化
6. 两条 SQL：COUNT 查询 + 数据查询（使用同一 WHERE 条件）
7. `LIMIT ? OFFSET ?` 参数化
8. `const [rows] = await db.query(countSql, params)` — 正确解构 mysql2 返回值

**违规示例**:
- `SELECT * FROM reviews ...` — 违反 DB 规范
- `LIMIT ${pageSize} OFFSET ${offset}` — 字符串插值非参数化
- 未 parseInt 的 pageSize 传入（字符串 "20" 被 mysql2 引用导致语法错误）
- 单个 SQL 查全量数据再 JS slice 分页
- N+1 查询：先查 reviews 再循环逐条查 users

**通过标准**: SQL 参数化；整数转换；2 条 SQL（count + data）；无 SELECT *；无 N+1。

---

## 评估记录模板

```markdown
## 评估记录

| 日期 | 模型 | GOLD-001 | GOLD-002 | GOLD-003 | GOLD-004 | GOLD-005 | GOLD-006 | GOLD-007 | GOLD-008 | 通过率 |
|------|------|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| YYYY-MM-DD | model-name | ✅ | ✅ | ⚠️ | ✅ | ❌ | ✅ | ✅ | ✅ | 75% |
```

每个 Case 的判定：✅ 通过 / ⚠️ 部分通过（有 minor 违规）/ ❌ 失败（有 major 违规）。

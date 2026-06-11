# Prompt Evaluation Dataset — Prompt 稳定性评估

> 用途：评估 Prompt 修改后是否仍能产出符合预期的代码；或在切换 AI 模型时对比输出质量。
> 核心问题：**同样的 Prompt 给不同模型 / Prompt 微调后，输出质量是否一致或提升？**

---

## 评估方法

1. 选择 3-5 个 Prompt Case
2. 在当前 Claude Code 会话中执行
3. 检查输出是否通过评估维度
4. 记录各维度通过/失败结果
5. 对比历史记录，判断质量趋势

---

### PROMPT-001: 新增 Express API 端点（标准 CRUD）

- **评估维度**: API, DB, SEC, ERR, NAME, FUNC, FILE
- **严重程度**: critical
- **适用场景**: 每次新增后端 API 端点时评估

**Prompt**:
```
在 Server 端新增"信誉分变动记录查询"功能：

1. GET /api/credits/logs — 查询当前用户的信誉分变动记录
   - 支持分页：page (默认1) / pageSize (默认20, 上限50)
   - 返回：变动类型、分值变化、变动原因、时间
   - 按时间倒序排列

2. 遵循 Express 5 层架构：routes → controllers → services → repository

3. 所有 SQL 使用参数化查询，禁止 SELECT *
4. 错误处理使用 errors.js 工厂函数
5. 遵循 rules/ 下的全部编码规范
```

**评估矩阵**:

| 维度 | 检查点 | 权重 |
|------|--------|:--:|
| 5 层分离 | route/controller/service/repository 职责清晰 | 20% |
| SQL 安全 | 参数化查询、无 `SELECT *`、`LIMIT ? OFFSET ?` | 20% |
| 错误处理 | AppError 工厂函数、无空 catch | 15% |
| 命名规范 | 文件名 kebab-case、函数 camelCase、DB snake_case | 10% |
| 分页逻辑 | parseInt + 边界保护 + COUNT 查询 | 15% |
| 代码长度 | 每个文件 ≤ 500 行、每个函数 ≤ 80 行 | 10% |
| 日志 | winston logger、无 console.log | 5% |
| 校验 | Joi schema 或 service 层参数校验 | 5% |

---

### PROMPT-002: 新增 uni-app 列表页（带筛选 + 分页）

- **评估维度**: UI, API, NAME, FUNC, FILE
- **严重程度**: critical
- **适用场景**: 每次新增前端列表页时评估

**Prompt**:
```
在 miniprogram 中新增"我卖出的订单"页面：

1. 路径：/pages/order/sold
2. Tab 切换：全部 / 待确认 / 待面交 / 已完成 / 已取消
3. 每条显示：商品封面图、标题、价格、交易对方昵称、订单状态、时间
4. 支持触底加载更多
5. 空状态使用 EmptyState 组件
6. 样式遵循 tokens.scss + 8pt 间距系统
7. 使用 Vue 3 Composition API（<script setup>）

遵循 rules/ 下的全部编码规范。
```

**评估矩阵**:

| 维度 | 检查点 | 权重 |
|------|--------|:--:|
| Composition API | `<script setup>` 语法、ref/reactive/computed 正确使用 | 20% |
| 分页逻辑 | page 在成功回调内递增、hasMore 用 list.length < total | 15% |
| 空状态 | EmptyState 组件 + loading 判断防闪烁 | 10% |
| 设计令牌 | colors/spacing 引用 tokens.scss、无硬编码颜色 | 15% |
| 内联样式 | 无内联 style（动态值除外） | 10% |
| API 封装 | 使用 @/api/ 模块、非直接 uni.request | 10% |
| 代码长度 | 文件 ≤ 500 行、函数 ≤ 80 行 | 10% |
| 触底加载 | onReachBottom 防重复请求（loading 锁） | 10% |

---

### PROMPT-003: Bug 修复 — 前后端字段名不匹配

- **评估维度**: API, NAME, ERR
- **严重程度**: critical
- **适用场景**: Bug 修复类 Prompt 的评估

**Prompt**:
```
Bug 报告：用户点击"我想要"按钮提交订单时报错 ""product_id" is required"。

前端代码：
  api.createOrder({ productId: product.value.id })

后端 Joi schema（routes/order.js）：
  product_id: Joi.number().integer().required()

请修复这个 Bug。
```

**评估矩阵**:

| 维度 | 检查点 | 权重 |
|------|--------|:--:|
| 根因定位 | 明确指出 camelCase vs snake_case 不匹配 | 25% |
| 修复方案 | 前端字段名改为 product_id（非改后端） | 25% |
| 最小改动 | 只改必要的地方、不重构无关代码 | 20% |
| 系统性排查 | 同一文件中是否还有其他 camelCase/snake_case 不一致 | 15% |
| 验证说明 | 提供测试方法 | 15% |

---

### PROMPT-004: 添加 Joi 参数校验

- **评估维度**: API, SEC, ERR, NAME
- **严重程度**: high
- **适用场景**: 参数校验类 Prompt 的评估

**Prompt**:
```
为 POST /api/reviews 端点添加请求体校验：

- order_id: required, positive integer
- communication_score: required, 1-5 integer
- punctuality_score: required, 1-5 integer
- accuracy_score: required, 1-5 integer
- comment: optional, string, max 200 chars
- reviewee_id: required, positive integer

校验失败返回 400/4001 + 具体字段错误信息。
```

**评估矩阵**:

| 维度 | 检查点 | 权重 |
|------|--------|:--:|
| Joi 语法 | `.required()` / `.integer().min(1).max(5)` 正确 | 20% |
| 错误消息 | 包含具体字段名 | 15% |
| abortEarly | `true`（项目规范） | 10% |
| stripUnknown | `true`（清洗多余字段） | 10% |
| 注册方式 | schema 在 middleware/validate.js 集中管理 | 15% |
| 路由绑定 | `router.post('/', authMiddleware, validate('createReview'), ...)` | 10% |
| 边界处理 | 0 和负数被拒绝（positive） | 10% |
| 默认值 | optional 字段有合理默认值 | 10% |

---

### PROMPT-005: 编写 Service 层单元测试

- **评估维度**: 测试（test-rules.md）、ERR
- **严重程度**: high
- **适用场景**: 测试编写类 Prompt 的评估

**Prompt**:
```
为 server/src/services/credit.js 的 getCreditLogs 函数编写单元测试：

- 测试正常分页查询返回 { list, total }
- 测试空记录返回 { list: [], total: 0 }
- 测试 page 为 0 或负数时的回退行为
- 测试 pageSize 超上限 50 时的截断行为

使用项目已有的 vitest + 真实测试数据库模式。
```

**评估矩阵**:

| 维度 | 检查点 | 权重 |
|------|--------|:--:|
| 测试框架 | vitest（非 jest）、globals 模式一致 | 15% |
| DB 隔离 | setupTestDb / teardownTestDb 正确使用 | 20% |
| 用例覆盖 | 正常 + 空 + 边界 + 异常 至少 4 条 | 25% |
| 断言精确 | 断言具体值而非 `toBeDefined()` | 15% |
| 数据准备 | 测试数据在 beforeEach 中创建、afterEach 中清理 | 15% |
| 命名规范 | describe/it 描述清晰 | 10% |

---

### PROMPT-006: 复杂业务流程 — 订单状态机

- **评估维度**: API, DB, SEC, ERR, FUNC
- **严重程度**: critical
- **适用场景**: 复杂业务逻辑类 Prompt 的评估

**Prompt**:
```
实现"卖家标记面交完成"功能：

1. 验证订单状态为 'accepted'（已接受）
2. 验证操作者是订单的 seller_id
3. 验证订单未超过面交时间（24 小时内未操作自动取消？不用管，那是定时任务的事）
4. 更新订单状态为 'met'（已面交）
5. 发送系统通知给买家："卖家已标记面交完成，请确认收货"

遵循 rules/ 下的全部规范，使用 Express 5 层架构。
```

**评估矩阵**:

| 维度 | 检查点 | 权重 |
|------|--------|:--:|
| 权限校验 | 验证 seller_id === operatorId | 15% |
| 状态机 | 只允许 accepted → met（非 pending → met 等跳跃） | 20% |
| 事务安全 | FOR UPDATE 或 transaction 防竞态 | 15% |
| 错误码 | 使用 errors.js 工厂函数（orderStatusConflict 等） | 10% |
| 通知 | 通过 IM REST API 发送系统通知（非遗漏） | 10% |
| 函数长度 | ≤ 80 行，复杂逻辑拆分 | 10% |
| SQL 安全 | 参数化查询 | 10% |
| 日志 | logger.info/warn 记录关键操作 | 10% |

---

## 评估记录模板

```markdown
## Prompt 评估记录

### 基线 (YYYY-MM-DD, 模型: claude-sonnet-4-6)

| Case | API | DB | SEC | ERR | UI | NAME | FUNC | FILE | 综合 |
|------|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| PROMPT-001 | ✅ | ✅ | ✅ | ✅ | N/A | ✅ | ✅ | ✅ | ✅ |
| PROMPT-002 | ✅ | N/A | N/A | ✅ | ✅ | ✅ | ⚠️ | ⚠️ | ⚠️ |
| PROMPT-003 | ✅ | N/A | N/A | ✅ | N/A | ✅ | ✅ | ✅ | ✅ |
| PROMPT-004 | ✅ | N/A | ✅ | ✅ | N/A | ✅ | ✅ | ✅ | ✅ |
| PROMPT-005 | N/A | N/A | N/A | ✅ | N/A | ✅ | ✅ | ✅ | ✅ |
| PROMPT-006 | ✅ | ✅ | ✅ | ✅ | N/A | ✅ | ⚠️ | ✅ | ⚠️ |

**通过率**: 4/6 完全通过, 2/6 部分通过 (综合 83%)

### 对比 (YYYY-MM-DD, 模型: claude-opus-4-8)

| Case | API | DB | SEC | ERR | UI | NAME | FUNC | FILE | 综合 |
|------|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| PROMPT-001 | ✅ | ✅ | ✅ | ✅ | N/A | ✅ | ✅ | ✅ | ✅ |
| ... | | | | | | | | | |

**结论**: [模型对比结论]
```

## 使用建议

1. **模型切换时**：运行全部 6 个 Case，对比新旧模型在各维度的表现
2. **CLAUDE.md 修改后**：运行 PROMPT-001（后端）和 PROMPT-002（前端），确保规范变更不影响代码生成质量
3. **新增 rules/ 规范后**：选择与该规范相关的 Case 运行
4. **月度质量审计**：随机选取 3 个 Case，检查 AI 输出质量是否退化

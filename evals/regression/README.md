# Regression Dataset — 回归测试评估

> 用途：基于项目已知 Bug（`memory/known-bugs.md`），评估 AI 是否重犯相同模式的错误。
> 核心问题：**这个 Bug 模式修复后，AI 在类似场景下还会犯同样的错误吗？**

---

## 回归 Case 总览（按 Bug 模式分类）

| Case | Bug 模式 | 对应原始 Bug | 严重程度 |
|------|---------|------------|:--:|
| REG-001 | camelCase/snake_case 字段名不匹配 | BUG-025, BUG-029 | critical |
| REG-002 | req.query 参数未 parseInt 导致 SQL 错误 | BUG-006, BUG-026 | critical |
| REG-003 | pool.execute() vs pool.query() LIMIT 问题 | BUG-005 | critical |
| REG-004 | 数值类型属性访问 .length | BUG-023 | critical |
| REG-005 | Vue 3 v-model 协议不匹配 | BUG-028 | critical |
| REG-006 | tabBar 页面跳转方式错误 | BUG-034, BUG-035 | critical |
| REG-007 | 共享状态修改后仍读取旧值 | BUG-036, BUG-037 | high |
| REG-008 | JWT payload 字段名定义与读取不一致 | BUG-001 | critical |
| REG-009 | 空 catch 块吞错误 | BUG-002, A5-007~009 | high |
| REG-010 | 图片上传 preselected files 被丢弃 | BUG-007 | high |
| REG-011 | 订单状态机缺少状态校验 | BUG-016 | high |
| REG-012 | 事务缺少 FOR UPDATE / 使用错误连接 | BUG-018, BUG-005 | critical |

---

### REG-001: 前后端字段名格式一致性

- **评估维度**: API, NAME
- **严重程度**: critical
- **来源**: BUG-025 (`productId` → 后端要求 `product_id`)、BUG-029 (`orderId` → 后端要求 `order_id`)

**场景描述**: 新增一个前端 API 调用，传递参数给后端 Joi 校验的端点。

**输入 Prompt**:
```
前端调用 POST /api/reports 提交举报：
- 订单 ID：order_id（后端 Joi 定义）
- 举报类型：report_type（后端 Joi 定义）
- 描述文字：description（后端 Joi 定义）
```

**预期行为**:
1. 前端发送的字段名与后端 Joi schema 完全一致（snake_case）
2. `createReport({ order_id: order.id, report_type: 'fake', description: '...' })`
3. NOT `createReport({ orderId: order.id, reportType: 'fake', description: '...' })`
4. 查阅后端 Joi schema 确认字段名后再写前端代码

**违规示例**:
- 前端使用 camelCase（`orderId`、`reportType`）
- 不查后端 schema 直接凭"惯例"命名

**被测技能**: 跨层字段名一致性检查

---

### REG-002: LIMIT/OFFSET 参数 parseInt

- **评估维度**: DB, API
- **严重程度**: critical
- **来源**: BUG-006 (product.findBySeller 字符串 pageSize)、BUG-026 (6 个 repo 方法遗漏)

**场景描述**: 新增一个带分页的 Repository 方法。

**输入 Prompt**:
```
在 reportRepo 中实现 listWithFilters({ page, pageSize, status })：
- 分页查询举报列表
- page 默认 1，pageSize 默认 20，上限 50
```

**预期行为**:
1. `const p = Math.max(1, parseInt(page, 10) || 1)`
2. `const ps = Math.min(50, Math.max(1, parseInt(pageSize, 10) || 20))`
3. 对 page 和 pageSize 都做 parseInt（不遗漏任何一个）
4. 在函数开头统一转换，然后在 SQL 中使用转换后的变量
5. `const offset = (p - 1) * ps`

**违规示例**:
- 直接使用 `page` 和 `pageSize`（字符串类型）传入 SQL
- 只 parseInt pageSize 不转换 page
- 使用 `Number(page)` 而非 `parseInt(page, 10)`（`Number('')` = 0 而非 NaN → `|| 1` 回退）

---

### REG-003: db.query() vs db.execute() — mysql2 协议差异

- **评估维度**: DB
- **严重程度**: critical
- **来源**: BUG-005（pool.execute() 不支持 LIMIT/OFFSET 占位符）

**场景描述**: 在 `server/src/models/db.js` 中新增一个数据库辅助方法。

**输入 Prompt**:
```
在 db.js 中新增 paginate(table, { page, pageSize, where, orderBy }) 通用分页方法。
使用连接池方法执行 SQL。
```

**预期行为**:
1. 使用 `pool.query(sql, params)` — 客户端参数化转义
2. NOT `pool.execute(sql, params)` — 服务端 PREPARE 协议不支持 LIMIT/OFFSET
3. 如果创建事务连接，使用 `conn.query(sql, params)` 而非 `conn.execute(sql, params)`
4. JSDoc 注释注明使用 `query()` 而非 `execute()` 的原因

**违规示例**:
- `pool.execute(sql, params)` — LIMIT/OFFSET 会报 "Incorrect arguments to mysqld_stmt_execute"
- JSDoc 写 `conn.execute()` 但实际用 `conn.query()`（注释与实现不一致）

---

### REG-004: 数值类型字段调用 .length/.toString 等字符串方法

- **评估维度**: API, FUNC
- **严重程度**: critical
- **来源**: BUG-023（userId 是 INT，`userId.length` = `undefined` 导致 UserSig 非法）

**场景描述**: 需要根据数据库 INT 类型字段生成字符串标识符。

**输入 Prompt**:
```
生成 UserSig 时，userId 从数据库 users 表取出（INT 类型 AUTO_INCREMENT）。
需要将其嵌入字符串模板中，并获取其长度。
```

**预期行为**:
1. `const userIdStr = String(userId)` 显式转为字符串
2. 后续所有引用使用 `userIdStr` 而非 `userId`
3. `userIdStr.length` 和模板 `${userIdStr}` 正常工作
4. JSON 序列化中 `identifier: userIdStr`（字符串类型）

**违规示例**:
- `userId.length` → INT 无 .length 属性 → `undefined`
- 模板字符串 `${userId}` 隐式转换可能不一致
- `JSON.stringify({ identifier: userId })` → 数字类型而非字符串

---

### REG-005: Vue 3 Component v-model 协议

- **评估维度**: UI, NAME
- **严重程度**: critical
- **来源**: BUG-028（StarRating 用 `value` prop + `change` event，Vue 3 v-model 绑定 `modelValue` + `update:modelValue`）

**场景描述**: 创建一个支持 `v-model` 的自定义表单组件。

**输入 Prompt**:
```
创建 TagSelector.vue 组件：
- 支持 v-model 绑定选中的标签数组
- 点击标签切换选中/取消
- 父组件使用 v-model="selectedTags"
```

**预期行为**:
1. Props: `modelValue`（非 `value`）— Vue 3 `v-model` 默认绑定
2. Emit: `update:modelValue`（非 `change` 或 `input`）
3. 父组件：`<TagSelector v-model="selectedTags" />`
4. 只读展示场景：`:model-value="tags"`（非 `:value="tags"`）
5. 如需多个 v-model：`v-model:propName` → prop `propName` + emit `update:propName`

**违规示例**:
- Props 用 `value`、Emit 用 `change` → 父组件 v-model 收不到数据
- Emit 用 `input` → Vue 2 遗留写法，Vue 3 不兼容

---

### REG-006: 微信小程序 tabBar 页面导航

- **评估维度**: UI, API
- **严重程度**: critical
- **来源**: BUG-034（navigateTo tabBar 页报错）、BUG-035（switchTab 无历史栈）

**场景描述**: 需要从非 tabBar 页面跳转到 tabBar 页面并传递参数。

**输入 Prompt**:
```
从商品详情页跳转到发布页进行编辑：
- 发布页 /pages/product/publish 是 tabBar 页面
- 需要传递商品 ID 以便进入编辑模式
```

**预期行为**:
1. 识别目标页为 tabBar 页 → 使用 `uni.switchTab`
2. 参数通过 Pinia store / 全局状态传递（非 URL query，switchTab 不支持）
3. 目标页在 `onShow` 中检查是否有待处理的参数
4. 消费后立即清除（防止下次 onShow 时误触发）
5. 编辑保存后导航：`uni.navigateTo` 到商品详情页（非 tabBar 页，可 navigateTo）
6. 不要 `uni.navigateBack()` — switchTab 无历史栈

**违规示例**:
- `uni.navigateTo({ url: '/pages/product/publish?id=123' })` — 报错
- `uni.switchTab({ url: '/pages/product/publish?id=123' })` — query 被忽略
- 编辑保存后 `uni.navigateBack()` — "cannot navigate back at first page"

---

### REG-007: 共享状态修改后对旧值的依赖

- **评估维度**: FUNC, ERR
- **严重程度**: high
- **来源**: BUG-036（resetForm 清零 editId 后拼接 `id=null` URL）、BUG-037（编辑状态未清理导致发布页被永久覆盖）

**场景描述**: 一个函数中先修改共享状态，再读取该状态用于后续操作。

**输入 Prompt**:
```
提交编辑成功后：
1. 重置表单状态（resetForm 清空 editId、form 等）
2. 跳转到商品详情页（URL 需要 editId）
```

**预期行为**:
1. **先保存到局部变量**：`const targetId = editId.value`
2. **再调用状态修改函数**：`resetForm()`
3. **最后用局部变量**：`uni.navigateTo({ url: \`/pages/product/detail?id=${targetId}\` })`
4. 原则：调用任何可能修改 `this.xxx` / `xxx.value` 的函数前，将需要的值提取到局部变量
5. 同理适用于：`deleteItem(id)` → 先 `const idToDelete = currentId.value` 再操作

**违规示例**:
- `resetForm()` → `uni.navigateTo({ url: \`/pages/product/detail?id=${editId.value}\` })` — editId 已被 reset 为 null
- `list.splice(index, 1)` → `const item = list[index]` — index 处已是下一项

---

### REG-008: JWT/jwt 签名与验证 payload 字段名一致

- **评估维度**: SEC, API, NAME
- **严重程度**: critical
- **来源**: BUG-001（签发用 `tv`，验证读 `payload.version`）

**场景描述**: 修改 JWT payload 结构时，签发端和验证端分别修改。

**输入 Prompt**:
```
在 JWT payload 中新增 token_version 字段用于防伪造：
- 签发时：jwt.sign({ sub, role, tv: user.token_version }, ...)
- 验证时：需要读取并比对
```

**预期行为**:
1. 签发端和验证端使用**完全相同的字段名**
2. 如签发用 `tv`，验证读 `payload.tv`（非 `payload.tokenVersion`、`payload.version` 等）
3. 建议：定义常量 `JWT_PAYLOAD_KEYS = { TOKEN_VERSION: 'tv' }` 并在两端引用
4. 测试覆盖：修改 token_version 后验证 1003 是否正确触发

**违规示例**:
- 签发 `{ tv: 5 }`，验证读 `payload.tokenVersion`
- 签发 `{ tokenVersion: 5 }`，验证读 `payload.tv`
- 字段名不统一常量，两处各自写字符串

---

### REG-009: 空 catch 块

- **评估维度**: ERR
- **严重程度**: high
- **来源**: BUG-002（health check 空 catch）、A5-007~009（notification/App/chat 空 catch）

**场景描述**: 写一个 try/catch，catch 块的错误不影响主流程。

**输入 Prompt**:
```
在页面 onShow 中静默更新未读消息数——失败不阻塞页面渲染。
```

**预期行为**:
1. 必须输出日志：`console.warn('更新未读消息数失败', err)` 或 `logger.warn(...)`
2. **至少 1 行日志记录** — 满足 `error-handling-rules` "禁止空 catch" 要求
3. 日志级别：warn 或 debug（非 error，因为不影响主流程）
4. 不弹出 toast（静默失败）

**违规示例**:
- `catch (e) {}` — 完全空块
- `catch (e) { return }` — 无日志
- `catch { /* 忽略 */ }` — 无日志

---

### REG-010: 图片上传流程 — 已选文件复用

- **评估维度**: UI, API
- **严重程度**: high
- **来源**: BUG-007（ImageUploader 已选文件被 chooseAndUpload 丢弃，用户需选两次图）

**场景描述**: 封装一个"选择 + 上传"函数，需要支持传入已选文件列表（跳过重复选择）。

**输入 Prompt**:
```
实现 chooseAndUpload(maxCount) 函数：
- 选择图片（uni.chooseImage）
- 上传到 COS
- 返回 URL 数组
- 某些场景下用户已经选好了文件（tempFiles），应直接上传而非重新选择
```

**预期行为**:
1. 函数签名包含可选参数：`chooseAndUpload(maxCount, { preselectedFiles } = {})`
2. `preselectedFiles` 传入时跳过 `uni.chooseImage()`，直接校验 + 上传
3. 仍然做格式/大小校验（防止非图片或超大文件）
4. 不传入 `preselectedFiles` 时行为不变（完整选择 + 上传流程）

**违规示例**:
- 函数强制调用 `uni.chooseImage()`（重新打开相册）
- 传入已选文件后不做校验就直接上传
- 已选文件被忽略、始终重新选择

---

### REG-011: 状态机校验 — 遗漏的终态

- **评估维度**: API, DB, ERR
- **严重程度**: high
- **来源**: BUG-016（update 未检查 deleted 状态，只检查 sold/frozen）

**场景描述**: 商品状态有 `active/sold/frozen/deleted` 四种，编辑操作需验证商品处于可编辑状态。

**输入 Prompt**:
```
实现 productService.update() 的状态校验：
- sold（已售出）不可编辑
- frozen（已冻结）不可编辑
```

**预期行为**:
1. 校验**所有不可编辑的状态**：`['sold', 'frozen', 'deleted'].includes(product.status)`
2. 考虑终态：deleted（软删除）的商品不应被编辑
3. 使用数组 `includes()` 而非多条件 `||`（更易维护、不易遗漏）
4. 错误消息明确告知当前状态：`"商品状态为 ${product.status}，无法编辑"`

**违规示例**:
- `if (product.status === 'sold')` — 只检查 sold，遗漏 frozen 和 deleted
- `if (product.status !== 'active')` — 可能遗漏新状态（如 'pending_review'）
- 使用白名单（只允许 active）比黑名单（排除 sold/frozen/deleted）更安全

---

### REG-012: 事务使用 — FOR UPDATE + 正确连接

- **评估维度**: DB, ERR
- **严重程度**: critical
- **来源**: BUG-018（TOCTOU 竞态条件）、BUG-005（事务内用 pool.query 而非 conn.query）

**场景描述**: 实现"取消订单 + 恢复商品状态 + 退款"事务操作。

**输入 Prompt**:
```
实现 cancelOrder(orderId, userId)：
- 验证订单状态为 'pending'（待确认）
- 更新订单状态为 'cancelled'
- 恢复商品状态为 'active'
- 三者原子操作
```

**预期行为**:
1. 使用 `db.transaction(async (conn) => { ... })` 开启事务
2. 事务内所有查询使用 `conn.query(sql, params)`（非 `pool.query()`）
3. `SELECT ... FOR UPDATE` 锁定订单行和商品行
4. 状态验证在 FOR UPDATE 锁定之后
5. catch 块中不手动 `conn.rollback()` (transaction helper 自动处理)
6. 事务成功自动 commit、失败自动 rollback

**违规示例**:
- 先 SELECT 查状态（无锁）→ 判断 → UPDATE（TOCTOU）
- 事务内 `db.query()` 而非 `conn.query()` — 绕过事务连接
- `await pool.query('START TRANSACTION')` 手动管理 — 使用 transaction helper
- 事务内 throw → 未回滚（依赖 transaction helper）

---

## 评估记录模板

```markdown
| 日期 | 模型 | REG-001 | REG-002 | REG-003 | REG-004 | REG-005 | REG-006 | REG-007 | REG-008 | REG-009 | REG-010 | REG-011 | REG-012 | 通过率 |
|------|------|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| YYYY-MM-DD | model | ✅ | ✅ | ⚠️ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠️ | ✅ | 75% |
```

**判定标准**：
- ✅ 通过：代码无该 Bug 模式
- ❌ 失败：代码重犯该 Bug 模式
- ⚠️ 部分：有个别违规但非系统性

**使用方式**：选择 3-5 个 Case 作为 Prompt，检查 AI 输出是否避免了已知 Bug 模式。优先选 critical 级别的 Case。

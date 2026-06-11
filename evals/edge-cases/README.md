# Edge Case Dataset — 边界场景评估

> 用途：评估 AI 在边界条件、异常场景下的代码鲁棒性。
> 边界 Case 的核心问题是：**正常流程写对了，异常流程处理了吗？**

---

### EDGE-001: 空列表状态处理

- **评估维度**: UI, ERR
- **严重程度**: high

**场景描述**: 商品列表页首次加载返回空结果时，应展示引导式空状态，而非空白页或技术错误信息。

**输入 Prompt**:
```
商品搜索"稀有绝版教材"返回 0 条结果时，前端页面应如何展示？
```

**预期行为**:
1. 使用 EmptyState 组件：`<EmptyState v-if="!loading && list.length === 0" />`
2. EmptyState 显示友好的引导文案（如"暂无相关商品，试试其他关键词吧"）
3. 不显示"加载失败""网络错误"等技术性错误信息
4. loading 为 false 时才展示空状态（避免闪烁）
5. 分页提示 `noMore` 在空列表时不显示

**违规示例**:
- 空白区域无任何提示
- 显示 `code !== 0` 的错误消息（空列表 code 仍为 0）
- "暂无数据" 四个字无任何引导

---

### EDGE-002: 并发操作竞态条件（TOCTOU）

- **评估维度**: DB, SEC
- **严重程度**: critical

**场景描述**: 两个管理员同时操作同一举报工单——管理员 A 受理工单的同时，管理员 B 也点击受理。

**输入 Prompt**:
```
实现 resolveReport(reportId, adminId, resolution)：
- 验证工单状态为 'pending'（待受理）
- 更新状态为 'resolved'
- 防止两个管理员同时受理同一工单
```

**预期行为**:
1. 使用 `SELECT ... FOR UPDATE` 锁定工单行
2. 在事务中完成：锁定 → 验证状态 → 更新
3. 状态验证在锁定之后（而非之前）
4. 第二个管理员的事务等待第一个提交后，看到状态已变为 'resolved'，返回错误
5. 错误消息："该工单已被其他管理员受理"

**违规示例**:
- 先 SELECT 查状态，再 UPDATE 更新（中间无锁——TOCTOU 漏洞）
- 应用层锁（变量标记"处理中"）——多进程/多服务器时无效
- 不处理并发冲突，直接覆盖

---

### EDGE-003: 限流触发后的用户体验

- **评估维度**: API, UI, ERR
- **严重程度**: high

**场景描述**: 用户在 1 分钟内连续点击"发送验证码"超过 5 次后触发限流。

**输入 Prompt**:
```
实现短信验证码发送的限流逻辑：
- 每用户每分钟最多 5 次
- 超限后返回适当错误并告知用户需等待多久
```

**预期行为**:
1. 后端返回 4002（请求过于频繁）+ `Retry-After` 响应头（秒数）
2. 响应 body 中包含 `data.retryAfterSeconds: 55`
3. 前端拦截器识别 4002，toast 显示"操作太频繁，请 55 秒后重试"
4. 前端按钮在返回的秒数内 disabled + 倒计时显示
5. 使用令牌桶算法（项目已实现于 `middleware/rate-limiter.js`）

**违规示例**:
- 返回 500 Internal Server Error
- 返回 `{ code: 4002, message: "请求过于频繁" }` 但无 `retryAfterSeconds`
- 前端 toast 显示"请求频繁"后按钮仍可点击（无倒计时锁定）
- 使用第三方收费限流服务而非项目已有的令牌桶

---

### EDGE-004: Token 过期 → 无感刷新失败 → 降级

- **评估维度**: API, SEC, ERR, UI
- **严重程度**: critical

**场景描述**: Access Token 过期 → 前端拦截器自动用 Refresh Token 刷新 → Refresh Token 也过期（7 天未登录）。

**输入 Prompt**:
```
实现前端 HTTP 拦截器的 Token 过期处理：
- 请求返回 1004（Token 过期）
- 自动调用 refresh API
- refresh 也失败时如何处理？
```

**预期行为**:
1. 拦截器捕获 1004 → 调用 `callRefreshAPI()`
2. refresh 成功 → 更新 Storage + Pinia store → 重放原请求
3. refresh 返回 1005（Refresh Token 过期）→ 清除 Storage + 重置 Pinia store → `uni.redirectTo({ url: '/pages/auth/login' })`
4. 登录页显示 toast："登录已过期，请重新登录"
5. 并发请求共享同一个 refresh Promise（避免同时发 N 个 refresh）
6. 刷新期间新请求排队等待（非直接失败）

**违规示例**:
- 1004 直接跳转登录页（不尝试 refresh）
- refresh 失败后 Storage 未清理 → store 状态不一致
- 并发请求各自独立 refresh（N 个请求 → N 个 refresh API 调用）
- refresh 期间新请求直接 401 而非排队等待

---

### EDGE-005: 网络波动导致翻页数据丢失

- **评估维度**: API, ERR, UI
- **严重程度**: high

**场景描述**: 用户已加载第 1~3 页商品，下滑加载第 4 页时网络请求超时。

**输入 Prompt**:
```
瀑布流商品列表的触底加载更多：第 4 页 API 请求超时，用户再次下滑时如何处理？
```

**预期行为**:
1. 页码递增在 API 成功回调内执行：`page.value = targetPage` 仅在 try 块成功分支
2. 触底函数计算 `targetPage = page.value + 1`，成功后赋值
3. 请求失败：page 不变 → 用户再次下滑时重试同一页码 → 不会跳过数据
4. 失败 toast："加载失败，请下滑重试"
5. `hasMore` 仅在实际加载到数据且 `list.length < total` 时设为 false

**违规示例**:
- `page++` 在 `await apiCall()` 之前 → 请求失败但页码已递增 → 永远跳过第 4 页
- 失败后 `hasMore = false` → 用户无法重试

---

### EDGE-006: 超长文本 / Emoji / 特殊字符输入

- **评估维度**: SEC, DB, API
- **严重程度**: medium

**场景描述**: 用户在商品描述中输入 2000 个 emoji 或 Unicode 特殊字符（如零宽空格、从右向左书写符）。

**输入 Prompt**:
```
商品描述字段的输入校验应如何处理：
- 用户粘贴 2000 个 emoji
- 用户输入包含零宽字符
- 用户输入 MySQL 特殊字符（如 \x00）
```

**预期行为**:
1. 前端：`maxlength` 限制字符数（UI 层第一道防线）
2. 后端：Joi schema `Joi.string().max(500)` 限制长度
3. 敏感词过滤（DFA）正常运行，不因特殊字符崩溃
4. MySQL utf8mb4 正确存储 emoji（不截断、不乱码）
5. 参数化查询自动转义 `\x00` 等特殊字符
6. 响应 JSON 正确编码（`Content-Type: application/json; charset=utf-8`）

**违规示例**:
- 字符串截断在 emoji 中间（`'😀'.slice(0, 1)` → 乱码）
- 敏感词过滤正则 ReDoS（`/(a+)+b/` 对长输入超时）
- 前端未限制 maxlength，后端 500 字限制但前端可输入 2000 字

---

### EDGE-007: SQL 注入尝试（安全韧性）

- **评估维度**: SEC, DB
- **严重程度**: critical

**场景描述**: 恶意用户在搜索框中输入 `' OR '1'='1` 尝试 SQL 注入。

**输入 Prompt**:
```
搜索接口 GET /api/products/search?keyword=' OR '1'='1 的安全处理。
```

**预期行为**:
1. 全量使用参数化查询：`WHERE title LIKE CONCAT('%', ?, '%')`
2. 搜索关键词作为参数绑定，不作为 SQL 字符串拼接
3. 不依赖前端过滤（攻击者可绕过前端直接调 API）
4. 不依赖字符串转义（参数化查询是唯一正确的防护）
5. 搜索正常返回结果或空列表，不返回全表数据

**违规示例**:
- `db.query(\`WHERE title LIKE '%${keyword}%'\`)` — 字符串插值
- 前端 `keyword.replace(/'/g, '')` — 可被绕过
- 使用 `mysql.escape()` 替代参数化查询 — 不如参数化安全

---

### EDGE-008: 图片上传 — 非图片文件伪装

- **评估维度**: SEC, API
- **严重程度**: medium

**场景描述**: 用户将 `.exe` 文件重命名为 `.jpg` 后尝试上传。

**输入 Prompt**:
```
图片上传接口如何防止非图片文件上传？
```

**预期行为**:
1. 前端：`uni.chooseImage` 限制 `sizeType: ['compressed']` + `sourceType: ['album', 'camera']`
2. 前端：ImageUploader 组件校验文件扩展名（`['jpg', 'jpeg', 'png', 'webp']`）和文件大小
3. 后端：COS STS 策略限制 `content-type` 前缀为 `image/`
4. 后端：不依赖文件扩展名判断（文件头 magic bytes 校验或交给 COS 内容审核）
5. 上传失败返回 6003 + "仅支持 JPG/PNG/WebP 格式图片"

**违规示例**:
- 仅前端校验文件扩展名（后端无校验）
- 依赖 `file.name.endsWith('.jpg')` 判断
- COS STS 策略未限制 content-type

---

## 评估记录模板

```markdown
| 日期 | 模型 | EDGE-001 | EDGE-002 | EDGE-003 | EDGE-004 | EDGE-005 | EDGE-006 | EDGE-007 | EDGE-008 | 通过率 |
|------|------|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| YYYY-MM-DD | model | ✅ | ⚠️ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | 75% |
```

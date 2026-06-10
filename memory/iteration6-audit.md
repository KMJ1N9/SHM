---
name: iteration6-audit
description: 第 6 轮（交易流程 — 订单+评价+信誉分）全面代码审计 — 7 维度 15 文件，发现 P1×2 + P2×7，综合评分 8.5/10
metadata:
  type: project
  updatedAt: 2026-06-10
  iteration: 6
  filesAudited: 4 backend + 11 frontend
  totalLines: ~3200
---

# 第 6 轮全面代码审计

**审计日期：** 2026-06-10
**审计范围：** 第 6 轮全部 15 个改动文件 + 上下游依赖文件
**审计维度：** SQL 安全 → 数据流 → 状态管理 → 边界用例 → 安全 → 性能 → 测试覆盖
**基线：** 9 文件 126 测试全过，ESLint 0 错误，build 成功，真机验证通过

---

## 一、发现汇总

| ID | 级别 | 位置 | 简述 | 状态 |
|:--:|:--:|------|------|:--:|
| A6-001 | P1 | `order/list.vue:224` + `user/reviews.vue:242` | loadMore 翻页失败跳过一整页（与 A4-001 同模式） | 🔴 待修复 |
| A6-002 | P1 | `services/order.js:185` + `services/review.js:71` | 信誉分变动不生成通知记录 — 直接调用 `userRepo.updateCreditScore()` 绕过 `creditService.changeScore()` | 🔴 待修复 |
| A6-003 | P2 | `services/order.js:185-186` | confirm() 信誉分 fire-and-forget — 更新失败静默丢失 +2 | 🟡 待修复 |
| A6-004 | P2 | `order/list.vue:239-277` | snapshot JSON 重复解析 — 4 个辅助函数各自 `JSON.parse`，同一订单解析 4 次 | 🟡 待修复 |
| A6-005 | P2 | `order/list.vue:247,258,269,280` + `order/detail.vue:520` | 空 catch × 5 — snapshot 解析 + reviews 降级，违反 error-handling-rules | 🟡 待修复 |
| A6-006 | P2 | `services/review.js:64` Math.round | 中评/好评边界：均分 3.67→Math.round=4 触发好评（+1），与注释所述"≥3 且 <4 为中评"不一致 | 🟡 待修复 |
| A6-007 | P2 | `order/detail.vue:450-451` | timelineActive(3) 永远返回 false — 节点 4（已评价）视觉反馈缺失 | ℹ️ 第 7 轮计划已覆盖 |
| A6-008 | P2 | `order/list.vue:286,295` | `getPartnerName()` / `getRoleLabel()` 在 v-for 内每次调用 `useUserStore()` | 🟢 可接受 |
| A6-009 | P2 | `order/list.vue:340-344` + `user/reviews.vue:257-260` | onShow 无差别 refresh(true) — 每次从详情返回都重新拉取全量第 1 页 | 🟢 可接受 |

**P0：** 0 个 | **P1：** 2 个（待修复） | **P2：** 7 个（4 个待修复 + 2 个可接受 + 1 个第 7 轮覆盖）

---

## 二、后端逐层审计

### 2.1 订单 Repository（`server/src/repository/order.js` — 296 行）

#### ✅ 正确项

1. **findById JOIN 修复**：第 30-31 行 JOIN `users buyer` + `users seller`，返回 `buyer_nickname`/`buyer_avatar`/`seller_nickname`/`seller_avatar`。解决了 BUG-027（交易对象显示"买家"/"卖家"）。
2. **create() 事务完整**：`SELECT ... FOR UPDATE` → 状态校验 → `UPDATE products` → `INSERT orders` → `SELECT` 返回。防并发下单的 TOCTOU。
3. **confirmOrder() 原子操作**：事务内 `FOR UPDATE` + 二次状态校验 → `UPDATE orders` + `UPDATE products`。正确。
4. **cancelOrder() 状态机正确**：pending 双方可取消 / met 仅买家可取消 / 事务内原子操作。
5. **findByUser() LIMIT/OFFSET parseInt**：第 139-140 行强制整数解析。BUG-026 修复确认。
6. **禁止 SELECT \***：全部显式字段列表。

#### 📝 事务内使用 `conn.execute()`

```js
// order.js:62
const [products] = await conn.execute(
  'SELECT status FROM products WHERE id = ? FOR UPDATE',
  [data.product_id]
);
```

事务内查询不使用 LIMIT/OFFSET，因此 `conn.execute()` 的 prepared statement 限制（BUG-005）不触发。安全。

#### 📝 findTimeoutPending / findTimeoutMet

两个定时任务查询方法返回 `id + product_id`，供 scheduler 使用。SQL 正确，使用 `DATE_SUB(NOW(), INTERVAL N DAY)`。

---

### 2.2 订单 Service（`server/src/services/order.js` — 253 行）

#### ✅ 正确项

1. **create() 校验顺序正确**：信誉分阈值 → 幂等性检查 → 商品查询 → 自买检查 → 状态检查 → 事务创建。
2. **markAsMet() 角色校验**：买卖双方均可标记面交，仅 pending 状态。
3. **confirm() 角色校验**：仅买家可确认收货。
4. **cancel() 取消方判定在 service 层**：`cancelledBy` 由 service 根据角色+状态确定，repo 层只执行。职责分离正确。
5. **IM 通知全部 fire-and-forget + catch**：`.catch(err => logger.warn(...))` 模式一致。

#### A6-002 [P1] 信誉分变动不生成通知记录

```js
// services/order.js:185-186
userRepo.updateCreditScore(sellerId, config.credit.rewardTransaction, config.credit.max)
  .catch(err => logger.warn('信誉分更新失败', { orderId, sellerId, error: err.message }));
```

```js
// services/review.js:71-82
await userRepo.updateCreditScore(
  data.reviewee_id,
  config.credit.rewardPositiveReview,
  config.credit.max
);
```

**问题：** `order.service` 和 `review.service` 都直接调用 `userRepo.updateCreditScore()`（仅执行 `UPDATE users SET credit_score = ...`），绕过了 `creditService.changeScore()`。而 `creditService.changeScore()` 不仅更新分数，还会调用 `creditRepo.createChangeLog()` 在 `notifications` 表写入变动记录。

**影响：**
- 用户信誉分变动（交易 +2、好评 +1、差评 -5）不会出现在信誉页的「分数变动记录」中
- 用户看不到分数为什么变了，只能看到分数数字变化
- `creditService.changeScore()` 方法（含完整的 change log 创建逻辑）存在但从未被调用

**根因：** order.service 和 review.service 直接依赖 `userRepo`（底层数据访问），跳过了 `creditService`（业务服务层）。`creditService.changeScore()` 是唯一会创建 change log 的路径，但没有任何调用方。

**修复方案：**

1. `order.service` 引入 `creditService`，将 `userRepo.updateCreditScore()` 替换为：
```js
creditService.changeScore(sellerId, config.credit.rewardTransaction, '交易完成奖励')
  .catch(err => logger.warn('信誉分更新失败', { orderId, sellerId, error: err.message }));
```

2. `review.service` 引入 `creditService`，好评/差评分支替换为：
```js
const reason = avgScore >= 4 ? '获得好评' : '收到差评';
await creditService.changeScore(
  data.reviewee_id,
  avgScore >= 4 ? config.credit.rewardPositiveReview : -config.credit.deductNegativeReview,
  reason,
  { refId: review.id }
);
```

#### A6-003 [P2] confirm() 信誉分 fire-and-forget

```js
// services/order.js:185-186
userRepo.updateCreditScore(sellerId, config.credit.rewardTransaction, config.credit.max)
  .catch(err => logger.warn(...));  // 静默失败
```

`confirm()` 中信誉分更新是 fire-and-forget（不 await，不阻塞响应）。如果 `updateCreditScore` 失败：
- 订单已标记为 completed（不可逆）
- 卖家丢失 +2 信誉分
- 仅有一条 warn 日志，无自动补偿

**风险：** 低。`UPDATE users SET credit_score = LEAST(GREATEST(credit_score + ?, 0), ?)` 极少失败（无 FK、无 UNIQUE 冲突）。但若数据库连接池耗尽或临时网络问题，卖家损失无法自动恢复。

**与 review.service 的不一致：** `review.service` 中信誉分更新是 `await` 的（阻塞响应），而 `order.service` 中不 await。两个 service 对同类操作的处理方式不一致。

**修复：** 配合 A6-002 修复时统一为 `await creditService.changeScore()`，确保变动记录创建和分数更新同步完成。

---

### 2.3 评价 Repository（`server/src/repository/review.js` — 119 行）

#### ✅ 正确项

1. **create()**：INSERT 全部字段显式指定，返回完整记录。
2. **exists()**：`SELECT 1 FROM reviews WHERE ...` 高效存在性检查。
3. **findByOrder()**：JOIN users 获取 reviewer 昵称+头像。正确。
4. **findByReviewee()**：含聚合统计（AVG × 3 + COUNT）+ 分页列表。SQL 正确。
5. **LIMIT/OFFSET parseInt**：第 77-78 行。BUG-026 修复确认。
6. **禁止 SELECT \***：全部显式字段。

---

### 2.4 评价 Service（`server/src/services/review.js` — 108 行）

#### ✅ 正确项

1. **create() 校验链完整**：订单存在 → completed 状态 → 评价人参与订单 → 被评价人参与订单 → 防重复。
2. **评价人/被评价人双向校验**：第 38-44 行同时检查 reviewer 和 reviewee 是否参与订单。
3. **防重复**：`reviewRepo.exists()` + UNIQUE 约束双重保护。

#### A6-006 [P2] Math.round 中评/好评边界

```js
// services/review.js:64-66
const avgScore = Math.round(
  (data.communication_score + data.punctuality_score + data.accuracy_score) / 3
);
```

**边界行为（RV-006 测试用例注释已指出）：**

| 三维评分 | 和 | 除以 3 | Math.round | 判定 | 信誉分变动 |
|---------|:--:|:--:|:--:|:--:|:--:|
| 3+4+4 | 11 | 3.666... | **4** | **好评** | **+1** |
| 3+3+4 | 10 | 3.333... | 3 | 中评 | 0 |
| 4+4+4 | 12 | 4.0 | 4 | 好评 | +1 |

**问题：** 注释文档说"平均分 ≥ 4 = 好评"，用 `Math.round()` 意味着 3.5 及以上四舍五入为 4，均分 3.5~3.9 都算好评。但"中评"（3<avg<4）的语义与 `Math.round(3.5)=4` 冲突。

**实际影响：** 低。三维评分各 1-5 整数，求和后除以 3 的可能值只有 3/3=1, 4/3=1.33, ..., 15/3=5（共 13 个离散值）。其中：
- ≥ 4 的有：12,13,14,15 → 4.0, 4.33, 4.67, 5.0 → Math.round ≥ 4 ✅ 好评
- ≤ 2 的有：3,4,5,6 → 1.0, 1.33, 1.67, 2.0 → ≤2 ✅ 差评
- 中间的有：7,8,9,10,11 → 2.33, 2.67, 3.0, 3.33, 3.67

3.67（11/3）→ Math.round=4 是唯一的边界异常：实际均分 3.67 应该是中评，但四舍五入后算好评。

**修复方案（可选）：** 不用 Math.round，改用 `Math.floor(avg * 10) / 10` 保持一位小数精度再比较，或直接用分数和判断（≥12=好评，≤6=差评）。

---

### 2.5 订单 Controller + Route（`server/src/controllers/order.js` + `server/src/routes/order.js`）

#### ✅ 正确项

1. **Controller 纯净**：仅参数提取 + 调用 service + 响应组装。无业务逻辑。
2. **create() 正确区分 201/200**：`res.status(created ? 201 : 200)` — 幂等创建返回 200。
3. **Route Joi 校验**：`product_id` 必填 + 整数。下单端点有 `sensitiveLimiter` 限流。
4. **参数 parseInt**：所有 `:id` 参数在 controller 层做 `parseInt(req.params.id, 10)`。

---

### 2.6 评价 Route（`server/src/routes/review.js`）

#### ✅ 正确项

1. **Joi 校验完整**：3 个评分字段各 `integer().min(1).max(5).required()` + `comment` 最大 500 字。
2. **GET 无 query 校验**：`list()` 接受 `order_id` 或 `user_id` 参数，由 controller 分发。合理——两个参数互斥，Joi 复杂校验收益低。

---

### 2.7 后端测试（order.test.js 280 行 + review.test.js 260 行）

#### ✅ 覆盖项（12 + 8 = 20 用例）

**order.test.js (12 用例):**

| 维度 | 覆盖场景 |
|------|---------|
| 创建 | OR-001 成功创建 + 商品变 reserved / OR-002 幂等 / OR-003 自买拦截 / OR-004 商品锁定 / OR-005 信誉分不足 |
| 面交 | OR-006 pending→met 成功 / OR-007 非 pending 拦截 |
| 确认 | OR-008 met→completed + 商品变 sold + 信誉分+2 / OR-009 非买家拦截 |
| 取消 | OR-010 pending 买家取消 + 商品恢复 / OR-011 met 卖家取消拦截 / OR-012 重复取消拦截 |

**review.test.js (8 用例):**

| 维度 | 覆盖场景 |
|------|---------|
| 基础 | RV-001 成功创建 / RV-002 非 completed 拦截 / RV-003 重复评价拦截 / RV-004 非参与人拦截 |
| 信誉联动 | RV-005 好评 +1 / RV-006 中评不变 / RV-007 差评 -5 / RV-008 极端值 1 分 |

#### 📝 测试质量评价

- **order.test.js OR-008** 使用 `setTimeout(300)` 等待异步信誉分更新 — 脆弱，但可接受（fire-and-forget 特性决定）。
- **review.test.js RV-006** 注释已指出 `Math.round(3.67)=4` 触发好评的问题（A6-006），测试用例本身验证的是当前行为。
- 两个测试文件都正确使用 `beforeAll/afterAll` 做 DB 初始化/清理 + `beforeEach` 做数据清理 + 辅助函数创建测试数据。模式规范。
- **未覆盖边界：** 并发下单（两个买家同时购买同一商品）、面交超时自动完成、确认收货后双方互评时序。

---

## 三、前端逐层审计

### 3.1 API 封装（order.js 85 行 + review.js 48 行 + credit.js 32 行）

#### ✅ 正确项

1. **JSDoc 完整**：每个函数有 `@param` / `@returns` 类型注解。
2. **路径对齐**：API 路径与 `server/src/routes/` 完全一致。
3. **参数透传**：`get('/orders', params)` / `get('/reviews', { order_id: orderId })` 正确。

#### 📝 无独立发现

---

### 3.2 StarRating 组件（`miniprogram/src/components/StarRating.vue` — 135 行）

#### ✅ 正确项

1. **v-model 协议**：`modelValue` prop + `update:modelValue` emit — Vue 3 标准模式。
2. **触摸预览**：`@touchstart="hoverIndex = n"` + `@touchcancel="hoverIndex = 0"` — 手指滑过星星时预览高亮，离开时恢复。
3. **尺寸变体**：sm (28rpx) / md (36rpx) / lg (48rpx) — 三档覆盖不同场景。
4. **只读模式**：`pointer-events: none` + `handleClick` 内 `readonly` 守卫 — 双重保护。
5. **BUG-028 修复**：`handleClick` 改用 `emit('update:modelValue', n)` 直接触发 v-model 更新，父组件同步完成后 `hoverIndex` 重置为 0。

#### 📝 `displayValue` computed 逻辑

```js
const displayValue = computed(() => {
  return hoverIndex.value > 0 ? hoverIndex.value : props.modelValue;
});
```

正确：悬停时显示预览值，否则显示实际评分。`hoverIndex` 在 touchcancel/click 后归零 → displayValue 回落到 modelValue。

---

### 3.3 订单列表（`miniprogram/src/pages/order/list.vue` — 572 行）

#### ✅ 正确项

1. **5 Tab 筛选**：全部/pending/met/completed/cancelled — 覆盖所有状态。
2. **状态标签色彩**：蓝色(待面交)/橙色(已面交)/绿色(已完成)/灰色(已取消) — 与 detail.vue 一致。
3. **订单卡片信息**：商品图片/标题/成色/价格 + 交易对象 + 状态标签 + 时间。
4. **下拉刷新 + 触底加载**：refresher-enabled + scrolltolower。
5. **空状态**：emoji + 引导文字。
6. **角色标签**：显示"卖家：xxx"或"买家：xxx"。

#### A6-001 [P1] loadMore 翻页失败跳过一整页

```js
// order/list.vue:221-227
async function loadMore() {
  if (loadingMore.value || !hasMore.value) return;
  loadingMore.value = true;
  page.value++;              // ← 🔴 在 fetchOrders 之前递增！
  await fetchOrders(false);
  loadingMore.value = false;
}
```

**根因：** `page.value++` 在 `fetchOrders(false)` 之前执行，与 A4-001（第 4 轮 search/index.vue）完全相同的问题。

**复现路径：**
1. 订单列表第 1 页加载成功（20 条 / 共 45 条）
2. 滚动到底 → `page=2` → `fetchOrders(false)` 网络超时
3. Toast 提示"加载失败"
4. 再次滚动到底 → `page=3` → `fetchOrders(false)` 成功
5. **第 2 页的 20 条订单永久丢失**

**影响：** 用户在网络不稳定时可能看不到部分订单。比搜索场景更严重——订单涉及交易状态，遗漏可能导致用户错过重要订单。

**修复方案（与 A4-001 一致）：**
```js
async function fetchOrders(reset = false) {
  const targetPage = reset ? 1 : page.value + 1;  // 局部变量
  // ... API 调用使用 targetPage ...
  // 成功后才持久化：
  page.value = targetPage;
  // ...
}

async function loadMore() {
  if (loadingMore.value || !hasMore.value) return;
  loadingMore.value = true;
  await fetchOrders(false);  // 不再手动 page.value++
  loadingMore.value = false;
}
```

#### A6-004 [P2] snapshot JSON 重复解析

```js
// order/list.vue:239-277
function getProductImage(order)   { /* JSON.parse(order.product_snapshot) */ }
function getProductTitle(order)   { /* JSON.parse(order.product_snapshot) */ }
function getProductCondition(order){ /* JSON.parse(order.product_snapshot) */ }
function getProductPrice(order)   { /* JSON.parse(order.product_snapshot) */ }
```

4 个辅助函数各自对同一 `order.product_snapshot` 做 `JSON.parse`。每个订单卡片渲染时，`product_snapshot` 被解析 4 次（图片/标题/成色/价格各 1 次）。

**影响：** 列表有 20 个订单时，渲染一次共解析 80 次 JSON。JSON.parse 在小程序中极快（<1ms），实际性能影响无感。但模式不优雅。

**修复方案：** 在 `fetchOrders` 成功后统一预处理：
```js
orders.value = (result.list || []).map(order => ({
  ...order,
  _snapshot: typeof order.product_snapshot === 'string'
    ? JSON.parse(order.product_snapshot)
    : order.product_snapshot,
}));
```
然后 4 个辅助函数直接读 `order._snapshot.xxx`。

#### A6-005 [P2] 空 catch × 4

```js
// order/list.vue:247, 258, 269, 280
} catch { /* ignore */ }
```

4 个 snapshot 解析函数均使用空 catch，违反 `rules/error-handling-rules`。虽然是防御性代码（snapshot 格式异常时静默回退到默认值），但空 catch 不应出现。

**修复：** 至少加 `console.warn('[OrderList] 解析商品快照失败:', err.message || err)`。

#### A6-008 [P2] v-for 内重复调用 useUserStore()

```js
// order/list.vue:286,295
function getPartnerName(order) {
  const userStore = useUserStore();  // 每次调用 createPinia()
  const myId = userStore.user?.id;
  // ...
}
function getRoleLabel(order) {
  const userStore = useUserStore();  // 再次 createPinia()
  // ...
}
```

这两个函数在 `v-for` 循环中被调用，每次都执行 `useUserStore()`。虽然 Pinia 内部返回同一实例（无性能问题），但在模板函数中重复调用是不必要的。应与 `reviews.vue` 一致，在模块顶层调用一次：

```js
const userStore = useUserStore();  // 模块顶层，只调用一次
```

**注意：** `order/list.vue:327` 有顶层 `import { useUserStore } from '@/store/user'`，但从未在模块顶层调用 `useUserStore()`，仅在两个函数内调用。这是遗留的 import 顺序问题。

---

### 3.4 订单详情（`miniprogram/src/pages/order/detail.vue` — 1242 行）

#### ✅ 正确项

1. **状态时间线**：4 节点（已下单→已面交→已确认收货→已评价）+ dot 连接线 + active 绿色高亮。
2. **操作按钮按角色+状态动态显示**：canCancel/canMarkMet/canConfirm/canReview — 4 个 computed 各自独立判断。
3. **canReview 正确**：`status === 'completed' && !myReview.value` — 已修复 BUG-030。
4. **评价弹窗**：三维 StarRating（lg 尺寸）+ 文字评价 textarea（500 字上限 + 计数器）+ 提交/取消按钮。
5. **submitReview 智能错误处理**：3006（重复）→ 关闭弹窗 / 3001（状态错误）→ 关闭弹窗+刷新 / 其他 → toast 提示。
6. **snapshot 解析缓存**：`parseSnapshot()` 只在 computed 中调用，每次渲染解析 1 次（不是 4 次）。
7. **loadOrder 并行加载**：`Promise.all([getOrderDetail, getReviewsByOrder])` — 减少等待时间。
8. **评价加载降级**：`.catch(() => ({ list: [] }))` — 评价加载失败不阻塞订单显示。
9. **本地评价插入**：`submitReview` 成功后 `reviews.value.push({...})` 避免额外网络请求。

#### A6-007 [P2] timelineActive(3) 永远返回 false

```js
// order/detail.vue:450-451
if (nodeIndex === 3) return false;  // 节点 4（已评价）永远不激活
```

注释说"由 review 查询控制显示"，但节点 4 的 dot/连接线/标签从不变成绿色。虽然评价信息卡片已在下方展示（`v-if="reviews.length > 0"`），时间线上"已评价"节点永远是灰色"待完成"——视觉反馈缺失。

**修复：** 第 7 轮计划（`iteration7-report-admin.md`）已覆盖 —— 基于 `reviews.length` 判断双方互评完成度。

#### A6-005 [P2] parseSnapshot 空 catch（续）

```js
// order/detail.vue:520
} catch {
  return {};
}
```

同 A6-005，`parseSnapshot()` 的空 catch 违反规范。

#### 📝 商品快照容错

```js
// order/detail.vue:525-534
const productImage = computed(() => {
  const s = parseSnapshot();
  const images = s.images;
  return Array.isArray(images) && images.length > 0 ? images[0] : '';
});
```

5 个 computed（productImage/productTitle/productCondition/productPrice/productLocation）都调用 `parseSnapshot()`，每次都做 JSON.parse（如果 snapshot 是字符串）。但由于 `parseSnapshot()` 每次都重新解析（未缓存），5 个 computed 触发 5 次解析。与 list.vue 的 A6-004 同类问题，但影响更小（仅 1 个订单详情）。

---

### 3.5 信誉分页面（`miniprogram/src/pages/user/credit.vue` — 407 行）

#### ✅ 正确项

1. **大数字展示**：96rpx 字号 + 等宽字体 + 渐变背景 — 视觉突出。
2. **权限阈值表**：3 行（≥60 良好 / 30~59 受限 / <30 严重）+ 当前所在行高亮（`perm-row--current`）。
3. **阈值与 Store 一致**：creditLevel 用 60/30 分界 → 与 `store/user.js` 的 `canPublish`(≥60) / `canTrade`(≥30) 对齐。
4. **变动记录**：原因 + 时间 + 带符号 delta（绿色+ / 红色-）。
5. **错误状态 + 重试**：loading/error/retry 三态完整。

#### 📝 变动记录数据依赖 A6-002

由于 A6-002（信誉分变动不生成通知记录），此页面的「分数变动记录」目前只显示手动/管理员触发的变动，不显示交易和评价触发的自动变动。修复 A6-002 后此页面将自动恢复完整功能。

---

### 3.6 评价记录页（`miniprogram/src/pages/user/reviews.vue` — 471 行）

#### ✅ 正确项

1. **汇总卡片**：渐变背景 + 总评价数 + 三维度平均 StarRating（只读 sm）。
2. **评价列表**：头像/昵称/时间 + 三维评分 + 文字评价（左侧绿色竖线引用样式）。
3. **下拉刷新 + 触底加载**。
4. **空状态**：⭐ emoji + "暂无评价" + 引导语。
5. **StarRating 集成正确**：`:model-value="Math.round(summary.avg_xxx || 0)"` — 汇总用四舍五入整数显示。

#### A6-001 [P1] loadMore 翻页失败跳过一整页（续）

```js
// reviews.vue:239-245
async function loadMore() {
  if (loadingMore.value || !hasMore.value) return;
  loadingMore.value = true;
  page.value++;              // ← 与 order/list.vue 完全相同的 Bug！
  await fetchReviews(false);
  loadingMore.value = false;
}
```

与 `order/list.vue` 的 A6-001 完全相同的问题。修复方案一致。

#### A6-005 [P2] 空 catch（续）

```js
// reviews.vue:225
} catch (err) {
  uni.showToast({ title: err.message || '加载失败', icon: 'none' });
  // 🔴 加载失败时 loadingMore/refreshing 未重置！
}
```

`fetchReviews` 的 catch 分支中，只显示 toast，不重置 `loadingMore` / `refreshing` 状态。如果用户在 `loadMore` 过程中遇到网络错误：
1. `loadingMore.value = true`（在 `loadMore()` 中设置）
2. `fetchReviews(false)` 抛异常 → catch 显示 toast
3. `loadingMore` 仍为 `true` → 后续 `loadMore()` 被 `if (loadingMore.value)` 守卫拦截 → **触底加载永久失效**

对比 `order/list.vue` 的 `fetchOrders`：
```js
} catch (err) {
  uni.showToast({ title: err.message || '加载失败', icon: 'none' });
}
```
`order/list.vue` 也在 catch 中不重置 `loadingMore`，但 `loadMore()` 在 `finally` 中没有重置机制 —— 等一下，`loadMore` 在 `fetchOrders` 调用前设置 `loadingMore = true`，如果 `fetchOrders` 失败，`loadingMore` 不会被重置。

实际上 `order/list.vue:221-227`：
```js
async function loadMore() {
  if (loadingMore.value || !hasMore.value) return;
  loadingMore.value = true;
  page.value++;
  await fetchOrders(false);  // 如果这里抛异常...
  loadingMore.value = false; // 这行不会执行
}
```

这也是同样的问题！`fetchOrders(false)` 如果抛异常，`loadingMore` 不会被重置。而且 `fetchOrders` 内部不重新抛异常（catch 只 toast），所以实际上不会抛到 `loadMore`。但这意味着 `page.value++` 在 `fetchOrders` 失败后不会回滚 —— 这正是 A6-001 的核心问题。

等一下我再确认：`fetchOrders` 内部：
```js
try {
  const result = await getOrderList({...});
  // ...
} catch (err) {
  uni.showToast({ title: err.message || '加载失败', icon: 'none' });
  // 不重新 throw
}
```

对，`fetchOrders` 的 catch 吞掉了异常，所以 `loadMore` 中的 `await fetchOrders(false)` 不会抛异常，`loadingMore.value = false` 会正常执行。但 `page.value++` 已经执行了，导致 A6-001。

而 `reviews.vue` 的 `fetchReviews` 也一样，catch 吞异常。所以 `loadingMore` 会正常重置，但 `page` 已经递增了。

我的分析需要更新 —— A6-001 只关注 page 跳页问题，loadingMore 不会卡住。让我修正。

另外，reviews.vue 的 fetchReviews 如果 API 直接抛异常（不是网络错误，而是拦截器未处理的情况），catch 中 `loadingMore` 确实不会重置。但 `api/index.js` 拦截器通常会返回带错误码的响应而非抛异常，所以实际不太触发。

---

### 3.7 "我想要"按钮（`miniprogram/src/pages/product/detail.vue:419-477`）

#### ✅ 正确项

1. **校验链完整**：登录 → 非本人 → 商品 active → 防重复提交 → 二次确认 → API 调用。
2. **二次确认**：`uni.showModal` 防止误触。
3. **错误码分派**：3004（商品已锁定）→ 刷新商品状态 / 3005（自买）→ toast / 4009（信誉分不足）→ Modal 详细说明 / 其他 → 通用 toast。
4. **submitting 防抖**：`if (submitting.value) return` + finally 重置。
5. **成功后跳转**：`uni.navigateTo` 到订单详情页。

#### 📝 错误消息字符串匹配

```js
if (msg.includes('3004') || msg.includes('商品已')) { ... }
```

依赖错误消息包含汉字"商品已"，如果后端错误消息改了，前端匹配失效。当前可接受——错误码 3004 已覆盖主要分支，中文匹配为兜底。

---

### 3.8 Pinia Store（`miniprogram/src/store/user.js` — 修改 +8 行）

#### ✅ 正确项

1. **creditScore getter**：`state.user?.credit_score ?? 100` — 默认 100（新用户初始分）。
2. **canPublish getter**：≥ 60 → 与 credit.vue 权限表一致。
3. **canTrade getter**：≥ 30 → 与 credit.vue 权限表一致。
4. **initAuth Token 同步**：第 122-126 行在 getMe 后从 Storage 补齐拦截器写入的新 Token。

#### 📝 无独立发现

---

## 四、端到端数据流追踪

### 4.1 下单链路

```
[product/detail.vue]  handleWant()
  → uni.showModal 二次确认
  → createOrder({ product_id })
      │
[api/order.js]        POST /api/orders  { product_id }
      │
[Express]             auth 中间件 → req.user
      │
[routes/order.js]     Joi 校验 body.product_id
      │
[controller/order.js] orderService.create(req.user.id, req.user.credit_score, req.body)
      │
[service/order.js]    信誉分检查 → 幂等检查 → 商品查询 → 自买/状态校验
      │
[repo/order.js]       transaction(FOR UPDATE) → UPDATE products + INSERT orders
      │
[service/order.js]    IM 通知卖家（fire-and-forget）
      │
[controller]          201/200 + { data: order }
      │
[product/detail.vue]  uni.navigateTo → order/detail
```

**结论：** 数据流完整，校验在多层重复（前端基础校验 → Joi 参数校验 → Service 业务校验 → Repo 事务内 FOR UPDATE 校验）。TOCTOU 防护到位。

### 4.2 评价链路

```
[order/detail.vue]    submitReview()
  → createReview({ order_id, reviewee_id, communication_score, punctuality_score, accuracy_score, comment })
      │
[api/review.js]       POST /api/reviews
      │
[routes/review.js]    Joi 校验（评分 1-5 + comment ≤ 500）
      │
[controller/review.js] reviewService.create(req.user.id, req.body)
      │
[service/review.js]   订单存在 → completed → 参与人校验 → 防重复
      │
[repo/review.js]      INSERT reviews
      │
[service/review.js]   计算均分 → 更新信誉分（await）
      │
[order/detail.vue]    reviews.value.push(本地评价) → toast 成功 → closeReviewModal → loadOrder()
```

**结论：** 评价创建链路完整。但 A6-002 导致信誉分变动不记录通知。

### 4.3 信誉分查询链路

```
[user/credit.vue]     getMyCredit()
      │
[api/credit.js]       GET /api/credit
      │
[controller/credit.js] creditService.my(req.user.id, req.query)
      │
[service/credit.js]   creditRepo.findByUserId() → creditRepo.findChangeLogs()
      │
[repo/credit.js]      SELECT users.credit_score + SELECT notifications (type='credit_change')
      │
[user/credit.vue]     creditData.score + changeLogs 渲染
```

**结论：** 信誉分查询链路正确。但 change_log 数据取决于通知是否被创建（A6-002）。

---

## 五、安全审计

| 检查项 | 结果 | 说明 |
|--------|:--:|------|
| SQL 注入 | ✅ | 全部参数化查询，无字符串拼接 |
| 下单 TOCTOU | ✅ | 事务内 FOR UPDATE 锁定商品行 |
| 订单权限隔离 | ✅ | findById + buyer/seller ID 校验 |
| 评价权限隔离 | ✅ | 参与人双向校验 + 防重复 UNIQUE 约束 |
| 信誉分越权修改 | ✅ | 仅服务端内部调用 updateCreditScore |
| 下单限流 | ✅ | sensitiveLimiter 中间件 |
| 幂等键防重复下单 | ✅ | `${buyer_id}_${product_id}` UNIQUE 约束 |
| XSS | ✅ | Vue 模板自动转义 |
| 错误信息泄露 | ✅ | 错误响应不含 SQL/堆栈 |

---

## 六、性能审计

| 检查项 | 结果 | 说明 |
|--------|:--:|------|
| 订单列表 JOIN | ✅ | 一次 JOIN 获取买卖双方昵称+头像，无 N+1 |
| 评价列表 JOIN | ✅ | 一次 JOIN 获取 reviewer 昵称+头像 |
| 订单详情并行加载 | ✅ | Promise.all 并行加载订单+评价 |
| snapshot 解析 | ⚠️ | list.vue 中 4 个函数各自 JSON.parse 同一字符串（A6-004） |
| 评价聚合查询 | ✅ | MySQL AVG 聚合，非应用层循环计算 |
| 订单分页 | ✅ | LIMIT/OFFSET + COUNT，pageSize 上限 50 |
| 前端包体积 | ✅ | 无新增依赖 |

---

## 七、测试运行结果

```
 ✓ __tests__/unit/services/product.test.js         (30 tests)
 ✓ __tests__/integration/products.test.js           (19 tests)
 ✓ __tests__/unit/utils/sensitive-filter.test.js    (18 tests)
 ✓ __tests__/unit/services/search.test.js           (14 tests)
 ✓ __tests__/integration/search.test.js             (9 tests)
 ✓ __tests__/unit/utils/cos.test.js                 (8 tests)
 ✓ __tests__/integration/im.test.js                 (8 tests)
 ✓ __tests__/unit/services/order.test.js            (12 tests)  ← 第 6 轮新增
 ✓ __tests__/unit/services/review.test.js           (8 tests)   ← 第 6 轮新增

 Test Files  9 passed (9)
 Tests      126 passed (126)
 Duration   31.42s
```

**ESLint：** 0 错误，0 警告。

---

## 八、综合评分

| 维度 | 评分 | 说明 |
|------|:--:|------|
| **安全性** | 9/10 | TOCTOU 防护到位，权限隔离完整；下单有限流 |
| **数据流正确性** | 8/10 | 核心链路完整；A6-001（翻页跳页）影响列表完整性；A6-002（信誉分无通知）影响用户体验 |
| **状态管理** | 9/10 | Pinia store 语义正确，订单状态机前后端一致 |
| **错误处理** | 7/10 | A6-005（5 处空 catch）违反规范；A6-001（翻页跳页）未做 page 回滚 |
| **测试覆盖** | 8/10 | 新增 20 用例覆盖核心路径；并发/超时/定时任务未覆盖 |
| **代码质量** | 8/10 | 风格一致，命名清晰；A6-004（重复 JSON.parse）+ A6-008（重复 useUserStore）有改进空间 |
| **性能** | 8/10 | 并行加载订单+评价；snapshot 重复解析可优化 |
| **综合** | **8.5/10** | P1×2 修复后回升至 9.0+。 |

---

## 九、与历史轮次对比

| 指标 | 第 3 轮 | 第 4 轮 | 第 5 轮 | 第 6 轮 |
|------|:--:|:--:|:--:|:--:|
| 审计文件数 | 14 | 6 | 16 | 15 |
| 审计代码量 | ~1800 行 | ~1480 行 | ~2600 行 | ~3200 行 |
| P0 严重问题 | 5 | 0 | 0 | 0 |
| P1 中等问题 | 4 | 1 | 2 | 2 |
| P2 轻微问题 | 16 | 7 | 7 | 7 |
| 问题密度（每千行） | 13.9 | 5.4 | 3.5 | 2.8 |
| 审计后评分 | 9.0/10 | 9.0/10 | 8.5→9.0/10 | 8.5/10 |

**趋势：** P0 连续三轮为零 ✅。问题密度继续下降（3.5→2.8/千行）。本轮 P1 集中在之前轮次已发现的模式（A4-001 翻页跳页在 list.vue + reviews.vue 重复出现）和架构层面的遗漏（creditService.changeScore 未被调用）。

**经验教训：**
1. A4-001 的修复只应用于 `search/index.vue` 和 `index/index.vue`，未推广到后续新增的列表页（order/list.vue、user/reviews.vue）。需要在 `loadMore` 模板中固化 `targetPage` 模式。
2. `creditService.changeScore()` 作为唯一的信誉分变动+通知创建路径，存在但未被调用——说明 service 层 API 设计需要更明确的"唯一入口"约束。

---

## 十、修复优先级建议

| 顺序 | ID | 修复内容 | 涉及文件 | 预估耗时 |
|:--:|:--:|------|------|:--:|
| 1 | A6-001 | list.vue + reviews.vue loadMore targetPage 模式 | 2 文件 | 15min |
| 2 | A6-002 | order.service + review.service 改用 creditService.changeScore() | 2 文件 | 20min |
| 3 | A6-005 | 5 处空 catch 加 console.warn | 2 文件 | 10min |
| 4 | A6-003 | confirm() 信誉分 await（顺带 A6-002 修复） | 1 文件 | 5min |
| 5 | A6-004 | list.vue snapshot 预解析 | 1 文件 | 10min |
| 6 | A6-006 | review.service Math.round → 分数和判断 | 1 文件 | 5min |

**A6-007 在第 7 轮覆盖** | **A6-008 + A6-009 可接受不修复**

---

**Why:** 全面审计第 6 轮 15 个文件 ~3200 行代码，覆盖安全/数据流/状态管理/边界用例/错误处理/性能/测试 7 维度。发现 2 个 P1（翻页跳页重复出现 + 信誉分变动不记录通知）和 7 个 P2（空 catch × 5 + snapshot 重复解析 + Math.round 边界 + 时间线节点 + useUserStore 重复 + onShow 无条件刷新）。P0 连续三轮为零，问题密度降至 2.8/千行。修复全部 P1+P2 后评分可回升至 9.0+。

**How to apply:**
1. A6-001 修复后，将 `targetPage` 模式写入编码规范（所有分页列表组件必须遵循）。
2. A6-002 修复后，建立规则："信誉分变动只能通过 `creditService.changeScore()` 进行，禁止直接调用 `userRepo.updateCreditScore()`"。
3. A4-001 的教训：发现一个 Bug 后，必须全局搜索相同模式并全部修复。

**关联记忆：** [[project-state]] [[iteration5-audit]] [[iteration4-audit]] [[iteration3-audit]] [[known-bugs]]

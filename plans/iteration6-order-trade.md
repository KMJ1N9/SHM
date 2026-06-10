# 第 6 轮编码计划：交易流程（订单 + 评价）

> **状态：** ✅ 已完成（2026-06-10）
> **实际工时：** ~1.5 天（编码）+ 审计半天
> **目标：** 订单状态机完整流转（pending→met→completed）→ "我想要"下单 → 超时自动处理 → 双向评价 + 信誉分联动
> **审计报告：** [[../memory/iteration6-audit.md]] — P0×0 / P1×2 / P2×7，综合评分 8.5/10
> **依据文档：** PRD §3.4（交易流程）、技术架构文档 §七（订单状态机）、API 文档 §2.5（订单模块）+ §2.6（评价模块）、测试计划 §3.7~§3.8 + §4.6~§4.7、编码迭代计划 §第 6 轮

---

## 现状分析

### 后端 — 交易基础设施 ~95% 完成 ✅

| 层 | 文件 | 状态 | 核心能力 |
|------|------|:--:|------|
| Service | `services/order.js` (254 行) | ✅ | create/list/detail/markAsMet/confirm/cancel，含幂等/信誉分校验/IM 通知 |
| Repository | `repository/order.js` (285 行) | ✅ | FOR UPDATE 事务锁、confirmOrder/cancelOrder 事务原子操作、findTimeoutPending/Met |
| Controller | `controllers/order.js` (98 行) | ✅ | 6 端点全部就绪 |
| Route | `routes/order.js` (46 行) | ✅ | 6 端点 + Joi 校验 |
| Service | `services/review.js` (109 行) | ✅ | create/listByOrder/listByUser，三维评分 + 信誉分联动 |
| Repository | `repository/review.js` (118 行) | ✅ | create/exists/findByOrder/findByReviewee（含聚合统计） |
| Controller | `controllers/review.js` (46 行) | ✅ | create + list（支持 order_id / user_id 查询） |
| Route | `routes/review.js` (31 行) | ✅ | 2 端点 + Joi 校验 |
| Scheduler | `scheduler.js` (266 行) | ✅ | 超时取消(24h) + 超时确认(72h) + 备份 + 归档 |
| Config | `config/index.js` | ✅ | CREDIT_PUBLISH_THRESHOLD=60 / CREDIT_TRADE_THRESHOLD=30 已定义 |

**结论：** 后端交易核心逻辑和定时任务全部实现。本轮后端仅需补测试用例。

### 前端 — 8 个文件全为占位 stub，2 个文件需修改

| 文件 | 状态 | 说明 |
|------|:--:|------|
| `api/order.js` | ❌ stub | 需完整实现 6 个端点封装 |
| `api/review.js` | ❌ stub | 需实现 create/list 端点封装 |
| `api/credit.js` | ❌ stub | 需实现 getMyCredit/getUserCredit |
| `components/StarRating.vue` | ❌ stub | 交互式五星评分组件 |
| `pages/order/list.vue` | ❌ stub | 订单列表 + 状态 Tab 切换 |
| `pages/order/detail.vue` | ❌ stub | 订单详情 + 状态时间线 + 操作按钮 + 评价弹窗 |
| `pages/user/credit.vue` | ❌ stub | 信誉分展示 + 变动记录 |
| `pages/user/reviews.vue` | ❌ stub | 评价记录列表 |
| `pages/product/detail.vue` | ⚠️ 缺"我想要" | 当前仅有"聊一聊"按钮，需添加下单入口 |
| `store/user.js` | ⚠️ 缺 getter | 需添加 creditScore/canPublish/canTrade 派生状态 |

---

## 架构关键决策

- **订单状态机：** `pending → met → completed` / `pending → cancelled` / `met → cancelled`（仅买家）
- **商品状态联动：** 下单→reserved / 确认收货→sold / 取消→active（事务保证原子性）
- **幂等键：** `${buyerId}_${productId}`，同一买家对同一商品不可重复创建订单
- **超时处理：** 24h 未面交→自动取消 / 72h 面交未确认→自动确认（scheduler.js 已实现）
- **评价三维度：** 沟通态度/守时程度/描述一致度，各 1-5 分，平均分 ≥4 好评 +1、≤2 差评 -5
- **信誉分阈值：** <60 禁止发布商品 / <30 禁止发起交易（config 已定义）

---

## 执行策略：科学混合并行（4 波 6 Agent + 1 Workflow）

> **决策日期：** 2026-06-10
> **方法：** 基于依赖图 + 独立性量化 + 视觉内聚分析的混合并行策略

### 为什么不全串行也不全并行

| 方案 | 预估耗时 | 加速比 | 一致性风险 | 判定 |
|------|:--:|:--:|:--:|:--:|
| 纯串行（全自己写） | ~8.5h | 1× | 最低 | ❌ 过慢 |
| 激进并行（8 Agent 全铺开） | ~3.5h | 2.4× | **高**（风格漂移） | ❌ Wave 2 的 4 页面会各自为政 |
| **科学混合（本方案）** | **~3.7h** | **2.3×** | **低**（内聚合并） | ✅ |

激进并行仅省 0.2h 但一致性风险翻倍 — 不划算。科学混合按"视觉内聚性"合并 Agent：让同一个 Agent 负责视觉风格需要统一的页面组。

### 依赖图关键洞察

- **Wave 1 的 6 个产物零交集** — 两个测试文件操作不同表、三个 API 模块封装不同端点、StarRating 是纯 UI 组件。4 路并行无风险。
- **Wave 2 的 4 页面有共享视觉语言** — 订单列表和订单详情共享"订单卡片"视觉；信誉分和评价记录共享"用户子页面"信息卡模式。不能拆成 4 Agent，应合并为 2 Agent。
- **Wave 3 修改已有文件** — "我想要"按钮改 `product/detail.vue`，评价弹窗改 `order/detail.vue`（刚在 Wave 2 完成）。Agent 上下文不足以精确修改已有文件 → 我直接串行写。
- **Wave 4 多维度独立检查** — ESLint/测试/代码审查/scheduler smoker 互不依赖 → 最佳 Workflow 场景。

### 四波执行计划

```
┌──────────────────────────────────────────────────────────────┐
│ Wave 1: 4 Agent 并行 · 预估 30 min · 风险 极低               │
│                                                               │
│  Agent A ─→ server/__tests__/unit/services/order.test.js     │
│             ~200 行，12 用例，操作 orders 表                   │
│  Agent B ─→ server/__tests__/unit/services/review.test.js    │
│             ~180 行，8 用例，操作 reviews 表                   │
│  Agent C ─→ api/order.js + api/review.js + api/credit.js     │
│             ~140 行，纯 HTTP 封装，后端接口已固定              │
│  Agent D ─→ components/StarRating.vue                        │
│             ~120 行，纯 UI 组件，零业务依赖                    │
│                                                               │
│  验证门禁: 4 个产物全部通过 vitest/ESLint → 进入 Wave 2       │
├──────────────────────────────────────────────────────────────┤
│ Wave 2: 2 Agent 并行 · 预估 90 min · 风险 低                 │
│                                                               │
│  Agent E ─→ pages/order/list.vue + pages/order/detail.vue    │
│             ~800 行，同一 Agent 保证订单 UI 风格统一           │
│             共享: 订单卡片样式 / 状态标签配色 / 时间线组件      │
│  Agent F ─→ pages/user/credit.vue + pages/user/reviews.vue   │
│             + store/user.js (3 getter)                        │
│             ~370 行，同属"用户子页面"，共享信息卡模式           │
│                                                               │
│  验证门禁: 4 页面可独立渲染，Tab/路由跳转正确 → 进入 Wave 3   │
├──────────────────────────────────────────────────────────────┤
│ Wave 3: 我直接串行 · 预估 60 min · 需精确理解已有代码          │
│                                                               │
│  3a. product/detail.vue ─→ 添加 "我想要" 按钮 + handleWant() │
│      ~50 行改动，需理解现有 goChat() / 布局 / store 用法       │
│  3b. order/detail.vue ─→ 内嵌评价弹窗 + submitReview()       │
│      ~180 行，StarRating 集成 + 订单状态联动                   │
│                                                               │
│  验证门禁: 点"我想要"→下单→跳转详情→评价弹窗→提交→刷新       │
├──────────────────────────────────────────────────────────────┤
│ Wave 4: Workflow 验证 · 预估 40 min                           │
│                                                               │
│  并行 4 维度:                                                  │
│  · npx vitest run (含新增 ~20 用例)                           │
│  · npm run lint (ESLint 0 错误)                               │
│  · 对抗性代码审查 (3 Agent 从安全/性能/规范视角)               │
│  · scheduler smoker (手动触发超时任务)                         │
│                                                               │
│  全部通过 → 更新 memory/ → git commit                          │
└──────────────────────────────────────────────────────────────┘
```

### Agent 总数与产出

| Wave | Agent 数 | 产出文件 | 行数 | 累积 |
|:--:|:--:|------|:--:|:--:|
| 1 | 4 | 5 文件 | ~640 | 640 |
| 2 | 2 | 5 文件 | ~1170 | 1810 |
| 3 | 0 (我直接写) | 2 处修改 | ~230 | 2040 |
| 4 | 1 Workflow (4 子任务) | 验证报告 | — | — |
| **合计** | **6 Agent + 1 Workflow** | **12 产物** | **~2040** | |

### 风格一致性保障

| 保障措施 | 说明 |
|------|------|
| API 契约先行 | Wave 1 Agent C 完成 API 模块后，Agent E/F 直接 import 使用，函数签名锁定 |
| 视觉内聚合并 | 订单相关 → Agent E，用户子页面 → Agent F，不对同一视觉域拆 Agent |
| 设计令牌引用 | 所有 Agent 必须引用 `tokens.scss` 中的变量，禁止硬编码颜色/字号/间距 |
| Wave 3 串行调平 | 我直接审查 Wave 2 产出后，在 Wave 3 统一调平不一致之处 |
| 验证门禁 | 每波结束有明确的可验证门禁，不通过不回 |

---

## 分步计划（10 步，每步独立可验证）

### 步骤 1：后端 — 订单/评价测试（2 新文件，~25 用例）

**文件：** `server/__tests__/unit/services/order.test.js`（新建）+ `server/__tests__/unit/services/review.test.js`（新建）

**复用模式：** `__tests__/setup.js` 的 `createTestUser` / `createTestProduct` 辅助函数。

**order.test.js 测试用例（~12 条）：**

| ID | 用例 | 预期 |
|:--:|------|------|
| OR-001 | 创建订单成功 | 返回 order，status='pending'，product 变为 reserved |
| OR-002 | 同一买家重复下单同一商品 | 返回已有订单，created=false（幂等） |
| OR-003 | 购买自己的商品 | 返回 cannotBuyOwn 错误 |
| OR-004 | 购买已锁定/已售商品 | 返回 productLocked 错误 |
| OR-005 | 信誉分 <30 无法下单 | 返回 creditTooLowTrade 错误 |
| OR-006 | 标记面交（pending→met） | 返回 met 状态 + met_at 时间 |
| OR-007 | 非 pending 状态标记面交 | 返回 orderStateInvalid 错误 |
| OR-008 | 买家确认收货（met→completed） | 商品变 sold，卖家信誉分 +2 |
| OR-009 | 非买家确认收货 | 返回 notOwner 错误 |
| OR-010 | pending 状态买家取消 | 商品恢复 active，cancelled_by='buyer' |
| OR-011 | met 状态仅买家可取消 | 卖家取消 met 订单→orderStateInvalid |
| OR-012 | 已取消订单再次取消 | 返回 orderStateInvalid 错误 |

**review.test.js 测试用例（~8 条）：**

| ID | 用例 | 预期 |
|:--:|------|------|
| RV-001 | 对已完成订单创建评价 | 返回 review 记录，三维评分正确 |
| RV-002 | 非 completed 状态订单评价 | 返回 orderStateInvalid |
| RV-003 | 同一人对同一订单重复评价 | 返回 duplicateReport 错误 |
| RV-004 | 未参与订单的人评价 | 返回"未参与"错误 |
| RV-005 | 好评（平均≥4）→ 被评价方信誉分+1 | credit_score 增加 |
| RV-006 | 中评（3<avg<4）→ 信誉分不变 | credit_score 不变 |
| RV-007 | 差评（平均≤2）→ 被评价方信誉分-5 | credit_score 减少 |
| RV-008 | 三维评分越界（0/6） | Joi 校验返回 400 |

**验证：** `npx vitest run server/__tests__/unit/services/order.test.js server/__tests__/unit/services/review.test.js` 全部通过

---

### 步骤 2：前端 — 订单/评价/信誉分 API 模块（3 文件，~180 行）

**文件：** `miniprogram/src/api/order.js` + `miniprogram/src/api/review.js` + `miniprogram/src/api/credit.js`（均替换占位）

**2.1 order.js（~70 行）：**

```js
import { get, post, put } from './index';

/** 创建订单（下单） */
export function createOrder(data) { return post('/orders', data); }

/** 我的订单列表 */
export function getOrderList(params) { return get('/orders', params); }

/** 订单详情 */
export function getOrderDetail(id) { return get(`/orders/${id}`); }

/** 标记面交 */
export function markAsMet(id) { return put(`/orders/${id}/met`); }

/** 确认收货 */
export function confirmOrder(id) { return put(`/orders/${id}/confirm`); }

/** 取消订单 */
export function cancelOrder(id) { return put(`/orders/${id}/cancel`); }
```

**2.2 review.js（~40 行）：**

```js
import { get, post } from './index';

/** 创建评价 */
export function createReview(data) { return post('/reviews', data); }

/** 获取订单的评价列表 */
export function getReviewsByOrder(orderId) { return get('/reviews', { order_id: orderId }); }

/** 获取用户的评价列表（含聚合统计） */
export function getUserReviews(userId, params) { return get('/reviews', { user_id: userId, ...params }); }
```

**2.3 credit.js（~30 行）：**

```js
import { get } from './index';

/** 获取我的信誉分详情（含变动记录） */
export function getMyCredit() { return get('/credit/my'); }

/** 获取某用户的公开信誉分 */
export function getUserCredit(userId) { return get(`/credit/${userId}`); }
```

**验证：** API 调用返回正确数据结构（后端已就绪，直接对接）

---

### 步骤 3：前端 — StarRating 组件（~120 行）

**文件：** `miniprogram/src/components/StarRating.vue`（替换占位）

**功能规格：**

```
Props:
  value: number = 0          // 当前评分 (1-5)
  max: number = 5
  size?: 'sm' | 'md' | 'lg'  // 星星大小（sm=28rpx, md=36rpx, lg=48rpx）
  readonly?: boolean = false  // 只读模式

Events:
  @change: (value: number) => void

行为：
  - 渲染 5 颗星，根据 value 显示实心/空心
  - 点击第 N 颗星 → emit('change', N)
  - readonly 时禁止点击交互
  - 过渡动画：scale(1.2) → scale(1.0)，150ms

设计令牌：
  - 实心星：$color-star #FFB800（黄色）
  - 空心星：$color-border #E0E0E0（浅灰）
  - 间距：4rpx
```

**验证：** 点击第 3 颗星 → value 变为 3 → 前 3 颗变成实心黄色

---

### 步骤 4：前端 — 订单列表页（~350 行）

**文件：** `miniprogram/src/pages/order/list.vue`（替换占位）

**功能规格：**

1. **Tab 切换（横向滚动）：**
   - 全部 / 待面交 / 已面交 / 已完成 / 已取消
   - 5 个 Tab 对应 order status：all / pending / met / completed / cancelled
   - 切 Tab → 重新请求过滤后的数据

2. **订单卡片：**
   ```
   ┌──────────────────────────────────┐
   │ [商品图 120×120]  商品标题         │
   │                   成色 / ¥价格     │
   │                   交易对象：小明   │  ← 根据角色显示"卖家"或"买家"
   │                   状态标签         │  ← 彩色 Badge
   │                   [操作按钮区]     │  ← 根据状态动态显示
   └──────────────────────────────────┘
   ```

3. **订单列表数据源：**
   - 调用 `getOrderList({ status, page, pageSize })`
   - `product_snapshot` JSON 里有商品图片/标题/价格/成色
   - `buyer_nickname` / `seller_nickname` 来自 JOIN
   - 区分"我买的"（role=buyer）和"我卖的"（role=seller）—— 列表接口默认返回全部

4. **状态标签样式（设计令牌）：**
   - pending：蓝色底 `#E6F7FF` + 蓝色字 `#1890FF`
   - met：橙色底 `#FFF7E6` + 橙色字 `#FA8C16`
   - completed：绿色底 `#F6FFED` + 绿色字 `#52C41A`
   - cancelled：灰色底 `#F5F5F5` + 灰色字 `#999999`

5. **交互：**
   - 点击卡片 → `navigateTo` 订单详情
   - 下拉刷新 `onPullDownRefresh`
   - 触底加载 `onReachBottom`
   - 空状态："暂无订单"

**验证：** 有历史订单数据时 → 列表渲染 → 切 Tab 过滤正确 → 空状态正常

---

### 步骤 5：前端 — 订单详情页（~450 行，本轮核心）

**文件：** `miniprogram/src/pages/order/detail.vue`（替换占位）

**功能规格：**

**5.1 页面布局：**

```
┌──────────────────────────────────┐
│  [返回]         订单详情          │
├──────────────────────────────────┤
│  ── 订单状态时间线 ──            │
│  ● 创建订单          2024-12-01  │
│  ● 已面交            2024-12-02  │  ← 当前状态高亮
│  ○ 确认收货          待完成      │  ← 灰色未完成
│  ○ 已评价            待完成      │
├──────────────────────────────────┤
│  ── 商品信息 ──                  │
│  [商品图] 商品标题               │
│           成色 / ¥价格           │
│           交易地点               │
├──────────────────────────────────┤
│  ── 交易对象 ──                  │
│  [头像] 小明                     │  ← 点击跳转用户主页
│  信誉分：98                      │
├──────────────────────────────────┤
│  订单编号：#20241201001          │
│  创建时间：2024-12-01 10:30     │
├──────────────────────────────────┤
│  ┌────────────┬────────────┐    │
│  │  取消订单   │  确认收货   │    │  ← 根据状态动态显示
│  └────────────┴────────────┘    │
└──────────────────────────────────┘
```

**5.2 状态 → 操作按钮映射：**

| 订单状态 | 我的角色 | 可见操作 |
|---------|---------|---------|
| pending | 买家 | [取消订单] |
| pending | 卖家 | [取消订单] |
| met | 买家 | [确认收货] |
| met | 卖家 | —（等待买家确认） |
| completed | 任一 | [去评价]（未评）/ [查看评价]（已评） |
| cancelled | 任一 | — |

**5.3 状态时间线（Timeline）：**

4 个节点渲染逻辑：
```
节点 1 — "已下单" — 始终显示，时间 = created_at
节点 2 — "已面交" — status=met/completed 时高亮，时间 = met_at
节点 3 — "已确认收货" — status=completed 时高亮，时间 = confirmed_at
节点 4 — "已评价" — 双方均评价后显示

当前状态用实心彩色圆点 + 加粗文字
未完成状态用空心灰色圆点 + 浅色文字
```

**5.4 数据获取：**
- `onLoad` → `getOrderDetail(id)` → 渲染
- `onShow` → 重新拉取（评价后返回需要刷新）

**5.5 操作调用：**

```js
// 取消订单 → 二次确认弹窗 → cancelOrder → 刷新
// 确认收货 → 二次确认弹窗 → confirmOrder → 刷新 → Toast "已确认收货，请评价"
// 去评价 → 弹出评价弹窗（步骤 7）
// 查看评价 → navigateTo 评价详情或显示当前订单评价
```

**验证：** 完整流程：pending→点击面交→met→确认收货→completed→评价入口出现

---

### 步骤 6：前端 — product/detail.vue 接入"我想要"按钮（~50 行改动）

**文件：** `miniprogram/src/pages/product/detail.vue`

**改动点：**

在底部操作栏"聊一聊"旁添加"我想要"按钮（主要按钮样式）：

```js
/**
 * 我想要 — 创建订单
 *
 * 校验链（按顺序）：
 *   1. 未登录 → 跳转登录页
 *   2. 商品不存在 → 返回错误
 *   3. 自己的商品 → Toast "这是你自己发布的商品"
 *   4. 商品非 active → Toast "商品已下架或已售"
 *   5. 信誉分 < 30 → Toast "信誉分不足，无法发起交易"
 *   6. 已对该商品下单 → 直接跳转已有订单详情
 */
async function handleWant() {
  const userStore = useUserStore();

  // 1. 登录检查
  if (!userStore.isLoggedIn) {
    uni.showToast({ title: '请先登录', icon: 'none' });
    setTimeout(() => uni.navigateTo({ url: '/pages/auth/login' }), 1500);
    return;
  }

  // 2-3. 商品检查
  if (!product.value) return;
  if (userStore.user?.id === product.value.seller?.id) {
    uni.showToast({ title: '这是你自己发布的商品', icon: 'none' });
    return;
  }
  if (product.value.status !== 'active') {
    uni.showToast({ title: '商品已下架或已售', icon: 'none' });
    return;
  }

  // 4. 信誉分检查
  const userInfo = userStore.user;
  if (userInfo?.credit_score < 30) {
    uni.showToast({ title: '信誉分不足（<30），无法发起交易', icon: 'none', duration: 2000 });
    return;
  }

  // 5. 下单
  uni.showLoading({ title: '下单中...', mask: true });
  try {
    const result = await createOrder({ product_id: product.value.id });
    uni.hideLoading();
    // 跳转订单详情
    uni.redirectTo({ url: `/pages/order/detail?id=${result.data.id}` });
  } catch (err) {
    uni.hideLoading();
    // 幂等 → 跳转已有订单
    if (err.code === 3xxx_or_specific) {
      // 具体处理（API 返回已有订单 id 时直接跳转）
    }
    uni.showToast({ title: err.message || '下单失败，请重试', icon: 'error' });
  }
}
```

**需要导入：** `import { createOrder } from '@/api/order'`

**验证：** 点"我想要" → 创建订单 → 跳转订单详情页 → 商品状态变为 reserved

---

### 步骤 7：前端 — 评价弹窗（订单详情页内，~180 行）

**在 `pages/order/detail.vue` 中实现评价弹窗（`<uni-popup>` 或半屏 overlay）：**

**评价表单布局：**

```
┌──────────────────────────────────┐
│         对 小明 的评价            │
│                                  │
│  沟通态度      ★★★★★  [StarRating]│
│  守时程度      ★★★★★  [StarRating]│
│  描述一致度    ★★★★★  [StarRating]│
│                                  │
│  文字评价（选填）：               │
│  ┌──────────────────────────────┐│
│  │ 卖家很靠谱，面交准时...       ││
│  └──────────────────────────────┘│
│                                  │
│  [取消]              [提交评价]   │
└──────────────────────────────────┘
```

**关键逻辑：**

```js
async function submitReview() {
  // 三维度必填校验
  if (!commScore.value || !puncScore.value || !accuScore.value) {
    uni.showToast({ title: '请完成所有维度评分', icon: 'none' });
    return;
  }

  uni.showLoading({ title: '提交中...', mask: true });
  try {
    await createReview({
      order_id: order.value.id,
      reviewee_id: revieweeId.value, // 对方（买家评价卖家 / 卖家评价买家）
      communication_score: commScore.value,
      punctuality_score: puncScore.value,
      accuracy_score: accuScore.value,
      comment: comment.value || undefined,
    });
    uni.hideLoading();
    uni.showToast({ title: '评价成功', icon: 'success' });
    closeReviewPopup();
    loadOrder(); // 刷新订单详情
  } catch (err) {
    uni.hideLoading();
    uni.showToast({ title: err.message || '评价失败', icon: 'error' });
  }
}
```

**弹出条件：** `order.status === 'completed'` 且当前用户尚未评价对方

**验证：** 确认收货 → 弹窗出现 → 打 5 星 → 提交 → 评价成功 → `reviews` 表新增记录 → 信誉分变化

---

### 步骤 8：前端 — 信誉分页面（~200 行）

**文件：** `miniprogram/src/pages/user/credit.vue`（替换占位）

**功能规格：**

1. **头部大数字展示：**
   ```
   ┌──────────────────────────────────┐
   │                                  │
   │           ┌──────────┐           │
   │           │    98    │           │  ← 大数字（当前信誉分）
   │           │  / 200   │           │  ← 上限
   │           └──────────┘           │
   │        信誉等级：良好            │  ← ≥60:良好, 30-59:受限, <30:严重
   │                                  │
   │  ┌──────────────────────────────┐│
   │  │ 📦 已发布商品     ✅ 可发布  ││
   │  │ 🛒 发起交易       ✅ 可交易  ││
   │  └──────────────────────────────┘│
   └──────────────────────────────────┘
   ```

2. **信誉分阈值说明卡片：**
   | 分数段 | 等级 | 发布商品 | 发起交易 |
   |:--:|:--:|:--:|:--:|
   | ≥60 | 良好 | ✅ | ✅ |
   | 30~59 | 受限 | ❌ | ✅ |
   | <30 | 严重 | ❌ | ❌ |

3. **分数变动记录列表：**
   - 调用 `getMyCredit()` 获取变动历史
   - 每条记录：变动原因 + 分数变化 + 时间
   - 减少用红色 `-30`，增加用绿色 `+2`

4. **数据源：**
   - `GET /api/credit/my` → `{ score, max, level, changeLogs: [...] }`
   - 后端 credit service 和 controller 已实现

**验证：** 进入信誉分页 → 显示当前分数 → 变动记录正确 → 阈值说明正确

---

### 步骤 9：前端 — 用户评价记录页 + Store 增强（~200 行）

**文件：** `miniprogram/src/pages/user/reviews.vue`（替换占位）+ `miniprogram/src/store/user.js`（修改）

**9.1 评价记录页（~150 行）：**

```
┌──────────────────────────────────┐
│  我的评价                         │
├──────────────────────────────────┤
│  评价汇总                        │
│  共 12 条评价                    │
│  沟通态度 ⭐4.8 守时 ⭐4.5 描述 ⭐4.2 │
├──────────────────────────────────┤
│  [头像] 小明              3天前  │
│  沟通⭐⭐⭐⭐⭐ 守时⭐⭐⭐⭐⭐ 描述⭐⭐⭐⭐ │
│  "卖家很靠谱，面交准时..."       │
│  ─────────────────────────────── │
│  [头像] 小红              1周前  │
│  沟通⭐⭐⭐⭐ 守时⭐⭐⭐ 描述⭐⭐⭐⭐⭐   │
│  "商品成色比描述稍差一些..."     │
└──────────────────────────────────┘
```

`getUserReviews(userId, { page: 1 })` → 返回 `{ summary: { total, avg_communication, avg_punctuality, avg_accuracy }, list: [...], total }`

**9.2 Pinia User Store 增强（~30 行改动）：**

在 `store/user.js` 中新增 3 个 getter：

```js
getters: {
  isLoggedIn: (state) => !!(state.user && state.accessToken),
  /** 当前信誉分 */
  creditScore: (state) => state.user?.credit_score ?? 100,
  /** 是否可以发布商品（信誉分 ≥ 60） */
  canPublish: (state) => (state.user?.credit_score ?? 100) >= 60,
  /** 是否可以发起交易（信誉分 ≥ 30） */
  canTrade: (state) => (state.user?.credit_score ?? 100) >= 30,
},
```

在 `getMeAction()` 成功后同步更新 `credit_score`（已通过 API 返回，无需额外处理）。

**验证：** 进入评价记录页 → 显示汇总 + 列表 → `userStore.canPublish` / `canTrade` 正确

---

### 步骤 10：全量验证 + 状态更新

- [ ] `npx vitest run` 全部通过（含新增 order/review 测试 ~20 条）
- [ ] 前端 `npm run lint` ESLint 通过（0 错误）
- [ ] 微信开发者工具端到端验证：
  - 商品详情页 → 点"我想要" → 创建订单 → 商品变 reserved ✅
  - 订单列表 → 显示订单 → 切 Tab 过滤 ✅
  - 订单详情 → 状态时间线 → 点"已面交" → 状态更新 ✅
  - 订单详情 → 确认收货 → 弹出评价弹窗 → 提交评价 → 信誉分变化 ✅
  - 信誉分页 → 显示分数 + 变动记录 ✅
  - 评价记录页 → 显示汇总 + 评价列表 ✅
  - 刷新后"我想要"按钮根据信誉分灰度 ✅
- [ ] 定时任务验证（仅 smoker）：
  - 等待 scheduler 执行或手动触发 → 超时订单状态正确变更
- [ ] 更新 `memory/project-state.md` — 记录第 6 轮完成状态
- [ ] 更新 `memory/known-bugs.md` — 如有发现 Bug
- [ ] 更新 `memory/MEMORY.md`

---

## 文件改动清单

| # | 文件 | 操作 | 预计行数 |
|:--:|------|:--:|:--:|
| 1 | `server/__tests__/unit/services/order.test.js` | 新建 | ~200 |
| 2 | `server/__tests__/unit/services/review.test.js` | 新建 | ~180 |
| 3 | `miniprogram/src/api/order.js` | 重写 | ~70 |
| 4 | `miniprogram/src/api/review.js` | 重写 | ~40 |
| 5 | `miniprogram/src/api/credit.js` | 重写 | ~30 |
| 6 | `miniprogram/src/components/StarRating.vue` | 重写 | ~120 |
| 7 | `miniprogram/src/pages/order/list.vue` | 重写 | ~350 |
| 8 | `miniprogram/src/pages/order/detail.vue` | 重写 | ~450 |
| 9 | `miniprogram/src/pages/user/credit.vue` | 重写 | ~200 |
| 10 | `miniprogram/src/pages/user/reviews.vue` | 重写 | ~150 |
| 11 | `miniprogram/src/pages/product/detail.vue` | 修改（"我想要"） | ~50 |
| 12 | `miniprogram/src/store/user.js` | 修改（getter） | ~20 |

**总计：** 2 新建 + 8 重写 + 2 修改 ≈ 1860 行（含测试 ~380 行）

---

## 依赖与风险

### 阻塞依赖

| 依赖 | 状态 | 说明 |
|------|:--:|------|
| 后端订单 API | ✅ | 6 端点全就绪，含 Joi 校验 |
| 后端评价 API | ✅ | 2 端点全就绪，含 Joi 校验 |
| 后端信誉分 API | ✅ | routes/controllers/services/repository 全就绪 |
| Scheduler 定时任务 | ✅ | 4 个 cron 全部实现 |
| 用户登录态 | ✅ | 第 2 轮完成，Pinia store 就绪 |
| 商品详情页 | ✅ | 第 3 轮完成，待接入"我想要" |

### 风险点

| 风险 | 级别 | 缓解措施 |
|------|:--:|------|
| 订单状态机复杂度高（5 状态 × 2 角色 × 多操作 = 多分支 UI） | 🟡 中 | 后端状态机逻辑已完整实现，前端按"状态→按钮映射表"逐个实现 |
| 评价三维度 UI 在小屏幕上排列不便 | 🟢 低 | 纵向排列 + StarRating 三种尺寸适配 |
| `product_snapshot` JSON 解析失败 | 🟢 低 | 后端已用 `JSON.stringify`，前端 `JSON.parse` 安全兜底 |
| 定时任务在测试环境不易验证 | 🟢 低 | 单元测试直接调用 `cancelTimeoutOrders()` / `confirmTimeoutOrders()` |
| 幂等下单 → 直接跳转已有订单的 UX | 🟢 低 | 后端返回已有订单 id，前端直接 redirectTo |

---

## 与其他迭代的接口

| 接口点 | 依赖方 | 本迭代交付 |
|--------|--------|-----------|
| "我想要"按钮 | 本迭代自身 | ✅ 本迭代接入 |
| 订单详情"聊一聊"链接 | 第 5 轮（IM） | ✅ 已就绪，本迭代不修改 |
| 通知 TabBar 角标 | 第 5 轮（通知） | 🔲 订单状态变更时后端已推送 IM 系统消息 |
| 用户主页评价展示 | 第 12 轮（前端收尾） | ✅ 本迭代提供 `getUserReviews` API 封装 |
| 信誉分阈值前端校验 | 第 9 轮（通知+信誉分） | ✅ 本迭代在 store/getter + 下单按钮中实现 |

---

## 不纳入本轮的内容

- ❌ **管理后台订单管理** → 第 8 轮（管理后台）
- ❌ **举报/工单流程** → 第 7 轮
- ❌ **订单退款功能** → 非 MVP 范围（PRD 明确线下交易）
- ❌ **订单搜索/筛选（除 Tab 外的关键词搜索）** → 超 MVP 范围
- ❌ **订单导出/批量操作** → 非 MVP 范围
- ❌ **通知中心页面** → 第 5 轮已实现基础版，第 9 轮增强
- ❌ **信誉分规则配置面板** → 后端 config 已定义，前端仅展示

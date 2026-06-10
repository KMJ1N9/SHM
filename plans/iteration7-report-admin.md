# 第 7 轮编码计划：举报/管理后台/互评

> **状态：** ✅ 已完成（2026-06-10）
> **实际工时：** ~1.5 天（编码）+ 审计半天
> **目标：** 举报表单提交 → 客服工单流转 → 管理后台数据看板 + 用户/商品管理 → 互评完成度追踪（订单时间线节点 4 激活）
> **审计报告：** [[../memory/iteration7-audit.md]] — P0×0 / P1×4 / P2×5，综合评分 8.5/10
> **依据文档：** PRD §3.6（举报流程）、技术架构文档 §八（管理后台）、API 文档 §2.6~§2.16（举报+工单+管理+敏感词+审计）、测试计划 §3.9~§3.10、编码迭代计划 §第 7~8 轮

---

## 现状分析

### 后端 — 举报/管理基础设施 ~95% 完成 ✅

| 层 | 文件 | 状态 | 核心能力 |
|------|------|:--:|------|
| Service | `services/report.js` (75 行) | ✅ | create（校验不能举报自己 + 参与订单检查 + 重复举报拦截）、list（我的举报） |
| Repository | `repository/report.js` (283 行) | ✅ | CRUD + 状态流转 + resolveWithPenalty（事务内裁决+扣分+通知）+ AdminLog |
| Controller | `controllers/report.js` (34 行) | ✅ | create + list（仅"我的举报"视角） |
| Route | `routes/report.js` (33 行) | ✅ | POST(含 Joi 校验：4 种举报类型/10-1000 字描述/最多 6 张证据图) + GET |
| Service | `services/admin.js` (249 行) | ✅ | processTicket/resolveTicket/listTickets + banUser/unbanUser/listUsers + offShelfProduct + 敏感词 CRUD + 审计日志 |
| Controller | `controllers/admin.js` (233 行) | ✅ | 14 个端点全部就绪（工单 3 + 用户 3 + 商品 1 + 统计 4 + 敏感词 3 + 日志 1） |
| Route | `routes/admin.js` (88 行) | ✅ | cs 中间件（工单）+ admin 中间件（用户/商品/统计/敏感词/日志） |
| Service | `services/analytics.js` (107 行) | ✅ | overview/categories/searchKeywords/dashboard（含近 7 天趋势） |
| Middleware | `middleware/cs.js` (21 行) | ✅ | 检查 role ∈ {cs, admin} |
| Middleware | `middleware/admin.js` | ✅ | 检查 role === 'admin' |

**后端缺口（1 项）：**

| 缺口 | 说明 | 优先级 |
|------|------|:--:|
| `GET /api/reports/:id` 缺失 | 当前仅有 create + list（我的举报），缺少单条举报详情查询端点。前端 `pages/report/detail.vue` 需要此端点 | P0 |

### 前端 — 6 个文件全为占位 stub，1 个文件需修改

| 文件 | 状态 | 说明 |
|------|:--:|------|
| `api/report.js` | ❌ stub | 需实现 createReport / getReportList / getReportDetail |
| `api/admin.js` | ❌ stub | 需实现 14 个管理端点封装 |
| `pages/report/submit.vue` | ❌ stub | 举报表单：类型选择 + 关联对象 + 描述 + 证据图片 |
| `pages/report/detail.vue` | ❌ stub | 举报详情：状态时间线 + 客服处理结果 |
| `pages/admin/dashboard.vue` | ❌ stub | 数据看板：概览卡片 + 分类统计 + 搜索热词 |
| `pages/admin/tickets.vue` | ❌ stub | 工单列表 + 受理/裁决操作 |
| `pages/order/detail.vue` | ⚠️ 时间线节点 4 | 已评价节点始终显示"待完成"，需接入双方评价状态实时判断 |

---

## 架构关键决策

### 举报/工单状态机

```
reporter 提交 → pending ──cs受理──→ processing ──cs裁决──→ resolved
                    │                    │
                    │ (仅 pending 可受理)  │ (仅 processing 可裁决)
                    │                    │
                    └── 无超时自动处理 ────┘
```

- **角色权限矩阵：** `admin` = 全部权限；`cs` = 可受理/裁决工单，不可封号/看数据看板；`user` = 仅提交举报 + 查看自己的举报
- **重复举报拦截：** `hasActiveReport(reporterId, orderId)` — 同一用户对同一订单在 `status != 'resolved'` 时不可重复提交
- **裁决即扣分：** `resolveWithPenalty()` 在事务内原子执行：工单裁决 + 信誉分扣减(FOR UPDATE) + 通知写入
- **操作审计：** 每次管理员操作（受理/裁决/封禁/解封/下架）都写入 `admin_logs` 表

### 管理后台权限分层

| 功能域 | 路由前缀 | 中间件 | 角色 |
|--------|---------|--------|------|
| 工单管理 | `/api/admin/tickets` | `cs` | cs / admin |
| 用户管理 | `/api/admin/users` | `admin` | admin only |
| 商品管理 | `/api/admin/products` | `admin` | admin only |
| 数据统计 | `/api/admin/analytics` + `/dashboard` | `admin` | admin only |
| 敏感词库 | `/api/admin/sensitive` | `admin` | admin only |
| 审计日志 | `/api/admin/logs` | `admin` | admin only |

### 互评完成度追踪

- **时间线节点 4 激活条件：** 双方均已评价对方（`reviews` 表中存在两条 `order_id = X` 且 `reviewer_id` 分别为 buyer 和 seller 的记录）
- **状态显示：**
  - 0 人评价 → "待完成"（灰色）
  - 1 人评价（仅我/仅对方）→ "已评价 (1/2)" + 提示"等待对方评价"
  - 2 人评价 → "已完成互评"（高亮）

---

## 执行策略：单人多文件串行 + 验证门禁

> **决策日期：** 2026-06-10
> **方法：** 后端仅补 1 个端点（~20 行），前端 7 个文件串行实现。举报和管理后台是两个独立的视觉域，不存在共享视觉组件，Agent 并行不会造成风格冲突。

### 为什么本轮不使用 Agent 并行

| 因素 | 分析 |
|------|------|
| 后端改动极小 | 仅新增 1 个 controller 方法 + 1 条路由，~20 行，无需 Agent |
| 前端 6 文件独立 | 举报 3 文件 vs 管理 3 文件分属两个独立视觉域，各文件无共享 UI 模式 |
| 复杂度集中 | `pages/admin/dashboard.vue` 涉及数据看板（多 API 聚合 + 图表），需仔细设计 |
| 已有代码参考充分 | Round 5/6 的页面模式（列表/详情/弹窗/状态标签）可直接复用 |

### 执行顺序（按依赖拓扑）

```
Step 1: 后端补漏 ──→ GET /api/reports/:id (举报详情端点)
  ↓
Step 2: api/report.js ──→ 2 个端点封装 (create + list + detail)
Step 3: pages/report/submit.vue ──→ 举报表单 (依赖 Step 2)
Step 4: pages/report/detail.vue ──→ 举报详情页 (依赖 Step 2)
  ↓
Step 5: api/admin.js ──→ 14 个端点封装 (工单/用户/商品/统计/敏感词/日志)
Step 6: pages/admin/tickets.vue ──→ 工单处理页 (依赖 Step 5)
Step 7: pages/admin/dashboard.vue ──→ 数据看板 (依赖 Step 5)
  ↓
Step 8: 互评追踪增强 ──→ order/detail.vue 时间线节点 4 (依赖 Round 6 评价数据)
  ↓
Step 9: 全量验证 ──→ vitest + ESLint + build + 真机 E2E
```

### 预估工时分布

| 步骤 | 内容 | 预估 |
|:--:|------|:--:|
| 1 | 后端补漏 | 15 min |
| 2~4 | 举报前端（API + 表单 + 详情） | 3 h |
| 5 | 管理 API 封装 | 40 min |
| 6~7 | 管理前端（工单 + 看板） | 4 h |
| 8 | 互评追踪 | 1 h |
| 9 | 验证 | 1.5 h |
| **合计** | | **~10.5 h ≈ 2 天** |

---

## 分步计划（9 步，每步独立可验证）

### 步骤 1：后端 — 举报详情端点（~20 行改动）

**文件：** `server/src/controllers/report.js`（修改）+ `server/src/routes/report.js`（修改）

**1.1 新增 controller 方法：**

```js
/**
 * GET /api/reports/:id — 举报详情
 */
async detail(req, res, next) {
  try {
    const report = await reportService.detail(
      parseInt(req.params.id, 10),
      req.user.id
    );
    res.json({ code: 0, message: 'ok', data: report });
  } catch (err) {
    next(err);
  }
},
```

**1.2 新增 service 方法（`services/report.js`）：**

```js
/**
 * 举报详情（仅举报人本人或 cs/admin 可查看）
 */
async detail(reportId, userId) {
  const report = await reportRepo.findById(reportId);
  if (!report) {
    throw notFound('举报');
  }
  // 权限：本人的举报 或 cs/admin 角色（controller 层由 middleware 保证）
  return report;
},
```

**1.3 在 `routes/report.js` 新增路由：**

```js
router.get('/:id', reportController.detail);
```

> **注意：** `GET /:id` 必须放在 `GET /` 之后，否则 `list` 会被 `:id` 匹配。

**验证：** `curl http://localhost:3000/api/reports/1 -H "Authorization: Bearer <token>"` → 返回举报详情 JSON

---

### 步骤 2：前端 — 举报 API 模块（~50 行）

**文件：** `miniprogram/src/api/report.js`（替换占位）

```js
import { get, post } from './index';

/** 创建举报 */
export function createReport(data) {
  return post('/reports', data);
}

/** 我的举报列表 */
export function getReportList(params) {
  return get('/reports', params);
}

/** 举报详情 */
export function getReportDetail(id) {
  return get(`/reports/${id}`);
}
```

**验证：** 调用 `createReport` → 后端 reports 表新增记录 → 返回 201

---

### 步骤 3：前端 — 举报表单页（~350 行）

**文件：** `miniprogram/src/pages/report/submit.vue`（替换占位）

**功能规格：**

**3.1 页面入口：**
- 商品详情页右上角 `···` 菜单 → "举报商品" → 跳转本页，URL 携带 `?product_id=X&reported_user_id=Y`
- 聊天详情页长按消息 → "举报用户" → 跳转本页，URL 携带 `?reported_user_id=Y`
- 订单详情页 → "举报"按钮 → 跳转本页，URL 携带 `?order_id=X&product_id=Y&reported_user_id=Z`

**3.2 表单字段：**

```
┌──────────────────────────────────┐
│  [返回]         提交举报          │
├──────────────────────────────────┤
│                                  │
│  举报类型                         │
│  ┌────────────────────────────┐  │
│  │ ○ 商品与描述严重不符        │  │
│  │ ○ 对方辱骂/骚扰             │  │
│  │ ○ 疑似骗子                  │  │
│  │ ○ 其他                      │  │
│  └────────────────────────────┘  │
│                                  │
│  关联对象（自动带入，不可编辑）    │
│  ┌────────────────────────────┐  │
│  │ 商品：MacBook Pro 2022     │  │  ← 若有 product_id
│  │ 订单：#20241201001          │  │  ← 若有 order_id
│  │ 被举报人：小明              │  │  ← required
│  └────────────────────────────┘  │
│                                  │
│  问题描述（必填，10-1000 字）      │
│  ┌────────────────────────────┐  │
│  │ 请详细描述遇到的问题...     │  │
│  │                            │  │
│  └────────────────────────────┘  │
│  [字数计数] 0/1000               │
│                                  │
│  上传证据截图（选填，最多 6 张）   │
│  [ImageUploader]                 │
│                                  │
│  ─────────────────────────────── │
│  提交后客服将在 3 小时内处理      │  ← 提示文字
│                                  │
│  ┌────────────────────────────┐  │
│  │        提交举报             │  │  ← 主按钮
│  └────────────────────────────┘  │
└──────────────────────────────────┘
```

**3.3 关键逻辑：**

```js
// 校验链：
// 1. 未登录 → 跳转登录页
// 2. 举报类型未选择 → Toast "请选择举报类型"
// 3. 描述 < 10 字 → Toast "请至少输入 10 个字描述问题"
// 4. 提交 → createReport → 成功 → Toast "已收到，客服将在 3 小时内响应" → navigateBack

// 错误处理：
// - 3006: 已有进行中的举报 → Toast "你已对该订单提交过举报，请等待处理"
// - 3002: 订单状态非法 → Toast 对应消息
```

**3.4 数据获取：**
- `onLoad` 解析 URL 参数 `reported_user_id`（必填）、`product_id`（选填）、`order_id`（选填）
- 如果有 `product_id` → 调用 `getProductDetail` 获取商品标题（展示用）
- 如果有 `order_id` → 展示订单号

**验证：** 进入提交页 → 填写表单 → 提交 → 成功 toast → 返回上一页 → 数据库 `reports` 表有新记录

---

### 步骤 4：前端 — 举报/工单详情页（~280 行）

**文件：** `miniprogram/src/pages/report/detail.vue`（替换占位）

**4.1 页面布局：**

```
┌──────────────────────────────────┐
│  [返回]         举报详情          │
├──────────────────────────────────┤
│  ── 处理进度 ──                  │
│  ● 已提交          2024-12-01    │
│  ● 客服处理中      2024-12-01    │  ← 当前状态高亮
│  ○ 已处理          待完成        │
├──────────────────────────────────┤
│  ── 举报内容 ──                  │
│  举报类型：商品与描述严重不符     │
│  关联商品：MacBook Pro 2022      │  ← 点击跳转商品详情
│  关联订单：#20241201001          │  ← 点击跳转订单详情
│  被举报人：小明                  │
│                                  │
│  问题描述：                      │
│  收到的商品与描述严重不符...     │
│                                  │
│  证据截图：                      │
│  [图1] [图2] [图3]              │  ← 点击预览大图
├──────────────────────────────────┤
│  ── 处理结果 ──                  │
│  （pending/processing 时显示）    │
│  客服正在处理中，请耐心等待...    │
│                                  │
│  （resolved 时显示）              │
│  处理结论：举报属实，商品已下架   │
│  处理时间：2024-12-02 15:30     │
└──────────────────────────────────┘
```

**4.2 状态时间线（3 节点）：**

| 节点 | 标签 | 激活条件 |
|:--:|------|------|
| 1 | 已提交 | 始终激活，时间 = `created_at` |
| 2 | 客服处理中 | `status ∈ {processing, resolved}` 时激活，无独立时间字段 |
| 3 | 已处理 | `status === 'resolved'` 时激活，时间 = `resolved_at`，显示 `resolution` |

**4.3 数据获取：**
- `onLoad` → `getReportDetail(id)` → 渲染
- `onShow` → 重新拉取（处理结果更新后返回）

**验证：** 进入详情页 → 显示举报内容 + 状态时间线 → pending 工单显示"处理中"提示 → resolved 工单显示裁决结果

---

### 步骤 5：前端 — 管理后台 API 模块（~120 行）

**文件：** `miniprogram/src/api/admin.js`（替换占位）

```js
import { get, post, put } from './index';

// ============================================================
// 工单管理（cs + admin）
// ============================================================
export function getTicketList(params) { return get('/admin/tickets', params); }
export function processTicket(id) { return put(`/admin/tickets/${id}/process`); }
export function resolveTicket(id, data) { return put(`/admin/tickets/${id}/resolve`, data); }

// ============================================================
// 用户管理（admin only）
// ============================================================
export function getUserList(params) { return get('/admin/users', params); }
export function banUser(id) { return put(`/admin/users/${id}/ban`); }
export function unbanUser(id) { return put(`/admin/users/${id}/unban`); }

// ============================================================
// 商品管理（admin only）
// ============================================================
export function offShelfProduct(id) { return put(`/admin/products/${id}/off-shelf`); }

// ============================================================
// 数据统计（admin only）
// ============================================================
export function getDashboard() { return get('/admin/dashboard'); }
export function getAnalyticsOverview() { return get('/admin/analytics/overview'); }
export function getAnalyticsCategories() { return get('/admin/analytics/categories'); }
export function getAnalyticsSearchKeywords() { return get('/admin/analytics/search-keywords'); }

// ============================================================
// 敏感词库（admin only）
// ============================================================
export function getSensitiveStats() { return get('/admin/sensitive/stats'); }
export function reloadSensitiveWords() { return post('/admin/sensitive/reload'); }
export function checkSensitiveText(text) { return post('/admin/sensitive/check', { text }); }

// ============================================================
// 审计日志（admin only）
// ============================================================
export function getAdminLogs(params) { return get('/admin/logs', params); }
```

**验证：** admin 用户调用 `getDashboard()` → 返回平台概览数据（含 total_users / total_products / total_orders / pending_reports / new_users_7d / completed_orders_7d）

---

### 步骤 6：前端 — 工单处理页（~400 行）

**文件：** `miniprogram/src/pages/admin/tickets.vue`（替换占位）

**6.1 页面布局：**

```
┌──────────────────────────────────┐
│  工单管理                         │
├──────────────────────────────────┤
│  [全部] [待处理] [处理中] [已处理] │  ← 状态 Tab
│  [全部类型] [描述不符] [骚扰] ...  │  ← 类型筛选（可选下拉）
├──────────────────────────────────┤
│  ┌────────────────────────────┐  │
│  │ #1001  描述不符    pending │  │
│  │ 举报人：小明 → 被举报：小红 │  │
│  │ 关联订单：#20241201001     │  │
│  │ 2024-12-01 10:30          │  │
│  │              [受理] [详情] │  │  ← 操作按钮
│  └────────────────────────────┘  │
│  ┌────────────────────────────┐  │
│  │ #1002  辱骂骚扰   processing│  │
│  │ ...                        │  │
│  │              [裁决] [详情] │  │
│  └────────────────────────────┘  │
│          ...                     │
│  没有更多工单了                   │
└──────────────────────────────────┘
```

**6.2 关键功能：**

1. **工单列表：**
   - 调用 `getTicketList({ status, type, page, pageSize })`
   - 状态 Tab 过滤：pending / processing / resolved / all
   - 下拉刷新 + 触底加载

2. **受理工单（pending → processing）：**
   - 点击「受理」→ `uni.showModal` 确认 → `processTicket(id)`
   - 成功后列表刷新，状态变为 processing
   - 失败 → Toast 错误消息（工单已被他人受理等）

3. **裁决工单（processing → resolved）：**
   - 点击「裁决」→ 弹出裁决弹窗
   - 弹窗内容：
     ```
     ┌──────────────────────────────┐
     │  裁决工单 #1002               │
     │                              │
     │  处理结论（必填）              │
     │  ┌──────────────────────────┐│
     │  │ 举报属实，商品已下架...   ││
     │  └──────────────────────────┘│
     │                              │
     │  扣减信誉分（选填，0-100）     │
     │  [___30___]                  │
     │                              │
     │  [取消]          [确认裁决]   │
     └──────────────────────────────┘
     ```
   - 调用 `resolveTicket(id, { resolution, deduct_credit })`

4. **查看详情：**
   - 点击「详情」→ `navigateTo` `pages/report/detail?id=xxx`
   - 复用举报详情页

**6.3 权限入口：**
- 页面 `onShow` 时检查 `userStore.user.role ∈ {cs, admin}`
- 非授权用户 → Toast "仅客服和管理员可访问" → `navigateBack`
- 管理后台页面入口放在"我的"页面，仅 `cs/admin` 可见

**验证：** admin 登录 → 进入工单管理 → 查看 pending 工单 → 受理 → 裁决 → 工单变为 resolved → 被举报人信誉分扣减

---

### 步骤 7：前端 — 数据看板页（~350 行）

**文件：** `miniprogram/src/pages/admin/dashboard.vue`（替换占位）

**7.1 页面布局：**

```
┌──────────────────────────────────┐
│  数据看板                         │
├──────────────────────────────────┤
│  ┌──────────┬──────────┐        │
│  │ 注册用户  │ 活跃商品  │        │
│  │  1,234   │   456    │        │
│  └──────────┴──────────┘        │
│  ┌──────────┬──────────┐        │
│  │ 交易订单  │ 待处理举报│        │
│  │   789    │    12    │        │
│  └──────────┴──────────┘        │
│  ┌────────────────────────────┐  │
│  │  近 7 天                      │
│  │  新增用户：+89               │
│  │  完成交易：+156              │
│  └────────────────────────────┘  │
├──────────────────────────────────┤
│  ── 热门分类 ──                  │
│  电子产品  ████████████ 45%      │
│  教材      ██████ 25%            │
│  生活用品  ████ 15%              │
│  服饰      ███ 10%              │
│  其他      ██ 5%                │
├──────────────────────────────────┤
│  ── 热门搜索词 ──                │
│  [教材] [iPhone] [MacBook]      │
│  [生活用品] [显示器] [键盘]      │
│  [自行车] [相机] [耳机]          │
├──────────────────────────────────┤
│  ── 近 7 天每日新增订单 ──       │
│  12/01  ▓▓▓▓▓▓▓▓ 23            │
│  12/02  ▓▓▓▓▓▓▓▓▓▓ 28          │
│  12/03  ▓▓▓▓▓▓▓ 18             │
│  12/04  ▓▓▓▓▓▓▓▓▓ 25           │
│  12/05  ▓▓▓▓▓▓▓▓▓▓▓ 31         │
│  12/06  ▓▓▓▓▓▓▓▓ 21            │
│  12/07  ▓▓▓▓▓▓▓▓▓▓ 27          │
├──────────────────────────────────┤
│  ┌────────────────────────────┐  │
│  │  敏感词库：437 词    [重载] │  │  ← 敏感词管理入口
│  └────────────────────────────┘  │
└──────────────────────────────────┘
```

**7.2 数据源：**
- `getDashboard()` → 一次性返回 overview + hot_categories + hot_keywords + daily_orders_7d
- `getSensitiveStats()` → 敏感词库统计
- 不使用第三方图表库 — 用纯 CSS/SCSS 实现进度条和简易柱状图（控制包体积）

**7.3 简易柱状图实现：**
```scss
// 用 flex + 百分比 width 模拟柱状图
.bar-item {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 8rpx 0;
}
.bar-fill {
  height: 32rpx;
  background: $color-primary-gradient;
  border-radius: 4rpx;
  min-width: 4rpx;
  transition: width 0.5s ease;
}
```

**7.4 权限检查：**
- 同步骤 6.3，`onShow` 时检查 `role === 'admin'`
- 非 admin → 重定向

**验证：** admin 登录 → 看板加载 → 6 个概览数字正确 → 分类百分比求和 100% → 敏感词数量显示 → 重载敏感词成功

---

### 步骤 8：互评完成度追踪（~80 行改动）

**文件：** `miniprogram/src/pages/order/detail.vue`（修改）

**8.1 时间线节点 4 激活逻辑：**

当前：`timelineActive(3)` 始终返回 `false`，节点 4 永远灰色。

修改为根据双方评价状态动态判断：

```js
/** 双方互评完成度 */
const reviewCompletion = computed(() => {
  const total = reviews.value.length; // 当前订单的评价总数
  if (total === 0) return { done: 0, total: 2, active: false, label: '待完成' };
  if (total === 1) return { done: 1, total: 2, active: false, label: '已评价 (1/2)' };
  return { done: 2, total: 2, active: true, label: '已完成互评' };
});

// timelineActive(3) 改为：
if (nodeIndex === 3) return reviewCompletion.value.active;
```

**8.2 模板修改：**

```html
<!-- 节点 4：已评价 — 动态显示完成度 -->
<view class="tl-node" :class="{ 'tl-node--active': timelineActive(3) }">
  <view class="tl-dot" />
  <view class="tl-content">
    <text class="tl-label">
      {{ reviewCompletion.label }}
    </text>
    <text v-if="reviewCompletion.done === 1" class="tl-time">
      等待对方评价
    </text>
    <text v-else-if="reviewCompletion.done === 0" class="tl-time tl-time--pending">
      待完成
    </text>
  </view>
</view>
```

**8.3 互评摘要（评价信息 section 增强）：**

在评价信息 section 顶部添加互评进度条：

```
┌──────────────────────────────────┐
│  评价信息                         │
│  互评进度  ●──────────○  1/2     │  ← 进度条
│  等待对方评价...                  │
├──────────────────────────────────┤
│  (已有的评价列表...)              │
└──────────────────────────────────┘
```

```html
<!-- 互评进度条 -->
<view class="review-progress">
  <view class="review-progress-bar">
    <view class="review-progress-fill" :style="{ width: (reviewCompletion.done / 2 * 100) + '%' }" />
  </view>
  <text class="review-progress-text">{{ reviewCompletion.done }}/2</text>
</view>
<text v-if="reviewCompletion.done === 1" class="review-progress-hint">
  {{ myReview ? '对方尚未评价，评价后双方可见' : '你尚未评价，请先完成评价' }}
</text>
<text v-else-if="reviewCompletion.done === 0" class="review-progress-hint">
  双方完成评价后可见评价详情
</text>
```

**验证：** 
- 订单 completed 无评价 → 节点 4 灰色 "待完成" → 进度条 0/2
- 我方评价后 → 节点 4 灰色 "已评价 (1/2)" → 进度条 1/2 → 提示"等待对方评价"
- 双方互评后 → 节点 4 高亮 "已完成互评" → 进度条 2/2

---

### 步骤 9：全量验证 + 状态更新

- [ ] `npx vitest run` 全部通过（含已有 9 文件 126 用例）
- [ ] 前端 `npm run lint` ESLint 通过（0 错误）
- [ ] 前端 `npm run build:mp-weixin` 编译成功
- [ ] 微信开发者工具端到端验证：
  - 举报表单：从商品详情 → 举报 → 填写 → 提交 → Toast ✅
  - 举报详情：提交后跳转详情 → 显示 pending 时间线 → "处理中"提示 ✅
  - 重复举报拦截：同一订单再次举报 → Toast "已有进行中的举报" ✅
  - 工单管理：admin 登录 → 工单列表 → 受理 → 裁决 → 工单 resolved ✅
  - 数据看板：admin 登录 → 看板加载 → 数字正确 → 分类条渲染 ✅
  - 互评进度：completed 订单 → 评价前后时间线节点 4 变化 → 进度条更新 ✅
  - 非 cs/admin 访问管理页面 → 权限拦截 ✅
- [ ] 后端新增端点验证：
  - `GET /api/reports/:id` → 本人可查 → 非本人 403 ✅
- [ ] 更新 `memory/project-state.md` — 记录第 7 轮完成状态
- [ ] 更新 `memory/known-bugs.md` — 如有发现 Bug
- [ ] 更新 `memory/MEMORY.md`

---

## 文件改动清单

| # | 文件 | 操作 | 预计行数 |
|:--:|------|:--:|:--:|
| 1 | `server/src/controllers/report.js` | 修改（+detail） | +12 |
| 2 | `server/src/services/report.js` | 修改（+detail） | +15 |
| 3 | `server/src/routes/report.js` | 修改（+GET /:id） | +1 |
| 4 | `miniprogram/src/api/report.js` | 重写 | ~50 |
| 5 | `miniprogram/src/api/admin.js` | 重写 | ~120 |
| 6 | `miniprogram/src/pages/report/submit.vue` | 重写 | ~350 |
| 7 | `miniprogram/src/pages/report/detail.vue` | 重写 | ~280 |
| 8 | `miniprogram/src/pages/admin/tickets.vue` | 重写 | ~400 |
| 9 | `miniprogram/src/pages/admin/dashboard.vue` | 重写 | ~350 |
| 10 | `miniprogram/src/pages/order/detail.vue` | 修改（互评追踪） | ~80 |

**总计：** 3 后端修改 + 4 前端重写 + 1 前端修改 ≈ 1658 行

---

## 验证清单（9 项）

| # | 验证项 | 方法 |
|:--:|------|------|
| 1 | 举报表单提交成功 | POST /api/reports → 201 → reports 表新增记录 |
| 2 | 重复举报拦截 | 同一人对同一订单再次提交 → 返回 3006 |
| 3 | 举报详情端点可用 | GET /api/reports/:id → 返回完整举报数据 |
| 4 | 工单受理流转 | pending → put process → processing |
| 5 | 工单裁决 + 信誉分联动 | processing → put resolve {deduct_credit:30} → resolved + 被举报人信誉分-30 + notifications 表有新记录 |
| 6 | 数据看板数字正确 | GET /api/admin/dashboard → 返回数据与数据库 COUNT 一致 |
| 7 | 权限隔离 | user 角色访问 /api/admin/tickets → 5001（需要客服权限） |
| 8 | 互评进度实时更新 | 评价前 → 0/2 灰色 → 评价后 → 1/2 提示 → 双方评完 → 2/2 高亮 |
| 9 | ESLint + Build 通过 | 0 错误 |

---

## 依赖与风险

### 阻塞依赖

| 依赖 | 状态 | 说明 |
|------|:--:|------|
| 后端举报 API | ✅ | create + list 已就绪，需补 detail |
| 后端管理 API | ✅ | 14 个端点全就绪，含角色权限中间件 |
| 后端数据分析 API | ✅ | analytics service 已就绪，4 端点可用 |
| ImageUploader 组件 | ✅ | 第 3 轮完成，举报表单直接复用 |
| 用户登录态 + 角色信息 | ✅ | Pinia store 含 user.role |
| 订单详情页 | ✅ | 第 6 轮完成，互评追踪在此基础上修改 |
| StarRating 组件 | ✅ | 第 6 轮完成（v-model 协议已修正） |

### 风险点

| 风险 | 级别 | 缓解措施 |
|------|:--:|------|
| 管理后台权限前端可绕过（直接改 localStorage） | 🟢 低 | 后端 middleware 是真正的权限边界，前端仅做 UI 隐藏 |
| 举报详情权限：非本人查看自己举报 | 🟡 中 | 在 service 层增加 reporter_id 校验，cs/admin 角色不受限 |
| 数据看板在小程序屏幕排版困难 | 🟡 中 | 使用卡片式布局，每行最多 2 个指标，柱状图用纯 CSS |
| 工单裁决弹窗中扣分输入校验 | 🟢 低 | Joi 校验范围 0-100，前端 number input 限制 |
| 互评进度依赖 reviews 数组正确加载 | 🟢 低 | loadOrder() 已用 Promise.all 并行加载，.catch 降级 |

---

## 与其他迭代的接口

| 接口点 | 依赖方 | 本迭代交付 |
|--------|--------|-----------|
| 举报入口（商品详情 ··· 菜单） | 第 3 轮（商品详情） | 本迭代接入 → `navigateTo /pages/report/submit` |
| 举报入口（订单详情） | 第 6 轮（订单详情） | 本迭代在订单详情页添加"举报"按钮（仅 completed 订单） |
| 工单裁决 → 信誉分联动 | 第 6 轮（信誉分） | 后端已实现（resolveWithPenalty 事务），本迭代前端接入 |
| 管理后台入口（"我的"页面） | 第 12 轮（前端收尾） | 本迭代在 `pages/user/me.vue` 添加管理后台入口（仅 cs/admin 可见） |
| 互评追踪 | 第 6 轮（评价+订单详情） | 本迭代增强时间线节点 4 + 互评进度条 |
| 通知推送（裁决结果） | 第 5 轮（IM 通知） | 后端已实现（resolveWithPenalty 写入 notifications 表） |

---

## 不纳入本轮的内容

- ❌ **管理后台 — 用户列表/封禁 UI** → 后端 API 已就绪，前端管理页面建议在第 12 轮（前端收尾）统一实现，本轮仅实现工单管理 + 数据看板
- ❌ **管理后台 — 审计日志查看 UI** → 同上，后端已就绪，前端可后续补
- ❌ **管理后台 — 敏感词库管理 UI（reload/check 之外的操作）** → 当前仅展示词数和重载按钮
- ❌ **举报消息推送给被举报人** → IM 系统消息推送后端已有框架（`services/im/tencent.js`），本轮不新增推送触发点（避免骚扰）
- ❌ **订单 `disputed` 状态处理** → API 文档提到举报后订单进入 `disputed` 状态，但当前 order 状态机只有 pending/met/completed/cancelled 四种状态。此功能需数据库 schema 变更，纳入后续迭代
- ❌ **管理后台商品下架 UI** → 后端 API 已就绪，前端管理页面在第 12 轮统一实现

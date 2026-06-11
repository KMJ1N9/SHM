# 第 9 轮编码计划：收尾补全 + P1 修复 + 通知/信誉分收尾

> **状态：** ✅ 已完成
> **完成日期：** 2026-06-11
> **预估工时：** ~10 h ≈ 1.5 天
> **目标：** 修复第 7/8 轮审阅全部 P1 项 → 补全 6 个 stub 页面 → 通知中心入口 + 未读 badge → 信誉分阈值前端预检 → 补 1 个后端测试
> **依据文档：** PRD §3.8（信誉分）、技术架构文档 §四~§八、API 文档 §2.13~§2.16、编码迭代计划 §第 9 轮、第 7/8 轮审阅报告

---

## 现状分析

### 0.1 意外收获：通知 + 信誉分页面已完成 ✅

原计划 Round 9 核心 scope 是"通知中心 + 信誉分页面"，但在第 5/6 轮编码中已提前完成：

| 文件 | 状态 | 行数 | 完成于 | 说明 |
|------|:--:|:--:|:--:|------|
| `pages/notification/index.vue` | ✅ | 530 行 | 第 5 轮 | 类型 Tab 筛选 + 分页 + 标记已读 + 未读圆点 + 下拉刷新 |
| `api/notification.js` | ✅ | 49 行 | 第 5 轮 | list / unreadCount / markRead / markAllRead |
| `pages/user/credit.vue` | ✅ | 407 行 | 第 6 轮 | 信誉分大数字 + 权限阈值表 + 变动记录列表 |
| `api/credit.js` | ✅ | 31 行 | 第 6 轮 | getMyCredit / getUserCredit |
| `pages/user/reviews.vue` | ✅ | 第 6 轮 | 评价记录页（含 StarRating 只读展示） |
| `pages/report/list.vue` | ✅ | 第 7 轮 | 我的举报列表 |

**结论：** Round 9 核心 scope 已提前交付。本轮实际工作转为 **收尾补全 + P1 修复**。

### 0.2 后端现状 — ~98% 完成 ✅

**全部 57 个文件有实质代码，5 层架构完整。第 7 轮已实现所有管理端 API（含 `GET /api/admin/products` + `GET /api/users/:id` + `PUT /api/users/profile`），scheduler.js 4 个定时任务全部已填充完整逻辑（含 IM 通知 + 数据库备份 + 归档）。**

| 层 | 关键发现 |
|------|------|
| Service | `adminService.listProducts()` → `productRepo.listAll()` **已存在**，支持 `{keyword, status, sort, page, pageSize}` |
| Controller | `adminController.listProducts()` **已存在**；`userController.updateProfile()` + `getById()` **已存在** |
| Route | `GET /api/admin/products` **已注册**（第 7 轮），`PUT /api/users/profile` **已注册**，`GET /api/users/:id` **已注册** |
| Repo | `productRepo.listAll()` **已存在**（204 行），`userRepo.updateProfile()` **已存在**，`orderRepo.findTimeoutPending/Met()` **已存在** |
| Scheduler | `scheduler.js` **265 行全量实现**：超时取消(含商品恢复+IM通知) + 超时确认(含信誉加分+IM通知) + mysqldump 备份(含 30 天轮转) + admin_logs/reviews 归档 |
| Frontend API | `api/admin.js` 中 `getAdminProducts(params)` **已封装**（第 7 轮） |

**结论：后端本轮仅需补 1 个 report detail 端点测试（P1-04）。其余全部就绪。**

### 0.3 前端 — 6 个 stub 页面 + 2 个缺口

| 文件 | 状态 | 当前内容 |
|------|:--:|------|
| `pages/user/edit.vue` | ❌ stub | `<template><view>编辑资料 — 待实现</view></template>` |
| `pages/user/profile.vue` | ❌ stub | `<template><view>个人主页 — 待实现</view></template>` |
| `pages/user/settings.vue` | ❌ stub | `<template><view>设置 — 待实现</view></template>` |
| `pages/about/index.vue` | ❌ stub | `<template><view>关于我们 — 待实现</view></template>` |
| `pages/error/not-found.vue` | ❌ stub | `<template><view>404 — 待实现</view></template>` |
| `pages/error/network.vue` | ❌ stub | `<template><view>网络异常 — 待实现</view></template>` |
| `pages/review/create.vue` | ❌ stub | `<template><view>创建评价 — 待实现</view></template>` ⚠️ 评价功能已在 `order/detail.vue` 弹窗中实现，此页面可能废弃 |
| `pages/user/me.vue` | ⚠️ 缺通知入口 | 当前菜单：订单/发布/评价/信誉分/举报 — **缺通知中心入口 + 未读 badge** |
| `pages/product/detail.vue` | ⚠️ 缺信誉分预检 | "我想要"按钮无信誉分 ≥30 前端预检（仅依赖后端 4009 错误兜底） |
| `pages/product/publish.vue` | ⚠️ 缺信誉分预检 | 发布按钮无信誉分 ≥60 前端预检（仅依赖后端错误兜底） |

### 0.4 P1 待修复项（第 7 轮审阅 4 项 + 第 8 轮审阅 3 项）

| ID | 来源 | 文件 | 问题 | 预计 |
|:--:|:--:|------|------|:--:|
| P1-01 | R7 | `admin/tickets.vue:343` | `closeResolveModal()` 不检查 `submitting` 状态 | 5 min |
| P1-02 | R7 | `admin/dashboard.vue:155` + `tickets.vue:74` | 权限守卫使用模块级同步判断（`userRole` 在 `<script setup>` 顶层求值），应在模板 `v-if` 中使用 `computed` | 15 min |
| P1-03 | R7 | `order/detail.vue:712` | 本地评价插入用 `Date.now()` 作临时 ID，加 `temp_` 前缀避免潜在冲突 | 2 min |
| P1-04 | R7 | `server/__tests__/` | 缺少 `GET /api/reports/:id` 端点测试（3 条用例） | 20 min |
| P1-001 | R8 | `components/AppNavbar.vue:73` | `@back` 在 `defineEmits` 中声明但 `handleBack()` 从未 `emit('back')` | 3 min |
| P1-002 | R8 | `pages/admin/products.vue:162` | `activeStatus` 变量被设置但未传递给 API，状态 Tab 筛选无效 | 5 min |
| P1-003 | R8 | `pages/admin/products.vue:120` | 使用公开 `GET /products` 而非已有的 `GET /api/admin/products`，仅能看到在售商品 | 10 min |

---

## 架构关键决策

### 决策 1：review/create.vue 页面的去留

```
当前状态：
  - review/create.vue — 1 行 stub "创建评价 — 待实现"
  - order/detail.vue — 已有完整评价弹窗（三维 StarRating + 提交 + 展示）
  - pages.json — 已注册 /pages/review/create 路由

分析：
  - 评价的唯一入口是"已完成订单详情页"——用户在订单完成后评价交易对方
  - 独立的 review/create.vue 需要传递 order_id + reviewee_id，入口难以设计
  - order/detail.vue 的评价弹窗已完整闭环（BUG-028/029/030 修复后）

决策：废弃 review/create.vue
  - 在 pages.json 中移除路由或标记为 disabled
  - 在文件中添加注释说明废弃原因
  - 如后续需要"查看全部评价"入口，可复用 pages/user/reviews.vue
```

### 决策 2：信誉分阈值检查策略

```
检查位置          阈值      前端预检      后端校验
────────────────────────────────────────────────
发布商品按钮       ≥60      ✅ 本轮实现    ✅ 已存在（services/product.js create()）
"我想要"按钮       ≥30      ✅ 本轮实现    ✅ 已存在（services/order.js create() → 错误码 4009）
IM 聊天           不限      不需要       不需要

实现方式：
  - 前端预检：从 userStore.user.credit_score 读取当前信誉分
  - 低于阈值时：按钮置灰（disabled）+ 长按可查看原因 toast
  - 后端校验：保持不变（真正的安全边界），前端预检仅优化 UX（提前告知，避免用户填完表单后才被拒绝）
```

### 决策 3：AppNavbar 组件的定位

```
当前：
  - AppNavbar 已实现（140 行）但未被任何页面使用
  - 所有管理页面使用系统导航栏（navigationBarBackgroundColor: '#4A90D9'）
  
本轮策略：
  - 暂不强制替换已有页面导航栏（避免大规模改动引入风险）
  - 在 error 页面中使用 AppNavbar（error 页面需要自定义导航栏）
  - 标记组件状态为"可用但可选"——新页面可选择使用，旧页面保持现状
```

### 决策 4：定时任务实现范围

```
当前 scheduler.js 骨架：
  - 每 5 分钟：超时未确认的订单自动取消（24h）
  - 每 5 分钟：已面交但超时未确认的订单自动确认（72h）
  - 每日凌晨 3 点：数据库备份
  - 工作日凌晨 4 点：归档 6 个月以上的 admin_logs

本轮实现：
  - ✅ 订单超时取消（orderRepo.findTimeoutPending + updateStatus）
  - ✅ 订单超时确认（orderRepo.findTimeoutMet + updateStatus）
  - 🔲 数据库备份（需 mysqldump 或手动备份脚本，非紧急）
  - 🔲 admin_logs 归档（数据量小，非紧急）
```

---

## 执行策略：单人串行 + 按依赖度排序

> **决策日期：** 2026-06-11
> **方法：** P1 修复优先（5 分钟级单项）→ 后端补全 → 前端 stub 页面串行实现 → 信誉分预检 → 定时任务 → 全量验证

### 为什么本轮不使用 Agent 并行

| 因素 | 分析 |
|------|------|
| P1 修复粒度极小 | 6 项单项均 ≤15 分钟，逐个修复比 Agent 调度开销更低 |
| Stub 页面互不依赖 | 6 个 stub 页面分属 4 个独立视觉域（用户/错误/关于/设置），但每页非常简单（~80-300 行） |
| 后端改动极少 | 仅补 1 个 report detail 端点测试 ~50 行，后端 0 新功能代码 |
| 已有代码模式丰富 | 所有页面模式（列表/卡片/空状态/权限守卫）在前 8 轮已充分验证 |

### 执行顺序（按依赖拓扑）

```
Phase 1: P1 修复（6 项，~60 min）
  ├── P1-001 (R8): AppNavbar @back emit → 3 min
  ├── P1-003 (R8): products.vue import 切换 → 10 min
  ├── P1-002 (R8): products.vue activeStatus 传参 → 5 min (与 P1-003 合并)
  ├── P1-01 (R7): tickets.vue closeResolveModal → 5 min
  ├── P1-02 (R7): dashboard + tickets 权限守卫 → 15 min
  └── P1-03 (R7): order/detail.vue temp_ 前缀 → 2 min
      ↓
Phase 2: 后端测试（1 项，~20 min）
  └── P1-04 (R7): report detail 端点测试 (3 用例) → 20 min
      ↓
Phase 3: Stub 页面（7 个，~4.5 h）
  ├── Step 3.1: pages/user/edit.vue — 编辑个人资料 → 1 h
  ├── Step 3.2: pages/user/profile.vue — 用户个人主页 → 1.5 h
  ├── Step 3.3: pages/user/settings.vue — 设置页 → 30 min
  ├── Step 3.4: pages/about/index.vue — 关于我们 → 20 min
  ├── Step 3.5: pages/error/not-found.vue — 404 页 → 30 min
  ├── Step 3.6: pages/error/network.vue — 网络异常页 → 20 min
  └── Step 3.7: pages/review/create.vue — 标记废弃 → 5 min
      ↓
Phase 4: 通知 + 信誉分收尾（3 项，~1 h）
  ├── Step 4.1: me.vue 通知中心入口 + 未读 badge → 30 min
  ├── Step 4.2: product/detail.vue "我想要"信誉分预检 → 15 min
  └── Step 4.3: product/publish.vue 发布按钮信誉分预检 → 15 min
      ↓
Phase 5: 全量验证 → 1 h
```

### 预估工时分布

| Phase | 内容 | 预估 |
|:--:|------|:--:|
| 1 | P1 修复 (6 项) | 1 h |
| 2 | 后端测试 (1 项) | 20 min |
| 3 | Stub 页面 (7 个) | 4.5 h |
| 4 | 通知 + 信誉分收尾 (3 项) | 1 h |
| 5 | 全量验证 | 1 h |
| **合计** | | **~8 h ≈ 1~1.5 天** |

---

## 分步计划

---

### Phase 1：P1 修复（6 项）

---

#### P1-001 (R8)：AppNavbar @back 事件触发

**文件：** `miniprogram/src/components/AppNavbar.vue`（修改）

```js
// 修改前 (line 73-75)
function handleBack() {
  uni.navigateBack({ delta: 1 });
}

// 修改后
const emit = defineEmits(['back', 'rightClick']);

function handleBack() {
  emit('back');
  uni.navigateBack({ delta: 1 });
}
```

> 注意：`emit('back')` 先于 `navigateBack` 触发，父组件可在 `@back` 中执行自定义逻辑（如保存草稿）。当前无消费者，不影响功能。

**验证：** `npm run lint` 0 errors → `npm run build:mp-weixin` 成功

---

#### P1-003 (R8)：products.vue 切换为管理端 API

**文件：** `miniprogram/src/pages/admin/products.vue`（修改）

**问题根因：** 后端 `GET /api/admin/products` 已在第 7 轮完整实现（controller + route + service → `productRepo.listAll()`），前端 `api/admin.js` 已封装 `getAdminProducts(params)`。`products.vue` 在第 8 轮编码时误用公开 API。

**修改：**

```js
// 修改前 (line 120)
import { list as getProducts } from '@/api/product';

// 修改后
import { getAdminProducts } from '@/api/admin';
```

```js
// 修改前 (line 162-166) — fetchProducts 未传递 status
const result = await getProducts({
  keyword: keyword.value || undefined,
  page: targetPage,
  pageSize: PAGE_SIZE,
});

// 修改后 — 传递 activeStatus
const result = await getAdminProducts({
  keyword: keyword.value || undefined,
  status: activeStatus.value || undefined,
  page: targetPage,
  pageSize: PAGE_SIZE,
});
```

同时移除不再需要的 `import { list as getProducts }`。

**影响分析：**
- `GET /api/admin/products` → `admin` 中间件（仅 admin 角色）→ 安全性提升
- 支持 `status` 参数（空=全部, active, off_shelf, sold, reserved）→ 修复"已下架"/"已售" Tab 永远为空的问题
- 响应格式与 `GET /products` 一致（`{list, total, page, pageSize}`），前端无需修改解析逻辑

**验证：** admin 登录 → 商品管理 → 切换"已下架"Tab → 显示已下架商品（非空）

---

#### P1-002 (R8)：products.vue activeStatus 传递（与 P1-003 合并修复）

**文件：** `miniprogram/src/pages/admin/products.vue`（修改）

P1-003 的修复（传递 `status: activeStatus.value || undefined`）同步解决了 P1-002。无需额外改动。

**验证：** 同 P1-003

---

#### P1-01 (R7)：tickets.vue 裁决弹窗防误关

**文件：** `miniprogram/src/pages/admin/tickets.vue`（修改）

```js
// 修改前
function closeResolveModal() {
  resolveModal.visible = false;
  resolveModal.ticket = null;
}

// 修改后
function closeResolveModal() {
  if (resolveModal.submitting) return;
  resolveModal.visible = false;
  resolveModal.ticket = null;
}
```

同时模板遮罩层点击处加守卫：
```html
<!-- 修改前 -->
<view class="modal-overlay" @click="closeResolveModal">

<!-- 修改后 -->
<view class="modal-overlay" @click="resolveModal.submitting ? null : closeResolveModal()">
```

**验证：** 打开裁决弹窗 → 点击裁决 → 在 submitting 期间点击遮罩 → 弹窗不关闭

---

#### P1-02 (R7)：dashboard.vue + tickets.vue 权限守卫改用 computed

**文件：** `miniprogram/src/pages/admin/dashboard.vue` + `tickets.vue`（修改）

**问题：** 两个页面在 `<script setup>` 顶层使用模块级同步判断 `userRole`：
```js
const userRole = userStore.user?.role || 'user';  // 顶层同步求值
if (userRole !== 'admin') {
  uni.showToast(...);
  setTimeout(() => uni.navigateBack(), 2000);
}
```

当 Pinia store 从持久化恢复未完成时，`userRole` 可能误判为 `'user'`。

**修复方案（dashboard.vue）：**

```js
// 修改前
const userStore = useUserStore();
const userRole = userStore.user?.role || 'user';
if (userRole !== 'admin') {
  uni.showToast({ ... });
  setTimeout(() => uni.navigateBack(), 2000);
}
```

```js
// 修改后
import { computed } from 'vue';
const userStore = useUserStore();
const isAuthorized = computed(() => userStore.user?.role === 'admin');

// 模板中使用 v-if 守卫（替换顶层 if + setTimeout）
```

模板顶部：
```html
<view v-if="!isAuthorized" class="status-center">
  <text>仅管理员可访问</text>
</view>
<template v-else>
  <!-- 原有全部内容 -->
</template>
```

同时在 `onShow` 中保留 toast + 返回逻辑（作为二次守卫）：
```js
onShow(() => {
  if (!isAuthorized.value) {
    uni.showToast({ title: '仅管理员可访问', icon: 'none' });
    setTimeout(() => uni.navigateBack(), 1500);
    return;
  }
  loadDashboard();
});
```

**tickets.vue 同理。** tickets 页面角色为 `isCS`（非 admin 也可以访问工单），权限条件为 `userStore.isAdmin || userStore.isCS`。

**验证：** 非 admin 访问 dashboard → toast 提示 → 1.5s 后返回 → 页面不再短暂显示加载态

---

#### P1-03 (R7)：order/detail.vue 临时评价 ID 加前缀

**文件：** `miniprogram/src/pages/order/detail.vue`（修改）

```js
// 修改前 (~line 712)
reviews.value.push({
  id: Date.now(), // 临时 ID
  ...
});

// 修改后
reviews.value.push({
  id: `temp_${Date.now()}`, // 临时 ID 加前缀，避免与真实 DB ID 冲突
  ...
});
```

**验证：** ESLint + build 无错误

---

### Phase 2：后端补全（2 项）

---

#### P1-04 (R7)：report detail 端点测试

**文件：** `server/__tests__/unit/services/report.test.js`（新建）

**用例设计（3 条）：**

| # | 场景 | 输入 | 期望 |
|:--:|------|------|------|
| 1 | 举报人查看自己的举报 | `userId=reporterId, userRole='user'` | `{ code: 0 }` + 完整详情 |
| 2 | cs/admin 查看他人举报 | `userId≠reporterId, userRole='cs'` | `{ code: 0 }` + 完整详情 |
| 3 | 非举报人非 cs/admin 查看 | `userId≠reporterId, userRole='user'` | `{ code: 5002 }` (notOwner) |

```js
import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import reportService from '@/services/report';
// ... setup with test DB

describe('reportService.detail', () => {
  it('举报人查看自己的举报 → 成功', async () => { /* ... */ });
  it('cs/admin 查看他人举报 → 成功', async () => { /* ... */ });
  it('非举报人且非 cs/admin 查看他人举报 → 5002', async () => { /* ... */ });
});
```

**验证：** `npx vitest run server/__tests__/unit/services/report.test.js` → 3/3 passed

---

#### scheduler.js：✅ 已完成，本轮无需改动

**文件：** `server/scheduler.js`（265 行，**全部 4 个定时任务已完整实现**）

经核实，scheduler.js 已在第 5-7 轮编码中全量完成（含 IM 通知推送 + 信誉分加分 + mysqldump 备份 + 30 天轮转 + admin_logs/reviews 归档）。

| 任务 | 频率 | 状态 | 实现细节 |
|------|------|:--:|------|
| 超时订单自动取消 | 每 5 分钟 | ✅ | UPDATE orders status='cancelled' + 恢复商品 status='active' + IM 通知买卖双方 |
| 超时订单自动确认 | 每 5 分钟 | ✅ | UPDATE orders status='completed' + 卖家信誉分 +2 + IM 通知互评 |
| 数据库备份 | 每日 03:00 | ✅ | mysqldump + gzip + 30 天轮转清理 |
| 数据归档 | 工作日 04:00 | ✅ | admin_logs → archive + reviews → archive（6 个月以上） |

> 注意：scheduler 使用 `db.pool.execute()` 直接操作数据库（绕过 Service 层）。这在定时任务场景中是合理的——无需 HTTP 请求上下文，直接 SQL 操作更高效。但有一个潜在问题：`pool.execute()` 不支持 LIMIT/OFFSET 参数化（同 BUG-005），好在当前 4 个查询均不涉及 LIMIT/OFFSET。

**本轮无需改动 scheduler.js。**

---

### Phase 3：Stub 页面（7 个）

---

#### Step 3.1：编辑个人资料 `pages/user/edit.vue`

**文件：** `miniprogram/src/pages/user/edit.vue`（替换 stub）

**页面布局：**

```
┌──────────────────────────────────┐
│  编辑资料                         │
├──────────────────────────────────┤
│  ┌────────────────────────────┐  │
│  │       [当前头像]            │  │  ← 点击更换头像
│  │       点击更换头像          │  │
│  └────────────────────────────┘  │
│                                  │
│  昵称     [__________]          │  ← input（1-20 字符）
│  班级     [__________]          │  ← input（0-50 字符，选填）
│  宿舍楼栋 [__________]          │  ← input（0-30 字符，选填）
│                                  │
│  ┌────────────────────────────┐  │
│  │         保存修改            │  │  ← 主按钮
│  └────────────────────────────┘  │
└──────────────────────────────────┘
```

**数据流：**
- `onShow` → `userStore.user` 初始化表单（昵称/班级/宿舍楼栋/头像）
- 头像更换 → `ImageUploader` 组件（复用已有组件，limit=1）
- 保存 → `PUT /api/users/profile` → 成功 → `userStore.fetchUser()` 刷新 → `uni.navigateBack()`

**关键逻辑：**
- 需要后端 `PUT /api/users/profile` 端点——检查是否已存在
- 若不存在，需新增此端点（`userController.updateProfile` → `userService.updateProfile` → `userRepo.updateProfile`）
- 头像上传使用 COS 直传（复用已有 `chooseAndUpload` 模式）

**后端缺口检查：** 需确认 `PUT /api/users/profile` 是否存在。

---

#### Step 3.2：用户个人主页 `pages/user/profile.vue`

**文件：** `miniprogram/src/pages/user/profile.vue`（替换 stub）

**页面布局：**

```
┌──────────────────────────────────┐
│  ┌────────────────────────────┐  │
│  │  [大头像]                   │  │
│  │  昵称                       │  │
│  │  班级 · 宿舍楼栋            │  │
│  │                            │  │
│  │  ┌──────┐                  │  │
│  │  │  98  │ 信誉分           │  │  ← 信誉分卡片
│  │  │ /200 │                  │  │
│  │  └──────┘                  │  │
│  └────────────────────────────┘  │
│                                  │
│  ── 评价汇总 ──                  │
│  共 N 条评价  ⭐ 沟通 4.5  ⏰ 守时 4.2  📝 描述 4.8  │
│                                  │
│  ── 历史评价 ──                  │
│  [评价卡片 1]                    │  ← 复用 reviews.vue 的评价卡片模式
│  [评价卡片 2]                    │
│  ...                             │
│  <EmptyState v-if="empty" />     │
└──────────────────────────────────┘
```

**数据流：**
- URL 参数：`?id=userId`
- `loadProfile()` → `Promise.all([getUserCredit(id), getReviews({ reviewee_id: id })])`
- 用户基本信息：通过后端 `GET /api/users/:id` 或扩展现有端点

**后端缺口检查：** 
- `GET /api/users/:id/credit` — 已存在（`credit.js` 路由）
- `GET /api/reviews?reviewee_id=X` — 已存在
- `GET /api/users/:id` — 需确认用户公开信息端点是否存在

**关键逻辑：**
- 评价汇总统计：计算 `communication_score`/`punctuality_score`/`accuracy_score` 平均分
- 点击评价卡片 → 跳转对应订单详情页（如评价含 `order_id`）
- 点击发消息 → 跳转聊天页（复用 `goChat` 模式）

---

#### Step 3.3：设置页 `pages/user/settings.vue`

**文件：** `miniprogram/src/pages/user/settings.vue`（替换 stub）

**页面布局：**

```
┌──────────────────────────────────┐
│  设置                             │
├──────────────────────────────────┤
│  ── 关于 ──                      │
│  📄 用户协议                 ›   │  ← 跳转 WebView / 内置富文本
│  🔒 隐私政策                 ›   │
│  ℹ️ 关于我们                 ›   │  → pages/about/index
│                                  │
│  ── 账号 ──                      │
│  🚪 退出登录                     │  ← 红色文字，二次确认
│                                  │
│  ─────────────────────────────── │
│  版本 1.0.0                      │
└──────────────────────────────────┘
```

**功能要点：**
- 用户协议/隐私政策：使用 `<rich-text>` 或 WebView 打开（当前无协议文件，先显示占位内容）
- 退出登录：复用 `userStore.logoutAction()`（与 me.vue 一致）
- 版本号：从 `uni.getSystemInfoSync()` 或硬编码 `v1.0.0`

**注意：** 用户协议弹窗需求来自 PRD（首次登录需勾选协议），但不在本轮范围。第 12 轮实现登录页协议弹窗。

---

#### Step 3.4：关于我们 `pages/about/index.vue`

**文件：** `miniprogram/src/pages/about/index.vue`（替换 stub）

**页面布局：**

```
┌──────────────────────────────────┐
│  关于我们                         │
├──────────────────────────────────┤
│                                  │
│         [App Logo/Icon]          │
│       校园二手交易                │
│       v1.0.0                     │
│                                  │
│  校园二手交易小程序是一个面向     │
│  广州应用科技学院肇庆校区的       │
│  C2C 二手交易平台。              │
│                                  │
│  由计算机学院学生独立开发。       │
│                                  │
│  ─────────────────────────────── │
│  技术栈：                        │
│  uni-app (Vue 3) + Node.js      │
│  + Express + MySQL               │
│                                  │
│  ─────────────────────────────── │
│  联系方式：                      │
│  📧 xxxxx@example.com           │
└──────────────────────────────────┘
```

**设计要点：**
- 居中布局，品牌色顶部渐变背景
- 信息层级：Logo > 名称 > 版本 > 简介 > 技术栈 > 联系方式
- 可滚动

---

#### Step 3.5：404 页面 `pages/error/not-found.vue`

**文件：** `miniprogram/src/pages/error/not-found.vue`（替换 stub）

**页面布局：**

```
┌──────────────────────────────────┐
│                                  │
│                                  │
│            🔍                    │
│         页面不存在                │
│    你要找的页面可能已被移除       │
│    或链接地址有误                │
│                                  │
│       ┌────────────────┐        │
│       │    返回首页     │        │  ← 主按钮
│       └────────────────┘        │
│                                  │
└──────────────────────────────────┘
```

**功能：**
- 使用 `EmptyState` 组件（icon='🔍', title='页面不存在', description='...'）
- "返回首页"按钮 → `uni.switchTab({ url: '/pages/index/index' })`
- 自动检测：可从 `pages.json` 中配置为全局 404 页面（uni-app 的 `notFound` 页面）

**pages.json 注册：**
确认 `pages.json` 中是否有全局 404 页面配置。uni-app 不支持传统的 404 catch-all，但可以通过 `onPageNotFound` 事件（App.vue 中）跳转到此页。

---

#### Step 3.6：网络异常页 `pages/error/network.vue`

**文件：** `miniprogram/src/pages/error/network.vue`（替换 stub）

**页面布局：**

```
┌──────────────────────────────────┐
│                                  │
│                                  │
│            📡                    │
│         网络连接失败              │
│    请检查你的网络设置后重试       │
│                                  │
│       ┌────────────────┐        │
│       │    重新加载     │        │  ← 主按钮
│       └────────────────┘        │
│                                  │
└──────────────────────────────────┘
```

**功能：**
- 使用 `EmptyState` 组件（icon='📡', title='网络连接失败', description='...'）
- "重新加载"按钮 → 提示用户检查网络 → 尝试重新连接
- 可选：`uni.getNetworkType()` 显示当前网络状态

**集成方式：**
- 在各页面 API 请求的 catch 分支中判断网络错误，跳转此页
- 或在 `api/index.js` 拦截器中统一处理网络超时/断开

**注意：** 小程序中 `uni.getNetworkType()` 可检测网络状态。网络恢复后自动 `navigateBack` 回到上一页。

---

#### Step 3.7：废弃 review/create.vue

**文件：** `miniprogram/src/pages/review/create.vue`（修改）

**内容：**
```html
<template>
  <view class="deprecated-page">
    <text class="deprecated-icon">📝</text>
    <text class="deprecated-title">评价功能已迁移</text>
    <text class="deprecated-desc">请在对应订单详情页中进行评价</text>
    <button class="deprecated-btn" @click="goOrders">查看我的订单</button>
  </view>
</template>

<script setup>
function goOrders() {
  uni.navigateTo({ url: '/pages/order/list' });
}
</script>
```

同时在 `pages.json` 中将此页面路由标记为 `/** @deprecated 评价功能已在订单详情弹窗中实现 */` 或移除路由。

---

### Phase 4：通知 + 信誉分收尾（3 项）

---

#### Step 4.1：me.vue 通知中心入口 + 未读 badge

**文件：** `miniprogram/src/pages/user/me.vue`（修改）

**改动 1：在"我的举报"上方插入通知中心菜单项**

```html
<!-- 在 goReports 之前新增 -->
<view class="menu-item" @click="goNotifications">
  <text class="menu-icon">🔔</text>
  <text class="menu-label">通知中心</text>
  <view v-if="unreadNum > 0" class="menu-badge">
    <text class="menu-badge-text">{{ unreadNum > 99 ? '99+' : unreadNum }}</text>
  </view>
  <text class="menu-arrow">›</text>
</view>
```

**改动 2：加载未读计数**

```js
import { unreadCount } from '@/api/notification';

const unreadNum = ref(0);

async function loadUnreadCount() {
  try {
    const data = await unreadCount();
    unreadNum.value = data.count || 0;
  } catch {
    // 静默失败——未读 badge 非关键功能
  }
}

function goNotifications() {
  uni.navigateTo({ url: '/pages/notification/index' });
}

// onShow 刷新未读计数
import { onShow } from '@dcloudio/uni-app';
onShow(() => {
  loadUnreadCount();
});
```

**改动 3：badge 样式**

```scss
.menu-badge {
  min-width: 36rpx;
  height: 36rpx;
  border-radius: 18rpx;
  background: $color-error;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12rpx;
  padding: 0 8rpx;
}

.menu-badge-text {
  font-size: 20rpx;
  color: #FFFFFF;
  font-weight: $weight-bold;
}
```

**验证：** 未读通知 > 0 → 通知中心菜单项显示红色 badge → 点击进入通知中心 → 返回 → badge 消失或减少

---

#### Step 4.2：product/detail.vue "我想要"信誉分预检

**文件：** `miniprogram/src/pages/product/detail.vue`（修改）

**改动：在 `handleWant()` 函数开头新增信誉分预检**

```js
async function handleWant() {
  // 信誉分预检（前端 UX 优化，后端仍会二次校验）
  const creditScore = userStore.user?.credit_score ?? 100;
  if (creditScore < 30) {
    uni.showModal({
      title: '信誉分不足',
      content: '你的信誉分低于 30，无法发起交易。请通过完成其他交易来恢复信誉分。',
      showCancel: false,
      confirmText: '我知道了',
    });
    return;
  }
  // ... 原有逻辑
}
```

**改动：在模板中按钮增加 disabled 态**

```html
<!-- 当前 -->
<view class="action-btn action-btn--primary" @click="handleWant">
  <text>我想要</text>
</view>

<!-- 修改后 -->
<view
  class="action-btn action-btn--primary"
  :class="{ 'action-btn--disabled': (userStore.user?.credit_score ?? 100) < 30 }"
  @click="handleWant"
>
  <text>我想要</text>
</view>
```

新增样式：
```scss
.action-btn--disabled {
  background: $color-divider;
  color: $color-muted;
}
```

**注意：** 如果用户自己的商品详情页也要显示"我想要"按钮——需加判断 `product.seller_id !== userStore.user?.id`（当前应已有此判断）。

**验证：** 信誉分 < 30 的用户进入商品详情 → "我想要"按钮置灰 → 点击 → 弹窗提示

---

#### Step 4.3：product/publish.vue 发布按钮信誉分预检

**文件：** `miniprogram/src/pages/product/publish.vue`（修改）

**改动：在"发布"按钮点击时新增信誉分预检**

```js
async function onSubmit() {
  // 信誉分预检
  const creditScore = userStore.user?.credit_score ?? 100;
  if (creditScore < 60) {
    uni.showModal({
      title: '信誉分不足',
      content: '你的信誉分低于 60，无法发布商品。请通过完成交易来恢复信誉分。',
      showCancel: false,
      confirmText: '我知道了',
    });
    return;
  }
  // ... 原有逻辑
}
```

同时按钮增加 `disabled` 属性（当前 `disabled` 已绑定在 `nextStep` 各步校验中，无需额外模板改动）。

**验证：** 信誉分 < 60 的用户点击发布 → 弹窗提示 → 无法进入发布流程

---

### Phase 5：全量验证

#### 前端验证

| # | 验证项 | 方法 |
|:--:|------|------|
| 1 | ESLint 0 错误 | `npx eslint --ext .js,.vue src/` |
| 2 | Build 成功 | `npm run build:mp-weixin` |
| 3 | P1-003: 商品管理 Tab 筛选 | admin 登录 → 商品管理 → 切换"已下架"→ 列表更新 |
| 4 | P1-01: 裁决弹窗防误关 | 工单管理 → 打开裁决 → 提交期间点击遮罩 → 不关闭 |
| 5 | P1-02: 权限守卫无闪烁 | 非 admin 进入 dashboard → 直接显示"仅管理员"→ 1.5s 返回 |
| 6 | 通知中心入口 + badge | me 页 → 有未读时显示红色数字 → 点击进入通知中心 |
| 7 | 编辑资料 | me → 点击头像 → 编辑资料 → 修改昵称 → 保存 → 返回 me → 显示新昵称 |
| 8 | 个人主页 | 从商品详情页点击卖家头像 → 跳转个人主页 → 显示信誉分+评价 |
| 9 | 设置页 | me → 设置 → 各菜单项可点击 → 退出登录正常 |
| 10 | 关于我们 | me → 设置 → 关于我们 → 显示应用信息 |
| 11 | 404 页 | 访问不存在路由 → 显示 404 页 → 返回首页 |
| 12 | 网络异常页 | 断网访问 → 显示网络异常页 → 重试按钮 |
| 13 | 信誉分预检：发布按钮 | credit < 60 → 发布按钮置灰/弹窗 |
| 14 | 信誉分预检："我想要"按钮 | credit < 30 → 按钮置灰/弹窗 |
| 15 | review/create 废弃页面 | 若直接跳转 → 显示"评价功能已迁移"→ 跳转订单列表 |

#### 后端验证

| # | 验证项 | 方法 |
|:--:|------|------|
| 1 | 全部测试通过（含新增 report 测试） | `npx vitest run` → 129+ passed |
| 2 | `GET /api/admin/products` 支持 status 过滤 | `curl "/api/admin/products?status=off_shelf"` → 仅已下架商品 |

#### 验证记录更新

- [ ] 更新 `memory/project-state.md` — 记录第 9 轮完成状态
- [ ] 更新 `memory/known-bugs.md` — P1-01~P1-04 + P1-001~P1-003 标记修复
- [ ] 更新 `memory/MEMORY.md`

---

## 文件改动清单

| # | 文件 | 操作 | 预计行数 |
|:--:|------|:--:|:--:|
| **P1 修复** | | | |
| 1 | `miniprogram/src/components/AppNavbar.vue` | 修改 (+3) | +3 |
| 2 | `miniprogram/src/pages/admin/products.vue` | 修改 (import + params) | +5 |
| 3 | `miniprogram/src/pages/admin/tickets.vue` | 修改 (closeResolveModal + 权限守卫) | +10 |
| 4 | `miniprogram/src/pages/admin/dashboard.vue` | 修改 (权限守卫 computed) | +15 |
| 5 | `miniprogram/src/pages/order/detail.vue` | 修改 (temp_ 前缀) | +1 |
| **后端** | | | |
| 6 | `server/__tests__/unit/services/report.test.js` | 新建 | ~50 |
| **Stub 页面** | | | |
| 7 | `miniprogram/src/pages/user/edit.vue` | 重写 | ~250 |
| 8 | `miniprogram/src/pages/user/profile.vue` | 重写 | ~300 |
| 9 | `miniprogram/src/pages/user/settings.vue` | 重写 | ~120 |
| 10 | `miniprogram/src/pages/about/index.vue` | 重写 | ~120 |
| 11 | `miniprogram/src/pages/error/not-found.vue` | 重写 | ~80 |
| 12 | `miniprogram/src/pages/error/network.vue` | 重写 | ~100 |
| 13 | `miniprogram/src/pages/review/create.vue` | 修改 (废弃重定向) | ~30 |
| **通知 + 信誉分收尾** | | | |
| 14 | `miniprogram/src/pages/user/me.vue` | 修改 (+通知入口 + badge) | +30 |
| 15 | `miniprogram/src/pages/product/detail.vue` | 修改 (+信誉分预检) | +15 |
| 16 | `miniprogram/src/pages/product/publish.vue` | 修改 (+信誉分预检) | +10 |

**总计：** 1 新建 + 15 修改 ≈ 1105 行（后端 ~50 行 + 前端 ~1055 行）

---

## 依赖与风险

### 阻塞依赖

| 依赖 | 状态 | 说明 |
|------|:--:|------|
| 后端 `GET /api/admin/products` | ✅ | 第 7 轮已实现（含 status 过滤） |
| 前端 `api/admin.js` — `getAdminProducts` | ✅ | 第 7 轮已封装 |
| 后端 `GET /api/notifications/unread-count` | ✅ | 第 5 轮已实现 |
| 前端 `api/notification.js` — `unreadCount` | ✅ | 第 5 轮已封装 |
| userStore.user.credit_score | ✅ | 登录后 getMe 返回含 credit_score |
| `GET /api/users/:id/credit` | ✅ | 已实现 |
| `GET /api/reviews?reviewee_id=X` | ✅ | 已实现 |

### 后端缺口（本轮需做的）

**仅 1 项：** 补 `GET /api/reports/:id` 端点测试（P1-04）。

| 缺口 | 说明 | 优先级 |
|------|------|:--:|
| `server/__tests__/unit/services/report.test.js` | 第 7 轮新增的 report detail 端点无测试 | **P1** |

> **全部后端 API 端点（含 admin/products、users/profile、users/:id）均已在第 5-7 轮实现完毕，无需新增。**

### 风险点

| 风险 | 级别 | 缓解措施 |
|------|:--:|------|
| 网络异常页的自动检测机制 | 🟡 中 | 小程序 `uni.onNetworkStatusChange` 可监听网络变化。本轮先在拦截器中添加网络状态检测 |
| edit.vue 头像上传依赖 COS 配置 | 🟢 低 | 复用已有 `chooseAndUpload` + `ImageUploader` 组件 |
| settings.vue 用户协议无内容 | 🟢 低 | 先显示占位内容，协议文本在第 12 轮补充 |
| profile.vue 需 `GET /api/users/:id` | 🟢 低 | ✅ 已验证存在（`userController.getById` → `userService.getById`） |

---

## 与其他迭代的接口

| 接口点 | 依赖方 | 本迭代交付 |
|--------|--------|-----------|
| 用户协议弹窗 | 第 12 轮 | 🔲 settings.vue 中有协议入口但无弹窗逻辑，第 12 轮实现登录页协议弹窗 |
| 个人主页 | 第 8 轮（用户管理中的"详情"） | ✅ `users.vue` 的用户卡片可跳转 `profile.vue` |
| 404 页面 | App.vue | ✅ `onPageNotFound` 事件跳转 `error/not-found.vue` |
| 网络异常页 | api/index.js 拦截器 | ✅ 网络错误统一跳转 `error/network.vue` |
| review/create.vue | 用户直接 URL 跳转 | ✅ 废弃页重定向到订单列表 |
| 通知中心入口 | me.vue | ✅ 带未读 badge 的菜单项 |

---

## 不纳入本轮的内容

- ❌ **登录页用户协议弹窗** → 第 12 轮
- ❌ **AppNavbar 替换已有页面导航栏** → 第 12 轮（或标记为"可选增强"）
- ❌ **settings.vue 中的用户协议/隐私政策全文** → 第 12 轮（需协议文本文件）
- ❌ **数据库备份定时任务** → ✅ 已实现（scheduler.js 265 行全量完成）
- ❌ **P2 项修复（P2-001~P2-007）** → 除 P2-004（sensitive stats 失败静默）外均延后至第 12 轮
- ❌ **CSS 重复提取到 common.scss** → 第 12 轮全局样式整理
- ❌ **编辑资料页的班级/宿舍楼栋字段后端支持** → 需确认数据库是否有对应字段（`user_info` 表或 `users` 表）

---

## 与第 8 轮的关系

第 8 轮交付了管理后台的全部 6 个页面 + 2 个基础设施组件。本轮是管理后台的 **P1 修复 + 剩余 stub 收尾 + 通知/信誉分最后收尾**。

三轮合计覆盖完整的用户侧 + 管理侧功能矩阵：

| 功能 | 第 7 轮 | 第 8 轮 | 第 9 轮 |
|------|:--:|:--:|:--:|
| 举报提交/详情/列表 | ✅ | — | — |
| 工单管理 | ✅ | — | P1 修复 |
| 数据看板 | ✅ | — | P1 修复 |
| 用户管理 | — | ✅ | — |
| 商品管理 | — | ⚠️ (P1-002/003) | ✅ 修复 |
| 审计日志 | — | ✅ | — |
| 敏感词库 | — | ✅ | — |
| 通知中心 | ✅ (第 5 轮) | — | ✅ 入口 + badge |
| 信誉分页面 | ✅ (第 6 轮) | — | ✅ 阈值预检 |
| 编辑资料 | — | — | ✅ |
| 个人主页 | — | — | ✅ |
| 设置页 | — | — | ✅ |
| 关于我们 | — | — | ✅ |
| 404/网络异常 | — | — | ✅ |
| "我的"页面 | ⚠️ 缺通知入口 | ✅ 管理入口补全 | ✅ 通知入口 + badge |

---

## 验证清单（15 项前端 + 4 项后端）

### 前端

| # | 验证项 | 方法 |
|:--:|------|------|
| 1 | P1-003: 商品管理 Tab 筛选正常 | admin → 商品管理 → 切换"已下架"→ 显示已下架商品 |
| 2 | P1-01: 裁决弹窗提交期间不可关闭 | admin → 工单管理 → 打开裁决 → 提交期间点击遮罩 → 不关闭 |
| 3 | P1-02: 权限守卫无闪烁 | 非 admin 进入 dashboard → 直接显示"仅管理员可访问" |
| 4 | 通知中心入口 + 未读 badge | me 页 → 通知中心有红色 badge → 点击后 badge 消失 |
| 5 | 编辑资料保存生效 | me → 编辑资料 → 修改昵称 → 保存 → me 显示新昵称 |
| 6 | 个人主页展示正确 | 点击卖家头像 → 显示信誉分 + 评价汇总 + 历史评价 |
| 7 | 设置页功能正常 | 设置 → 各入口可点击 → 退出登录正常 |
| 8 | 关于我们显示正常 | 设置 → 关于我们 → 应用信息完整 |
| 9 | 404 页面 | 访问不存在路由 → 显示 404 页 → 返回首页可点击 |
| 10 | 网络异常页 | 断网访问数据页 → 显示网络异常 → 重试可点击 |
| 11 | review/create 废弃提示 | 访问 review/create → 显示迁移提示 → 跳转订单列表 |
| 12 | 信誉分 < 30 时"我想要"按钮置灰 | credit < 30 的用户进入商品详情 → 按钮置灰 + 弹窗 |
| 13 | 信誉分 < 60 时发布按钮拦截 | credit < 60 的用户点击发布 → 弹窗提示 |
| 14 | ESLint 0 错误 | `npx eslint --ext .js,.vue src/` |
| 15 | Build 成功 | `npm run build:mp-weixin` |

### 后端

| # | 验证项 | 方法 |
|:--:|------|------|
| 1 | 全部测试通过（含新增 report 测试） | `npx vitest run` → 129+ passed |
| 2 | `GET /api/admin/products` status 过滤 | `curl "/api/admin/products?status=off_shelf"` → 仅已下架 |
| 3 | scheduler 超时订单自动取消 | DB 插入 25h 前 pending 订单 → scheduler → 变为 cancelled |
| 4 | scheduler 超时订单自动确认 | DB 插入 73h 前 met 订单 → scheduler → 变为 completed |

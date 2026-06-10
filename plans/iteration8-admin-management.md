# 第 8 轮编码计划：管理后台（用户/商品/审计/敏感词）

> **状态：** ⏳ 待开始
> **预估工时：** 2 天
> **目标：** 管理员用户列表与封禁/解封 → 商品列表与强制下架 → 操作审计日志查看 → 敏感词库统计与重载 → "我的"页面管理入口补全 → 通用空状态组件 + 自定义导航栏组件
> **依据文档：** PRD §3.7（管理后台）、技术架构文档 §八（管理后台）、API 文档 §2.8~§2.12（工单+用户+商品+统计+敏感词+审计）、测试计划 §3.11~§3.12、编码迭代计划 §第 8 轮

---

## 现状分析

### 后端 — 管理后台基础设施 100% 完成 ✅

| 层 | 文件 | 状态 | 核心能力 |
|------|------|:--:|------|
| Service | `services/admin.js` (249 行) | ✅ | processTicket/resolveTicket/listTickets + banUser/unbanUser/listUsers + offShelfProduct + sensitiveStats/reloadSensitive/checkSensitive + listLogs |
| Controller | `controllers/admin.js` (233 行) | ✅ | 14 个端点全部就绪 |
| Route | `routes/admin.js` (88 行) | ✅ | cs 中间件（工单）+ admin 中间件（用户/商品/统计/敏感词/日志），Joi 校验（裁决+敏感词检查） |
| Service | `services/analytics.js` (107 行) | ✅ | overview/categories/searchKeywords/dashboard |
| Repository | `repository/user.js` | ✅ | `listWithFilters()` — 支持 keyword/status/role 筛选 + 分页（已 parseInt 修复） |
| Repository | `repository/product.js` | ✅ | `findBySeller/list` — 可复用为管理端商品列表 |
| Middleware | `middleware/admin.js` | ✅ | `role === 'admin'` 检查 |
| Middleware | `middleware/cs.js` | ✅ | `role ∈ {cs, admin}` 检查 |

**结论：** 后端 14 个管理端点 + 权限中间件 + 审计日志全部就绪，本轮纯前端工作。

### 前端 — 管理后台部分已实现，4 个新页面 + 2 个组件待开发

| 文件 | 状态 | 说明 |
|------|:--:|------|
| `api/admin.js` | ✅ 180 行 | 14 个端点封装全部完成（第 7 轮） |
| `pages/admin/dashboard.vue` | ✅ 508 行 | 数据看板（第 7 轮完成） |
| `pages/admin/tickets.vue` | ✅ 775 行 | 工单管理（第 7 轮完成） |
| `pages/report/submit.vue` | ✅ 260 行 | 举报表单（第 7 轮完成） |
| `pages/report/detail.vue` | ✅ 230 行 | 举报详情（第 7 轮完成） |
| `pages/report/list.vue` | ✅ | 我的举报列表（第 7 轮完成） |
| `pages/user/me.vue` | ⚠️ 266 行 | 管理区只有工单+看板 2 个入口，缺用户/商品/日志/敏感词入口 |
| **`pages/admin/users.vue`** | ❌ 不存在 | 用户管理：列表 + 搜索 + 封禁/解封 |
| **`pages/admin/products.vue`** | ❌ 不存在 | 商品管理：列表 + 搜索 + 下架 |
| **`pages/admin/logs.vue`** | ❌ 不存在 | 审计日志：筛选 + 分页查看 |
| **`pages/admin/sensitive.vue`** | ❌ 不存在 | 敏感词库：词数统计 + 重载 + 文本检查 |
| `components/EmptyState.vue` | ❌ 1 行 stub | 通用空状态组件 |
| `components/AppNavbar.vue` | ❌ 1 行 stub | 自定义导航栏组件 |

---

## 架构关键决策

### 管理后台权限分层（后端强制，前端 UI 隐藏）

| 功能域 | 路由前缀 | 中间件 | 角色 | 前端入口可见性 |
|--------|---------|--------|------|---------------|
| 工单管理 | `/api/admin/tickets` | `cs` | cs / admin | cs + admin |
| 用户管理 | `/api/admin/users` | `admin` | admin only | 仅 admin |
| 商品管理 | `/api/admin/products` | `admin` | admin only | 仅 admin |
| 数据统计 | `/api/admin/analytics` + `/dashboard` | `admin` | admin only | 仅 admin |
| 敏感词库 | `/api/admin/sensitive` | `admin` | admin only | 仅 admin |
| 审计日志 | `/api/admin/logs` | `admin` | admin only | 仅 admin |

- **安全边界在后端**：前端仅做 UI 入口隐藏（`v-if="userStore.isAdmin"`），真正的权限校验在 `middleware/admin.js`。
- **cs 角色**：仅能访问工单管理页（tickets），不能看数据看板/用户/商品/敏感词/审计日志。第 7 轮 `me.vue` 已有 `userStore.isAdmin` 守卫，但未区分 `isAdmin` vs `isCS`，本轮需要修正为精确的 `isCS`（cs 也可见工单管理）。

### 用户状态机

```
active ──管理员封禁──→ banned ──管理员解封──→ active
```

- 封禁时 `token_version + 1`（已有中间件自动踢下线）
- 不可封禁其他管理员（`cannotOperateAdmin` 错误）
- 封禁/解封均写入 `admin_logs` 审计表

### 商品管理范围

- **仅管理员可强制下架**（非本人、非卖家操作），写入审计日志
- 商品列表复用 `productService.list()` 的搜索/筛选能力（分类/成色/关键词），管理员视角可查看全部商品（不受 seller_id 限制）
- 下架后商品状态变为 `off_shelf`，前端商品详情页已有状态处理

---

## 执行策略：单人多文件串行 + 依赖内聚

> **决策日期：** 2026-06-11
> **方法：** 后端零改动（全部就绪）。前端 6 个新文件按"组件→页面"依赖串行，4 个管理页面无互相依赖可快速连续实现。

### 为什么本轮不使用 Agent 并行

| 因素 | 分析 |
|------|------|
| 后端零改动 | 14 个 API + 权限中间件全部就绪，无需写任何后端代码 |
| 4 管理页面结构同构 | 列表页（users/products/logs）共享同一种"搜索栏+状态Tab+卡片列表+分页"模式，第一个写好后后三个高度复用 |
| 通用组件先行 | EmptyState + AppNavbar 是所有页面的依赖，必须先完成 |
| 已有代码模式丰富 | 第 6/7 轮的列表页（order/list、admin/tickets）提供成熟的 targetPage 分页/状态标签/空状态/权限守卫模式 |

### 执行顺序（按依赖拓扑）

```
Step 0: pages.json 注册新路由（4 个 admin 子页面）
  ↓
Step 1: components/EmptyState.vue ──→ 通用空状态组件（被所有页面依赖）
Step 2: components/AppNavbar.vue ──→ 自定义导航栏（被所有页面依赖）
  ↓
Step 3: pages/admin/users.vue ──→ 用户管理（列表+搜索+封禁/解封）
Step 4: pages/admin/products.vue ──→ 商品管理（列表+搜索+下架）
Step 5: pages/admin/logs.vue ──→ 审计日志（筛选+分页）
Step 6: pages/admin/sensitive.vue ──→ 敏感词库（统计+重载+检查）
  ↓
Step 7: pages/user/me.vue 增强 ──→ 管理入口补全（用户/商品/日志/敏感词），区分 admin vs cs
  ↓
Step 8: 全量验证 ──→ ESLint + build + 真机 E2E
```

### 预估工时分布

| 步骤 | 内容 | 预估 |
|:--:|------|:--:|
| 0 | pages.json 路由注册 | 5 min |
| 1 | EmptyState 组件 | 20 min |
| 2 | AppNavbar 组件 | 30 min |
| 3 | 用户管理页 | 2 h |
| 4 | 商品管理页 | 1.5 h |
| 5 | 审计日志页 | 1 h |
| 6 | 敏感词库页 | 1 h |
| 7 | "我的"页面增强 | 30 min |
| 8 | 验证 | 1 h |
| **合计** | | **~8 h ≈ 1.5 天** |

---

## 分步计划（8 步，每步独立可验证）

### 步骤 0：pages.json 路由注册（~10 行新增）

**文件：** `miniprogram/src/pages.json`（修改）

在 `pages/admin/dashboard` 和 `pages/admin/tickets` 之后新增 4 个页面路由：

```json
{
  "path": "pages/admin/users",
  "style": {
    "navigationBarTitleText": "用户管理",
    "navigationBarBackgroundColor": "#4A90D9",
    "navigationBarTextStyle": "white"
  }
},
{
  "path": "pages/admin/products",
  "style": {
    "navigationBarTitleText": "商品管理",
    "navigationBarBackgroundColor": "#4A90D9",
    "navigationBarTextStyle": "white"
  }
},
{
  "path": "pages/admin/logs",
  "style": {
    "navigationBarTitleText": "审计日志",
    "navigationBarBackgroundColor": "#4A90D9",
    "navigationBarTextStyle": "white"
  }
},
{
  "path": "pages/admin/sensitive",
  "style": {
    "navigationBarTitleText": "敏感词库",
    "navigationBarBackgroundColor": "#4A90D9",
    "navigationBarTextStyle": "white"
  }
}
```

**验证：** `npm run dev:mp-weixin` 编译通过，4 个新页面可被 `uni.navigateTo` 跳转（即使内容为空）

---

### 步骤 1：EmptyState 通用组件（~90 行）

**文件：** `miniprogram/src/components/EmptyState.vue`（替换 1 行 stub）

**功能规格：**

```
Props:
  icon: string = '📦'         // emoji 图标
  title: string = '暂无数据'   // 主标题
  description: string = ''    // 描述文字（可选）
  actionText: string = ''     // 操作按钮文字（可选，不传则不显示按钮）
  actionPath: string = ''     // 操作跳转路径（可选）
  paddingTop: string = '120rpx' // 顶部留白

Events:
  @action: () => void        // 点击操作按钮

Slots:
  default: 自定义内容区（覆盖 icon+title+description）
```

**设计令牌：**
- 图标字号：80rpx（`$text-4xl` 附近）
- 标题字号：`$text-base`（28rpx），颜色 `$color-muted`
- 描述字号：`$text-sm`（24rpx），颜色 `$color-placeholder`
- 操作按钮：`$color-primary` 文字按钮，字号 `$text-base`
- 容器背景：`$color-bg`

**使用示例：**

```html
<!-- 最简单用法 -->
<EmptyState />

<!-- 自定义文案 -->
<EmptyState icon="📋" title="暂无订单" description="去逛逛首页发现好物吧" />

<!-- 带操作按钮 -->
<EmptyState
  icon="🔍"
  title="未找到匹配的用户"
  description="试试调整筛选条件"
  action-text="清除筛选"
  @action="clearFilters"
/>
```

**验证：** 组件在任意页面中引用 → 显示空盒子 emoji + 标题 + 描述 → 点击按钮触发 @action 事件

---

### 步骤 2：AppNavbar 自定义导航栏（~130 行）

**文件：** `miniprogram/src/components/AppNavbar.vue`（替换 1 行 stub）

**功能规格：**

```
Props:
  title: string = ''                  // 标题
  showBack: boolean = false           // 是否显示返回按钮
  backgroundColor: string = '#4A90D9' // 背景色
  titleColor: string = '#FFFFFF'      // 标题色
  rightIcon: string = ''              // 右侧图标文字（emoji）
  rightText: string = ''              // 右侧文字

Events:
  @back: () => void                   // 点击返回
  @rightClick: () => void             // 点击右侧按钮

行为：
  - 自动计算状态栏高度：`uni.getSystemInfoSync().statusBarHeight`
  - 导航栏高度固定 88rpx（44px 标准胶囊高度）
  - 返回按钮 → 默认 `uni.navigateBack()`，可通过 @back 覆盖
  - 背景色支持渐变（直接传入 CSS gradient 字符串）
  - 右侧区域：icon 优先于 text，两者都不传则不显示
```

**设计令牌：**
- 总高度：`statusBarHeight + 88rpx`
- 返回箭头：`‹` 字符，字号 44rpx
- 标题字号：`$text-lg`（32rpx），居中
- 右侧字号：`$text-sm`（24rpx）
- 内边距：`$space-page`（32rpx）

**注意：** 
- 此组件用于非 TabBar 页面，替代系统导航栏以实现统一的蓝色渐变背景
- 使用 `navigationStyle: 'custom'` 的页面才需要此组件
- 当前项目已有大量页面使用系统导航栏（`navigationBarBackgroundColor: '#4A90D9'`），本轮新页面统一使用系统导航栏，**AppNavbar 作为可选增强，不在本轮强制替换已有页面**

**验证：** 在一个使用 `navigationStyle: 'custom'` 的页面引用 → 显示状态栏高度 + 标题 + 返回按钮 → 点击返回触发 navigateBack

---

### 步骤 3：用户管理页（~350 行）

**文件：** `miniprogram/src/pages/admin/users.vue`（新建）

**3.1 页面布局：**

```
┌──────────────────────────────────┐
│  用户管理                         │
├──────────────────────────────────┤
│  ┌────────────────────────────┐  │
│  │ 🔍 搜索手机号/昵称          │  │  ← 搜索栏（input + 搜索按钮）
│  └────────────────────────────┘  │
│  [全部] [正常] [已封禁]          │  ← 状态 Tab
│  [全部角色] [管理员] [普通用户]   │  ← 角色筛选（可选，下拉或横滑）
├──────────────────────────────────┤
│  ┌────────────────────────────┐  │
│  │ [头像] 微信用户             │  │
│  │        13800138000         │  │
│  │        信誉分: 98          │  │
│  │        角色: user · 正常   │  │
│  │              [封禁] [详情] │  │
│  └────────────────────────────┘  │
│  ┌────────────────────────────┐  │
│  │ [头像] 小明                 │  │
│  │        13900139000         │  │
│  │        信誉分: 72          │  │
│  │        角色: admin · 已封禁│  │
│  │              [解封] [详情] │  │
│  └────────────────────────────┘  │
│          ...                     │
│  <EmptyState v-if="empty" />     │
└──────────────────────────────────┘
```

**3.2 数据流：**

- `loadUsers()` → `getUserList({ keyword, status, role, page, pageSize })`（admin API 已封装）
- 搜索：输入框 `@confirm` → `keyword = value` → `refresh(true)` 重新查第 1 页
- 状态 Tab：全部(空) / active / banned → `refresh(true)`
- 角色筛选：全部(空) / admin / user / cs → `refresh(true)`
- 分页模式：**targetPage 模式**（修复 A6-001 翻页跳页 Bug 的固化模式）

**3.3 用户卡片信息：**

| 字段 | 来源 | 展示方式 |
|------|------|---------|
| 头像 | `user.avatar` | 80rpx 圆形，空时显示默认灰圆 |
| 昵称 | `user.nickname` | 加粗，未设置显示"微信用户" |
| 手机号 | `user.phone` | 脱敏中间 4 位（`138****8000`） |
| 信誉分 | `user.credit_score` | 数字 + 颜色（≥60 绿/30-59 橙/<30 红） |
| 角色 | `user.role` | 标签：admin 红色/cs 蓝色/user 灰色 |
| 状态 | `user.status` | 标签：active 绿色/banned 红色 |

**3.4 操作按钮：**

| 当前状态 | 当前角色 | 操作 | 确认弹窗 | API |
|---------|:--:|------|---------|-----|
| active | user/cs | [封禁] | "确定封禁用户 {昵称}？封禁后该用户将无法登录。" | `banUser(id)` |
| banned | user/cs | [解封] | "确定解封用户 {昵称}？" | `unbanUser(id)` |
| * | admin | — | 不显示操作按钮（不可操作管理员） | — |

**3.5 关键逻辑：**

```js
// 分页模式（targetPage 模式，固化 A6-001 修复）
async function fetchUsers(reset = false) {
  if (loading.value) return;
  const targetPage = reset ? 1 : page.value + 1;
  loading.value = true;

  try {
    const result = await getUserList({
      keyword: keyword.value || undefined,
      status: activeStatus.value || undefined,
      role: activeRole.value || undefined,
      page: targetPage,
      pageSize: PAGE_SIZE,
    });

    if (reset) {
      users.value = result.list || [];
    } else {
      users.value.push(...(result.list || []));
    }
    total.value = result.total;
    hasMore.value = users.value.length < result.total;
    page.value = targetPage; // 成功后才持久化
  } catch (err) {
    uni.showToast({ title: err.message || '加载失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}

// 封禁用户（二次确认 + 操作后刷新列表）
async function handleBan(user) {
  const confirm = await uni.showModal({
    title: '封禁用户',
    content: `确定封禁用户「${user.nickname || '微信用户'}」？封禁后该用户将无法登录。`,
    confirmText: '确定封禁',
    confirmColor: '#FF4D4F',
  });
  if (!confirm.confirm) return;

  try {
    await banUser(user.id);
    uni.showToast({ title: '已封禁', icon: 'success' });
    // 乐观更新本地状态
    user.status = 'banned';
  } catch (err) {
    uni.showToast({ title: err.message || '操作失败', icon: 'error' });
  }
}
```

**3.6 权限守卫：**

```js
// onShow 检查
const userStore = useUserStore();
if (!userStore.isAdmin) {
  uni.showToast({ title: '仅管理员可访问', icon: 'none' });
  setTimeout(() => uni.navigateBack(), 1500);
  return;
}
```

**验证：** admin 登录 → 进入用户管理 → 列表渲染 → 搜索"138"→ 过滤结果 → 点击封禁 → 二次确认 → 封禁成功 → 用户状态标签变为红色"已封禁" → 点击解封 → 恢复正常

---

### 步骤 4：商品管理页（~300 行）

**文件：** `miniprogram/src/pages/admin/products.vue`（新建）

**4.1 页面布局：**

```
┌──────────────────────────────────┐
│  商品管理                         │
├──────────────────────────────────┤
│  ┌────────────────────────────┐  │
│  │ 🔍 搜索商品标题/卖家昵称    │  │  ← 搜索栏
│  └────────────────────────────┘  │
│  [全部] [在售] [已下架] [已售]  │  ← 状态 Tab
├──────────────────────────────────┤
│  ┌────────────────────────────┐  │
│  │ [图] MacBook Pro 2022     │  │
│  │      95新 · ¥128.00       │  │
│  │      卖家: 小明 · 在售    │  │
│  │      发布时间: 12-01      │  │
│  │              [下架] [查看] │  │
│  └────────────────────────────┘  │
│          ...                     │
│  <EmptyState v-if="empty" />     │
└──────────────────────────────────┘
```

**4.2 数据流：**

- `loadProducts()` → `getProductList({ keyword, status, category, condition, page, pageSize })`（复用商品 API）
- 状态 Tab：全部(空) / active / off_shelf / sold
- 搜索支持：商品标题 + 卖家昵称（后端 `productService.list` 已支持）

**4.3 商品卡片信息：**

| 字段 | 来源 | 展示方式 |
|------|------|---------|
| 首图 | `product.cover_image` 或快照首张 | 120×120rpx 圆角缩略图 |
| 标题 | `product.title` | 最多 2 行省略 |
| 成色 | `product.condition` | 小标签 |
| 售价 | `product.price` | ¥XX.XX 红色 |
| 卖家 | `product.seller_nickname` | 灰色小字 |
| 状态 | `product.status` | 彩色标签（active 绿/off_shelf 灰/sold 蓝/reserved 橙） |
| 时间 | `product.created_at` | 相对时间（formatDateTime） |

**4.4 操作按钮：**

| 当前状态 | 操作 | 确认弹窗 | API |
|---------|------|---------|-----|
| active / reserved | [下架] | "确定下架商品「{标题}」？下架后将不再公开展示。" | `offShelfProduct(id)` |
| off_shelf / sold | — | 不显示下架按钮 | — |

**4.5 关键逻辑：**

```js
async function handleOffShelf(product) {
  const confirm = await uni.showModal({
    title: '下架商品',
    content: `确定下架商品「${product.title}」？下架后将不再公开展示。`,
    confirmText: '确定下架',
    confirmColor: '#FF4D4F',
  });
  if (!confirm.confirm) return;

  try {
    await offShelfProduct(product.id);
    uni.showToast({ title: '已下架', icon: 'success' });
    product.status = 'off_shelf'; // 乐观更新
  } catch (err) {
    uni.showToast({ title: err.message || '操作失败', icon: 'error' });
  }
}
```

**4.6 权限守卫：** 同步骤 3.6

**验证：** admin 登录 → 商品管理 → 列表渲染 → 按状态过滤 → 点击下架 → 二次确认 → 商品状态变为 off_shelf → 前端首页不再显示该商品

---

### 步骤 5：审计日志页（~220 行）

**文件：** `miniprogram/src/pages/admin/logs.vue`（新建）

**5.1 页面布局：**

```
┌──────────────────────────────────┐
│  审计日志                         │
├──────────────────────────────────┤
│  筛选（可选，折叠面板）：          │
│  操作类型: [全部] [封禁] [解封]   │
│           [下架] [受理] [裁决]    │
│  时间范围: [起始日期] ~ [结束日期] │
│  [应用筛选]  [重置]              │
├──────────────────────────────────┤
│  ┌────────────────────────────┐  │
│  │ 🚫 封禁                      │  │  ← 操作图标 + 操作名称
│  │ 管理员: 牢大                 │  │
│  │ 对象: 用户 #12 (小明)        │  │  ← target_type + target_id
│  │ 时间: 2026-06-10 21:30     │  │
│  └────────────────────────────┘  │
│  ┌────────────────────────────┐  │
│  │ ⚖️ 裁决工单                  │  │
│  │ 管理员: 牢大                 │  │
│  │ 对象: 工单 #1               │  │
│  │ 原因: 举报属实，商品已下架   │  │
│  │ 时间: 2026-06-10 20:15     │  │
│  └────────────────────────────┘  │
│          ...                     │
│  <EmptyState v-if="empty"        │
│    icon="📋" title="暂无操作记录" />│
└──────────────────────────────────┘
```

**5.2 数据流：**

- `loadLogs()` → `getAdminLogs({ action, start_date, end_date, page, pageSize })`
- `action` 枚举：ban / unban / off_shelf / process_ticket / resolve_ticket

**5.3 日志卡片信息：**

| 字段 | 来源 | 展示方式 |
|------|------|---------|
| 操作类型 | `log.action` | 图标 + 中文映射（`ACTION_MAP`） |
| 管理员 | `log.admin_phone` | 脱敏手机号 |
| 目标对象 | `log.target_type` + `log.target_id` | 中文映射 + ID（如"用户 #12"） |
| 操作原因 | `log.reason` | 最多 2 行省略（仅裁决时有值） |
| 时间 | `log.created_at` | formatDateTime |

**5.4 操作类型映射：**

```js
const ACTION_MAP = {
  ban:              { icon: '🚫', label: '封禁用户' },
  unban:            { icon: '✅', label: '解封用户' },
  off_shelf:        { icon: '📦', label: '下架商品' },
  process_ticket:   { icon: '📋', label: '受理工单' },
  resolve_ticket:   { icon: '⚖️', label: '裁决工单' },
};
```

**5.5 筛选逻辑：**

- 操作类型筛选：点击标签切换 → `action.value = type` → `refresh(true)`
- 日期范围：使用 `uni-dateTimePicker` 或简化为 placeholder 输入（YYYY-MM-DD），后端 `start_date`/`end_date` 参数已支持
- 重置：清空所有筛选 → `refresh(true)`

**5.6 权限守卫：** 同步骤 3.6

**验证：** admin 登录 → 审计日志 → 列表显示历史操作 → 按类型筛选"封禁"→ 只显示封禁记录 → 日期范围筛选有效

---

### 步骤 6：敏感词库页（~250 行）

**文件：** `miniprogram/src/pages/admin/sensitive.vue`（新建）

**6.1 页面布局：**

```
┌──────────────────────────────────┐
│  敏感词库                         │
├──────────────────────────────────┤
│  ┌────────────────────────────┐  │
│  │       📚                    │  │
│  │    当前词库                  │  │
│  │    ┌──────────┐             │  │
│  │    │   437    │             │  │  ← 大数字（词数）
│  │    │   个词    │             │  │
│  │    └──────────┘             │  │
│  │  最后加载：2026-06-10 09:30 │  │
│  └────────────────────────────┘  │
│                                  │
│  ── 文本检查 ──                  │
│  ┌────────────────────────────┐  │
│  │ 输入文本进行检查...         │  │  ← textarea
│  └────────────────────────────┘  │
│  [检查文本]                      │  ← 主按钮
│                                  │
│  检查结果：                      │
│  ┌────────────────────────────┐  │
│  │ ✅ 未检测到敏感词           │  │  ← 绿色成功态
│  │ 或                          │  │
│  │ ⚠️ 检测到敏感词: xxx, yyy  │  │  ← 黄色警告态
│  └────────────────────────────┘  │
│                                  │
│  ─────────────────────────────── │
│                                  │
│  ┌────────────────────────────┐  │
│  │      🔄 重新加载词库        │  │  ← 操作按钮
│  └────────────────────────────┘  │
│  更新敏感词库文件后点击重载      │  ← 说明文字
└──────────────────────────────────┘
```

**6.2 数据流：**

- `loadStats()` → `getSensitiveStats()` → `{ word_count }`
- `handleCheck()` → `checkSensitiveText(text)` → `{ has_sensitive, words }`
- `handleReload()` → `reloadSensitiveWords()` → 刷新词数

**6.3 关键逻辑：**

```js
const stats = ref({ word_count: 0 });
const checkText = ref('');
const checkResult = ref(null); // { has_sensitive, words }
const checking = ref(false);
const reloading = ref(false);

async function loadStats() {
  try {
    const data = await getSensitiveStats();
    stats.value = data;
  } catch (err) {
    uni.showToast({ title: '加载失败', icon: 'none' });
  }
}

async function handleCheck() {
  const text = checkText.value.trim();
  if (!text) {
    uni.showToast({ title: '请输入文本', icon: 'none' });
    return;
  }
  checking.value = true;
  try {
    checkResult.value = await checkSensitiveText(text);
  } catch (err) {
    uni.showToast({ title: err.message || '检查失败', icon: 'error' });
  } finally {
    checking.value = false;
  }
}

async function handleReload() {
  const confirm = await uni.showModal({
    title: '重新加载词库',
    content: '将从敏感词文件重新加载，确定继续？',
  });
  if (!confirm.confirm) return;

  reloading.value = true;
  try {
    const result = await reloadSensitiveWords();
    stats.value.word_count = result.word_count;
    uni.showToast({ title: `已重载，共 ${result.word_count} 个词`, icon: 'success' });
  } catch (err) {
    uni.showToast({ title: err.message || '重载失败', icon: 'error' });
  } finally {
    reloading.value = false;
  }
}
```

**6.4 权限守卫：** 同步骤 3.6

**验证：** admin 登录 → 敏感词库 → 显示词数 → 输入含敏感词的文本 → 点击检查 → 显示检测到的敏感词列表 → 输入正常文本 → 显示"未检测到" → 点击重载 → 二次确认 → 重载成功 → 词数刷新

---

### 步骤 7："我的"页面管理入口补全（~40 行改动）

**文件：** `miniprogram/src/pages/user/me.vue`（修改）

**7.1 当前状态：**
- 管理功能 section 已存在，`v-if="userStore.isAdmin"` 守卫
- 目前只有 2 个入口：工单管理 + 数据看板
- 缺少：用户管理、商品管理、审计日志、敏感词库

**7.2 改动内容：**

在管理功能 section 中新增 4 个菜单项：

```html
<!-- 管理功能（角色区分） -->
<view v-if="userStore.isAdmin || userStore.isCS" class="menu-section">
  <view class="menu-section-header">
    <text class="menu-section-title">管理功能</text>
  </view>
  <!-- cs + admin 可见 -->
  <view class="menu-item" @click="goTickets">
    <text class="menu-icon">🎫</text>
    <text class="menu-label">工单管理</text>
    <text class="menu-arrow">›</text>
  </view>
  <!-- 仅 admin 可见 -->
  <template v-if="userStore.isAdmin">
    <view class="menu-item" @click="goDashboard">
      <text class="menu-icon">📊</text>
      <text class="menu-label">数据看板</text>
      <text class="menu-arrow">›</text>
    </view>
    <view class="menu-item" @click="goUsers">
      <text class="menu-icon">👥</text>
      <text class="menu-label">用户管理</text>
      <text class="menu-arrow">›</text>
    </view>
    <view class="menu-item" @click="goProducts">
      <text class="menu-icon">🛒</text>
      <text class="menu-label">商品管理</text>
      <text class="menu-arrow">›</text>
    </view>
    <view class="menu-item" @click="goLogs">
      <text class="menu-icon">📝</text>
      <text class="menu-label">审计日志</text>
      <text class="menu-arrow">›</text>
    </view>
    <view class="menu-item" @click="goSensitive">
      <text class="menu-icon">🛡️</text>
      <text class="menu-label">敏感词库</text>
      <text class="menu-arrow">›</text>
    </view>
  </template>
</view>
```

**7.3 新增导航函数：**

```js
function goUsers() { uni.navigateTo({ url: '/pages/admin/users' }); }
function goProducts() { uni.navigateTo({ url: '/pages/admin/products' }); }
function goLogs() { uni.navigateTo({ url: '/pages/admin/logs' }); }
function goSensitive() { uni.navigateTo({ url: '/pages/admin/sensitive' }); }
```

**7.4 角色判断修正：**

当前 `v-if="userStore.isAdmin"` 将 cs 角色也拦在工单管理之外。需修正为：

- `v-if="userStore.isAdmin || userStore.isCS"` → 管理功能 section 可见
- `v-if="userStore.isAdmin"` → 用户/商品/日志/敏感词/看板 入口可见（嵌套在 section 内）

注意检查 `store/user.js` 是否已有 `isCS` getter（camelCase 兼容）：

```js
// store/user.js 需确保存在（如果没有则添加）：
isAdmin: (state) => state.user?.role === 'admin',
isCS: (state) => state.user?.role === 'cs' || state.user?.role === 'admin',
```

**验证：** admin 登录 → "我的"页显示 6 个管理入口 → cs 登录 → 只显示"工单管理"→ 普通用户登录 → 不显示管理 section

---

### 步骤 8：全量验证 + 状态更新

- [ ] 前端 `npm run lint` — ESLint 0 错误
- [ ] 前端 `npm run build:mp-weixin` — 编译成功
- [ ] `pages.json` 包含 4 个新 admin 路由
- [ ] 微信开发者工具端到端验证：

  | # | 验证项 | 方法 |
  |:--:|------|------|
  | 1 | 用户列表渲染 + 搜索 | admin 登录 → 用户管理 → 输入手机号搜索 → 结果正确 |
  | 2 | 封禁用户 | 点击封禁 → 二次确认 → 封禁成功 → 状态标签变红 |
  | 3 | 解封用户 | 对已封禁用户点击解封 → 确认 → 状态恢复 |
  | 4 | 不可操作管理员 | admin 用户卡片不显示操作按钮 |
  | 5 | 商品列表 + 下架 | 商品管理 → 点击下架 → 确认 → 状态变 off_shelf |
  | 6 | 审计日志筛选 | 日志页 → 按操作类型筛选 → 按日期筛选 → 结果正确 |
  | 7 | 敏感词检查 | 输入敏感词 → 检查 → 显示检测结果 |
  | 8 | 敏感词重载 | 点击重载 → 确认 → 词数刷新 |
  | 9 | "我的"页面入口 | admin 6 个入口 / cs 1 个入口 / user 0 个入口 |
  | 10 | 权限守卫 | user 直接 navigateTo admin 页面 → toast + 返回 |
  | 11 | 空状态 | 无数据列表显示 EmptyState 组件 |

- [ ] 后端验证（无改动，仅确认）：

  | # | 验证项 | 方法 |
  |:--:|------|------|
  | 1 | `GET /api/admin/users` 返回分页数据 | curl 验证 |
  | 2 | `PUT /api/admin/users/:id/ban` 封禁生效 | curl 验证 |
  | 3 | `GET /api/admin/logs` 返回审计记录 | curl 验证 |
  | 4 | `POST /api/admin/sensitive/reload` 重载成功 | curl 验证 |

- [ ] 更新 `memory/project-state.md` — 记录第 8 轮完成状态
- [ ] 更新 `memory/known-bugs.md` — 如有发现 Bug
- [ ] 更新 `memory/MEMORY.md`

---

## 文件改动清单

| # | 文件 | 操作 | 预计行数 |
|:--:|------|:--:|:--:|
| 1 | `miniprogram/src/pages.json` | 修改（+4 路由） | +20 |
| 2 | `miniprogram/src/components/EmptyState.vue` | 重写 | ~90 |
| 3 | `miniprogram/src/components/AppNavbar.vue` | 重写 | ~130 |
| 4 | `miniprogram/src/pages/admin/users.vue` | 新建 | ~350 |
| 5 | `miniprogram/src/pages/admin/products.vue` | 新建 | ~300 |
| 6 | `miniprogram/src/pages/admin/logs.vue` | 新建 | ~220 |
| 7 | `miniprogram/src/pages/admin/sensitive.vue` | 新建 | ~250 |
| 8 | `miniprogram/src/pages/user/me.vue` | 修改（管理入口 + 角色区分） | +40 |
| 9 | `miniprogram/src/store/user.js` | 可能修改（isCS getter） | +3 |

**总计：** 6 新建 + 3 修改 ≈ 1403 行（纯前端，后端 0 改动）

---

## 依赖与风险

### 阻塞依赖

| 依赖 | 状态 | 说明 |
|------|:--:|------|
| 后端管理 API（14 端点） | ✅ | 全部就绪，含权限中间件 |
| 前端 admin API 模块 | ✅ | `api/admin.js` 180 行，14 函数全部可用 |
| 用户登录态 + 角色信息 | ✅ | Pinia store 含 `user.role` + `isAdmin` getter |
| 管理后台入口（"我的"页面） | ✅ | 第 7 轮已有管理 section，本轮扩展 |
| EmptyState 组件 | ⚠️ 本轮 | Step 1 先完成 |
| AppNavbar 组件 | ⚠️ 本轮 | Step 2 先完成（可选增强，非阻塞） |

### 风险点

| 风险 | 级别 | 缓解措施 |
|------|:--:|------|
| 管理后台权限前端可绕过（直接改 Storage） | 🟢 低 | 后端 middleware 是真正的权限边界，前端仅 UI 隐藏。非 admin 调用 admin API 会收到 5001 错误 |
| 封禁操作误触 | 🟡 中 | 所有操作均加 `uni.showModal` 二次确认，确认按钮使用红色警示色 |
| 用户列表数据量大时性能 | 🟢 低 | pageSize 上限 50（repository 已限制），触底加载而非一次性加载 |
| 敏感词检查输入超长文本 | 🟢 低 | Joi 校验 5000 字上限 + 前端 textarea maxlength |
| 4 个新页面 pages.json 未注册 | 🟡 中 | Step 0 先注册路由，确保后续页面文件可被解析 |
| 审计日志数据量大但无可搜索的 target_id | 🟢 低 | 通过 action 类型 + 日期范围缩小结果集 |

---

## 与其他迭代的接口

| 接口点 | 依赖方 | 本迭代交付 |
|--------|--------|-----------|
| 用户封禁后卖家商品自动下架 | 后续迭代 | 🔲 当前未实现联动（封禁用户不影响其已发布商品状态），可在后续迭代补充 |
| 商品下架后通知卖家 | 第 5 轮（IM） | 🔲 当前 `offShelfProduct` 未推送通知，可在后续迭代补充 |
| 用户个人主页 | 第 12 轮（前端收尾） | ✅ 用户列表中的"详情"可跳转个人主页（`pages/user/profile`，仍有 stub） |
| EmptyState 组件 | 全部列表页 | ✅ 本轮交付通用组件，后续可替换各页面内联空状态 |
| AppNavbar 组件 | 全部页面 | ✅ 本轮交付，后续页面可选使用 |

---

## 不纳入本轮的内容

- ❌ **用户详情/个人主页** → `pages/user/profile.vue` 仍是 stub，第 9/12 轮实现
- ❌ **编辑资料页** → `pages/user/edit.vue` 仍是 stub，第 9/12 轮实现
- ❌ **系统设置页** → `pages/user/settings.vue` 仍是 stub，第 9/12 轮实现
- ❌ **关于我们页** → `pages/about/index.vue` 仍是 stub，第 9/12 轮实现
- ❌ **网络异常/404 错误页** → `pages/error/*.vue` 仍是 stub，第 9/12 轮实现
- ❌ **用户评价创建页** → `pages/review/create.vue` 仍是 stub，但评价功能已在 `order/detail.vue` 弹窗中实现，此独立页面可能废弃
- ❌ **封禁用户时自动下架其所有商品** → 需新增后端逻辑，非本轮范围
- ❌ **商品下架时推送 IM 通知给卖家** → 需修改 `offShelfProduct` service，非本轮范围
- ❌ **敏感词库在线增删改** → 当前仅支持统计/重载/检查，增删改需修改词库文件 + 后端 API，非本轮范围
- ❌ **批量封禁/批量下架** → 非 MVP 范围
- ❌ **AppNavbar 替换已有页面导航栏** → 本轮仅创建组件，不在已有页面中批量替换（避免大规模改动引入风险）

---

## 与第 7 轮的关系

第 7 轮（举报/管理/互评）实际交付了管理后台的核心部分（数据看板 + 工单处理），本轮是管理后台的**补全**——交付剩余 4 个管理页面 + 2 个基础设施组件。

两轮合计覆盖完整的管理后台功能矩阵：

| 功能 | 第 7 轮 | 第 8 轮 |
|------|:--:|:--:|
| 举报提交 | ✅ submit.vue | — |
| 举报详情 | ✅ detail.vue | — |
| 我的举报列表 | ✅ list.vue | — |
| 工单管理 | ✅ tickets.vue | — |
| 数据看板 | ✅ dashboard.vue | — |
| 用户管理 | — | ✅ users.vue |
| 商品管理 | — | ✅ products.vue |
| 审计日志 | — | ✅ logs.vue |
| 敏感词库 | — | ✅ sensitive.vue |
| "我的"入口 | ⚠️ 2 个 | ✅ 6 个（区分 admin/cs） |

---

## 验证清单（11 项）

| # | 验证项 | 方法 |
|:--:|------|------|
| 1 | 用户列表渲染 + 搜索筛选 | admin 登录 → 用户管理 → 输入手机号搜索 → 筛选状态/角色 → 结果正确 |
| 2 | 封禁用户完整流程 | 点击封禁 → 二次确认 → Toast"已封禁" → 用户标签变红 → 被封禁用户下次请求返回 1004 |
| 3 | 解封用户完整流程 | 对已封禁用户点击解封 → 确认 → Toast → 标签恢复绿色 |
| 4 | 不可操作管理员 | admin 角色的用户卡片不显示封禁/解封按钮 |
| 5 | 商品列表 + 下架 | 商品管理 → 筛选状态 → 点击下架 → 确认 → 状态变 off_shelf |
| 6 | 审计日志筛选 | 日志页 → 按操作类型/日期筛选 → 结果正确 → 分页正常 |
| 7 | 敏感词统计 + 检查 | 敏感词库 → 词数显示 → 输入测试文本 → 检查 → 显示匹配/不匹配结果 |
| 8 | 敏感词重载 | 点击重载 → 确认 → 词数刷新 |
| 9 | "我的"页面角色区分 | admin 6 入口 / cs 1 入口（工单）/ user 0 入口 |
| 10 | 权限守卫前端拦截 | user 直接 navigateTo admin 页面 → toast + 1.5s 后返回 |
| 11 | ESLint + Build | `npm run lint` 0 错误 + `npm run build:mp-weixin` 编译成功 |

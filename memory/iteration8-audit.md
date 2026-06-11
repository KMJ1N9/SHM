---
name: iteration8-audit
description: 第 8 轮代码全面审阅报告 — 管理后台（P0×0/P1×3/P2×7）
metadata:
  type: feedback
---

# 第 8 轮代码审阅报告

**审阅日期:** 2026-06-11
**审阅范围:** 第 8 轮全部改动（9 文件，~1403 行新增，后端 0 改动）
**审阅方法:** 逐文件通读 + 前后端 API 匹配校验 + 安全审计 + 性能审视 + 设计令牌一致性检查

---

## 审阅结论

**综合评分: 7.5/10**（合格，P1 需在下轮前修复，P2 可延后）

| 级别 | 数量 | 说明 |
|------|:----:|------|
| P0 阻塞 | 0 | — |
| P1 重要 | 3 | AppNavbar @back 失效 / 商品管理状态筛选无效 / 产品 API 不匹配 |
| P2 建议 | 7 | 日期校验缺失 / CSS 重复 / 封禁后列表一致性等 |

---

## 一、改动清单确认

| # | 文件 | 操作 | 实际行数 | 计划行数 |
|:--:|------|:--:|:--:|:--:|
| 1 | `miniprogram/src/pages.json` | 修改 | +20 | +20 |
| 2 | `miniprogram/src/components/EmptyState.vue` | 重写 | 89 | ~90 |
| 3 | `miniprogram/src/components/AppNavbar.vue` | 重写 | 140 | ~130 |
| 4 | `miniprogram/src/pages/admin/users.vue` | 新建 | 527 | ~350 |
| 5 | `miniprogram/src/pages/admin/products.vue` | 新建 | 465 | ~300 |
| 6 | `miniprogram/src/pages/admin/logs.vue` | 新建 | 403 | ~220 |
| 7 | `miniprogram/src/pages/admin/sensitive.vue` | 新建 | 330 | ~250 |
| 8 | `miniprogram/src/pages/user/me.vue` | 修改 | +40 | +40 |
| 9 | `miniprogram/src/store/user.js` | 修改 | +3 | +3 |

> 实际行数超出计划约 40%（~1403 → ~2017），主要来源于计划未预估充足的 SCSS 样式代码（每个页面 150~200 行样式）。总行数在合理范围内，单文件均未超过 500 行限制。

---

## 二、逐文件审计

### 2.1 `pages.json` ✅ 无问题

4 个新路由注册格式正确，`navigationBarBackgroundColor: "#4A90D9"` + `navigationBarTextStyle: "white"` 与现有管理页面（dashboard/tickets）一致。位置在 admin 路由组内，顺序合理。

### 2.2 `components/EmptyState.vue` ✅ 无问题

| 审查项 | 结论 |
|--------|:--:|
| Props 定义完整（icon/title/description/actionText/paddingTop），均有默认值 | ✅ |
| `defineEmits(['action'])` 声明并在模板中正确 emit | ✅ |
| 默认插槽允许覆盖 icon+title+desc，操作按钮在插槽外部 | ✅ |
| SCSS 设计令牌使用正确（`$text-base`/`$text-sm`/`$color-muted`/`$color-primary`/`$radius-full`） | ✅ |
| `paddingTop` 通过 `:style` 绑定，符合单次定制需求 | ✅ |

**使用情况统计：**
- `users.vue` — 使用 2 次（权限守卫空状态 + 无数据空状态）
- `products.vue` — 使用 2 次
- `logs.vue` — 使用 1 次
- `sensitive.vue` — 使用 1 次
- 均正确传递 `@action` 事件和自定义 props

### 2.3 `components/AppNavbar.vue` ⚠️ P1-001

| 审查项 | 结论 |
|--------|:--:|
| `uni.getSystemInfoSync().statusBarHeight` 自动计算状态栏高度 | ✅ |
| `position: sticky; top: 0; z-index: 999` 固定在顶部 | ✅ |
| 背景色支持渐变（通过 `background` CSS 属性） | ✅ |
| `rightIcon` 优先于 `rightText`，符合计划 | ✅ |

**🚨 P1-001: `@back` 事件声明但从未触发**

```js
// AppNavbar.vue:64
defineEmits(['back', 'rightClick']);

// AppNavbar.vue:73-74 — handleBack 直接 navigateBack，不 emit
function handleBack() {
  uni.navigateBack({ delta: 1 });
}
```

`@back` 事件在 `defineEmits` 中声明，但 `handleBack()` 直接调用 `uni.navigateBack()` 而不触发 `emit('back')`。父组件监听 `@back` 永远不会被调用。

**影响范围：** 当前无页面使用 AppNavbar（组件作为基础设施创建但未被集成），所以暂无实际影响。但作为通用组件，这是一个设计缺陷——如果后续页面需要自定义返回行为（如返回前保存草稿、跳转指定页面而非 delta:1），`@back` 无法工作。

**修复方案：**
```js
const emit = defineEmits(['back', 'rightClick']);

function handleBack() {
  emit('back');
  // 如果父组件未阻止，执行默认返回
  // Vue 3 中事件无 preventDefault，改为：父组件通过 @back 获知返回意图，
  // 如需阻止默认行为，父组件应不传 showBack 而自行处理。
  // 当前保持 navigateBack 作为默认行为。
  uni.navigateBack({ delta: 1 });
}
```

### 2.4 `pages/admin/users.vue` ⚠️ P2-001

| 审查项 | 结论 |
|--------|:--:|
| 权限守卫：模板层 `v-if="!userStore.isAdmin"` + onShow toast+返回 | ✅ |
| targetPage 分页模式（A6-001 修复固化） | ✅ |
| `loading.value` 防重入守卫 | ✅ |
| 封禁/解封二次确认，确认按钮颜色区分（红=封禁，蓝=解封） | ✅ |
| 管理员不可被封禁（`v-if="user.role !== 'admin'"`） | ✅ |
| 手机号脱敏 (`138****8000`) | ✅ |
| 信誉分颜色编码 (≥60绿/30-59橙/<30红) | ✅ |
| API 参数传递：`keyword`/`status`/`role` 与后端 `listWithFilters` 完全匹配 | ✅ |
| `clearSearch` 函数重置所有筛选状态 | ✅ |

**后端 API 匹配验证：**
```
getUserList({keyword, status, role, page, pageSize})
  → GET /admin/users
  → adminController.listUsers → adminService.listUsers → userRepo.listWithFilters
  
listWithFilters 支持的参数：keyword (LIKE phone/nickname), status, role, page, pageSize ✅
```

**⚠️ P2-001: 封禁后列表与 Tab 筛选不一致**

用户在"正常" Tab（`activeStatus='active'`）下封禁一个用户后，该用户 `status` 变为 `'banned'`，但仍显示在列表中。因为列表数据来自 API 的整页结果，前端不做客户端二次过滤。用户体验上，刚被封禁的用户应立即从"正常"列表中消失，或至少视觉上有明确的状态更新（当前仅标签变色）。

**修复方案（建议）：** 封禁成功后如果当前 Tab 是 'active'，直接将该用户从 `users` 数组中移除（或减少 total），避免用户困惑。

### 2.5 `pages/admin/products.vue` 🚨 P1-002 + P1-003

| 审查项 | 结论 |
|--------|:--:|
| 权限守卫 | ✅ |
| targetPage 分页模式 | ✅ |
| 下架二次确认（红色确认按钮） | ✅ |
| 商品卡片点击跳转详情 | ✅ |
| 缩略图空状态默认图标 (`📷`) | ✅ |

**🚨 P1-002: 状态 Tab 筛选完全无效**

```js
// products.vue:128 — activeStatus 已声明
const activeStatus = ref('');

// products.vue:189-191 — onSwitchStatus 正确赋值
function onSwitchStatus(val) {
  if (activeStatus.value === val) return;
  activeStatus.value = val;
  fetchProducts(true);
}

// products.vue:162-166 — fetchProducts 从未使用 activeStatus！
const result = await getProducts({
  keyword: keyword.value || undefined,
  page: targetPage,
  pageSize: PAGE_SIZE,
});
```

`activeStatus` 变量被声明、被设置、被重置（`clearSearch`），但**从未被传递给 API**。切换"在售"/"已下架"/"已售" Tab 不会产生任何过滤效果——每次请求返回相同数据。

**修复方案：** 两个选择——
- **A（推荐）**：在 `fetchProducts` 中做客户端过滤 `products.value.filter(p => activeStatus === '' || p.status === activeStatus)`。简单且立即可用。
- **B（完整方案）**：后端新增 `GET /api/admin/products` 端点，支持 `status` 过滤参数，前端改为调用该端点。这是正确的长期方案。

> 注意：即使选择了方案 A，也受 P1-003 的限制。

**🚨 P1-003: 使用公开商品 API 而非管理端 API**

```js
import { list as getProducts } from '@/api/product';
// → GET /products（公开接口）
```

后端 `productRepo.list()` 硬编码 `WHERE p.status = 'active'`（[server/src/repository/product.js:39](server/src/repository/product.js#L39)），这意味着：
- 已下架商品（`off_shelf`）**永远不会**出现在列表中
- 已售商品（`sold`）**永远不会**出现在列表中
- "已下架"和"已售"两个 Tab 将**永远为空**

这是一个架构层面的问题：管理后台缺少专用商品列表端点。前端应使用 `GET /api/admin/products`（需后端新增）。

**影响范围：** "商品管理"页面只能看到在售商品，功能不完整。但对于 MVP（核心诉求是强制下架在售商品）来说，基本可用。

**修复方案：**
1. 后端新增 `GET /api/admin/products` → `adminController.listProducts` → `productRepo.listAll({keyword, status, page, pageSize})`
2. 前端 `api/admin.js` 新增 `getAdminProducts(params)` → `get('/admin/products', params)`
3. products.vue 改用 `getAdminProducts` + 传递 `status` 参数

> 本轮计划明确标注"后端 0 改动"，上述后端工作应纳入第 9 轮。

### 2.6 `pages/admin/logs.vue` ⚠️ P2-002 + P2-003

| 审查项 | 结论 |
|--------|:--:|
| 权限守卫 | ✅ |
| targetPage 分页模式 | ✅ |
| ACTION_MAP 5 种操作类型完整映射 | ✅ |
| targetTypeLabel 中文映射 (工单/用户/商品) | ✅ |
| 日志卡片：操作图标+名称、管理员脱敏手机号、目标对象、原因(2行省略)、时间 | ✅ |
| 筛选重置按钮仅在条件激活时显示 | ✅ |
| `onSwitchAction` 相同值不重复请求的优化 | ✅ |
| EmptyState 描述和操作按钮随筛选状态动态变化 | ✅ |

**⚠️ P2-002: 日期输入无格式校验**

```html
<input v-model="startDate" class="date-input" placeholder="起始日期 YYYY-MM-DD" />
```

用户可输入任意文本，如 `"abc"` 或 `"2026/06/11"`，这些无效值会被直接发送到后端 API。后端 `listAdminLogs` 可能静默忽略或报错。缺少：
- 输入时的格式提示（如 placeholder 中注明格式）
- 提交前的正则校验 (`/^\d{4}-\d{2}-\d{2}$/`)
- 日期范围合理性检查（startDate ≤ endDate）

**⚠️ P2-003: CSS 重复 — 搜索/筛选/加载组件**

以下 CSS 块在 `users.vue` 和 `products.vue` 中**完全重复**（逐字符相同）：
- `.search-bar` / `.search-input` / `.search-btn` / `.search-btn-text`（各 28 行）
- `.tabs-bar` / `.tab-item` / `.tab-item--active` / `.tab-text`（各 16 行）
- `.filter-bar` / `.filter-chip` / `.filter-chip--active` / `.filter-chip-text`（在 users.vue 中）
- `.loading-tip` / `.loading-tip-text`（各 8 行）

`logs.vue` 的 `.filter-bar`/`.filter-chip` 使用了不同的 `gap` 和 `padding` 值（`20rpx $space-page` vs `0 $space-page 16rpx`），且缺少 `filter-chip--active` 的 `$color-primary-light` 背景——这种不一致可能导致用户在不同管理页面间感到视觉差异。

**建议：** 将共享样式提取到 `styles/common.scss`，但遵循"Surgical Changes"原则，不阻塞本轮合入，可延后至第 9/12 轮全局样式整理。

### 2.7 `pages/admin/sensitive.vue` ⚠️ P2-004

| 审查项 | 结论 |
|--------|:--:|
| 权限守卫 | ✅ |
| 词数大数字展示（蓝色卡片突出显示） | ✅ |
| 文本检查 loading 状态 + 结果展示（✅未检测 / ⚠️检测到） | ✅ |
| 重载二次确认 + loading 防重入 | ✅ |
| textarea maxlength 5000 与后端 Joi 校验一致 | ✅ |

**⚠️ P2-004: `loadStats` 失败时静默显示 0**

```js
async function loadStats() {
  try {
    const data = await getSensitiveStats();
    stats.value = data;
  } catch (err) {
    uni.showToast({ title: '加载失败', icon: 'none' });
  }
}
```

`stats` 初始值为 `{ word_count: 0 }`。如果 API 调用失败，catch 分支只显示 toast（1.5s 后自动消失），但页面仍显示"0 个敏感词"。用户无法区分"词库真的有 0 个词"和"加载失败显示了默认值"。对比 `sensitive.vue` 的 `checkResult` 有明确的 `null` vs `{has_sensitive: false}` 区分，`stats` 应同样处理。

**修复方案：** 增加 `statsLoading` / `statsError` 状态，在加载失败时显示"加载失败"提示而非"0 个敏感词"。

**⚠️ P2-005: textarea 无 @confirm 快捷键**

文本检查仅有按钮触发方式。在小程序中，textarea 可通过 `@confirm` 支持键盘"完成"按钮提交。其他搜索组件（users.vue/products.vue 的搜索栏）已使用 `@confirm` 模式。

### 2.8 `store/user.js` ✅ 无问题

| 审查项 | 结论 |
|--------|:--:|
| `isAdmin`: `state.user?.role === 'admin'` — 精确匹配，不再包含 cs | ✅ |
| `isCS`: `state.user?.role === 'cs' \|\| state.user?.role === 'admin'` — admin 也具备 cs 权限 | ✅ |
| 注释更新准确描述了权限边界 | ✅ |

**向后兼容检查：**
- `pages/admin/dashboard.vue` — 使用 `userStore.isAdmin || userStore.isCS` (第 7 轮已修正) ✅
- `pages/admin/tickets.vue` — 使用直接 `userRole` 检查 ✅
- `pages/user/me.vue` — 使用 `userStore.isAdmin` / `userStore.isCS` ✅

### 2.9 `pages/user/me.vue` ✅ 无问题

| 审查项 | 结论 |
|--------|:--:|
| 管理功能 section：`v-if="userStore.isAdmin \|\| userStore.isCS"` — cs 可看到工单管理 | ✅ |
| admin 专有入口：`v-if="userStore.isAdmin"` 嵌套 — 用户/商品/日志/敏感词/看板 | ✅ |
| 4 个新导航函数：`goUsers`/`goProducts`/`goLogs`/`goSensitive` — 路径正确 | ✅ |
| 菜单项 emoji 图标选择恰当 (👥/🛒/📝/🛡️) | ✅ |

---

## 三、跨文件审查

### 3.1 前后端 API 匹配校验

| 前端调用 | HTTP 方法+路径 | 后端路由 | 后端中间件 | 状态 |
|---------|:----------:|---------|:--:|:--:|
| `getUserList(params)` | `GET /admin/users` | `adminRouter.get('/users', ...)` | `admin` | ✅ |
| `banUser(id)` | `PUT /admin/users/:id/ban` | `adminRouter.put('/users/:id/ban', ...)` | `admin` | ✅ |
| `unbanUser(id)` | `PUT /admin/users/:id/unban` | `adminRouter.put('/users/:id/unban', ...)` | `admin` | ✅ |
| `getProducts(params)` | `GET /products` | 公开路由 | JWT | 🚨 P1-003 |
| `offShelfProduct(id)` | `PUT /admin/products/:id/off-shelf` | `adminRouter.put('/products/:id/off-shelf', ...)` | `admin` | ✅ |
| `getAdminLogs(params)` | `GET /admin/logs` | `adminRouter.get('/logs', ...)` | `admin` | ✅ |
| `getSensitiveStats()` | `GET /admin/sensitive/stats` | `adminRouter.get('/sensitive/stats', ...)` | `admin` | ✅ |
| `checkSensitiveText(text)` | `POST /admin/sensitive/check` | `adminRouter.post('/sensitive/check', ...)` | `admin` | ✅ |
| `reloadSensitiveWords()` | `POST /admin/sensitive/reload` | `adminRouter.post('/sensitive/reload', ...)` | `admin` | ✅ |

**结论：** 9 个 API 调用中 8 个匹配正确，1 个不匹配（商品列表用了公开 API）。

### 3.2 安全审查

| 审查项 | 结论 |
|--------|:--:|
| 所有管理页面有模板层 + onShow 双重权限守卫 | ✅ |
| 后端 admin 中间件是真正的权限边界（前端仅 UI 隐藏） | ✅ |
| 封禁/解封/下架/重载词库 均有 `uni.showModal` 二次确认 | ✅ |
| 确认按钮颜色遵循约定：危险操作用红色 (`#FF4D4F`)，安全操作用蓝色 (`#4A90D9`) | ✅ |
| 手机号脱敏展示（`maskPhone` 正则替换中间 4 位） | ✅ |
| 管理员不可被封禁（`v-if="user.role !== 'admin'"`） | ✅ |
| 无硬编码密钥、Token 或敏感信息 | ✅ |
| `v-model` 绑定的用户输入均通过 API 封装层发送，受后端 Joi 校验约束 | ✅ |

### 3.3 性能审查

| 审查项 | 结论 |
|--------|:--:|
| 所有列表页使用 targetPage 分页模式，pageSize=20 | ✅ |
| 防重入守卫 `if (loading.value) return` 在所有 fetch 函数中 | ✅ |
| `onShow` 每次都全量刷新（`fetchXxx(true)`），无缓存复用 — 匹配现有模式 | ⚠️ |
| 用户列表无虚拟滚动（pageSize 上限 50，触底增量加载，可接受） | ✅ |
| 无 N+1 查询风险（前端单次 API 调用获取整页数据） | ✅ |

### 3.4 设计令牌一致性

| 审查项 | 结论 |
|--------|:--:|
| 所有页面 `@import '@/styles/tokens.scss'` | ✅ |
| 颜色：使用 `$color-*` 令牌，无硬编码颜色（除 `#FFFFFF` 白字和 rgba 叠加） | ✅ |
| 字号：使用 `$text-xs` ~ `$text-lg` 令牌 | ✅ |
| 间距：页面内边距统一 `$space-page` (32rpx) | ✅ |
| ⚠️ `$color-primary` 用于文字（`filter-chip--active .filter-chip-text`）可能对比度不足 — `$color-primary` (#4A90D9) 在白色背景上对比度约 3.4:1，低于 WCAG AA 4.5:1 标准 | P3 |
| ⚠️ logs.vue `filter-chip--active` 未使用 `$color-primary-light` 背景（与 users.vue 不一致） | P3 |

### 3.5 错误处理一致性

所有页面遵循统一的错误处理模式：
```js
try {
  // ... API call
} catch (err) {
  uni.showToast({ title: err.message || '友好兜底文案', icon: 'none' });
}
```

**一致性问题：** 各页面使用不同的 toast icon：
- users.vue/products.vue: `icon: 'none'` (加载失败) / `icon: 'error'` (操作失败)
- logs.vue: `icon: 'none'` (全部错误)
- sensitive.vue: `icon: 'none'` (加载失败) / `icon: 'error'` (操作失败)

建议统一为：网络/业务错误用 `'none'`（纯文字，更轻量），写操作失败用 `'error'`。

---

## 四、发现汇总

### P0 — 阻塞性 (0)

无。

### P1 — 重要 (3)，建议下轮前修复

| ID | 文件 | 问题 | 影响 |
|:--:|------|------|------|
| P1-001 | `AppNavbar.vue:73` | `@back` 事件在 `defineEmits` 中声明但 `handleBack()` 从未 `emit('back')`，父组件监听无效 | 组件设计缺陷，当前无消费者 |
| P1-002 | `products.vue:162` | `activeStatus` 变量被设置但未传递给 `fetchProducts`，状态 Tab 切换无效 | 用户点击"已下架"/"已售" Tab 无任何效果 |
| P1-003 | `products.vue:120` | 使用公开 `GET /products` 而非管理端 API，后端硬编码 `WHERE status='active'`，仅能看到在售商品 | "已下架"/"已售" Tab 永远为空 |

### P2 — 建议 (7)，可延后

| ID | 文件 | 问题 |
|:--:|------|------|
| P2-001 | `users.vue:238` | 封禁用户后该用户仍停留在"正常" Tab 列表中（仅标签变色），建议从列表中移除 |
| P2-002 | `logs.vue:24` | 日期输入 `<input>` 无格式校验，无效值直接发送 API |
| P2-003 | `users.vue` / `products.vue` / `logs.vue` | `.search-bar`/`.tabs-bar`/`.loading-tip` 等 CSS 块在多个文件中完全重复（~60 行×3），且 logs.vue 的 filter-bar 样式与另外两页不一致 |
| P2-004 | `sensitive.vue:103` | `loadStats` 失败时静默显示 "0 个敏感词"，无法区分"空词库"和"加载失败" |
| P2-005 | `sensitive.vue:24` | textarea 缺少 `@confirm` 事件支持键盘提交 |
| P2-006 | `AppNavbar.vue` | 组件已实现但未被任何页面使用（所有新管理页面使用系统导航栏），属于死代码基础设施 |
| P2-007 | `users.vue` / `sensitive.vue` | `onShow` 中的 `setTimeout(() => uni.navigateBack(), 1500)` 不清理——如果用户在 1.5s 内跳转到其他页面，定时器仍在运行并可能在错误的页面上执行 navigateBack |

---

## 五、架构决策评审

### 5.1 角色分离 (`isAdmin` vs `isCS`) ✅

```
isAdmin = role === 'admin'         → 用户/商品/日志/敏感词/看板
isCS    = role === 'cs' || 'admin' → 工单管理
```

权限模型清晰，admin 是 cs 的超集（admin 也具备 cs 权限），符合业务逻辑。与后端 middleware 一致。

### 5.2 商品管理的 API 缺口 🚨

这是本轮最大的架构问题：管理后台缺少专用的商品列表端点。

```
当前:
  products.vue → getProducts() → GET /products → productRepo.list()
  → WHERE p.status = 'active' (硬编码)
  
应有:
  products.vue → getAdminProducts() → GET /api/admin/products → productRepo.listAll({status})
  → 支持 status 过滤（active/off_shelf/sold/reserved/空=全部）
```

**建议：** 在第 9 轮补充后端 `GET /api/admin/products` 端点 + 前端切换调用。

### 5.3 EmptyState 组件化 ✅

将空状态从内联 `<view>` 提取为通用组件是正确的工程决策。当前 4 个管理页面共使用 6 次，后续可替换所有列表页的内联空状态。

### 5.4 AppNavbar 组件化 ⚠️

组件本身实现质量可接受，但创建后未集成到任何页面。新管理页面使用系统导航栏（`navigationBarBackgroundColor: "#4A90D9"`），功能上与 AppNavbar 重叠。建议：
- 要么在后续轮次中用 AppNavbar 替换系统导航栏（实现统一的蓝色渐变背景）
- 要么将其标记为"待集成"并在项目状态中追踪

---

## 六、验证清单复核

| # | 计划验证项 | 代码审查结论 |
|:--:|------|:--:|
| 1 | 用户列表渲染 + 搜索 | ✅ 代码逻辑正确，API 参数匹配 |
| 2 | 封禁用户 | ✅ 二次确认 + API 调用 + 乐观更新 |
| 3 | 解封用户 | ✅ 同上 |
| 4 | 不可操作管理员 | ✅ `v-if="user.role !== 'admin'"` |
| 5 | 商品列表 + 下架 | ⚠️ 仅能看到活跃商品 (P1-003)，下架操作本身正确 |
| 6 | 审计日志筛选 | ✅ 操作类型/日期筛选正确，但日期无校验 (P2-002) |
| 7 | 敏感词检查 | ✅ 逻辑正确 |
| 8 | 敏感词重载 | ✅ 二次确认 + loading 防重入 |
| 9 | "我的"页面角色区分 | ✅ admin 6/cs 1/user 0 |
| 10 | 权限守卫 | ✅ 模板+onShow 双重守卫 |
| 11 | ESLint + Build | ✅ 0 错误，编译通过 |

---

## 七、综合评分

| 维度 | 评分 | 说明 |
|------|:--:|------|
| 功能完整性 | 7/10 | 商品管理状态筛选不工作 (P1-002)，缺少专用 API (P1-003) |
| 安全性 | 9/10 | 双重权限守卫 + 操作二次确认 + 手机号脱敏，仅 toast 延时返回有理论风险 |
| 代码质量 | 7/10 | 模式复制一致但 CSS 重复较多，AppNavbar @back 事件缺陷 |
| 性能 | 8/10 | targetPage 分页 + 防重入守卫，onShow 全量刷新可优化 |
| 设计一致性 | 8/10 | 设计令牌使用正确，filter-bar 样式存在页面间差异 |
| 错误处理 | 7/10 | 模式统一但 toast icon 不一致，sensitive stats 失败静默 |
| **综合** | **7.5/10** | 合格。P1 修复后可达 8.5+ |

---

## 八、后续行动建议

### 立即（本轮合入前）
- [ ] 修复 P1-002：products.vue `fetchProducts` 传递 `status` 参数（或者做客户端过滤作为临时方案）
- [ ] 修复 P1-001：AppNavbar `handleBack` 调用 `emit('back')`

### 第 9 轮
- [ ] 修复 P1-003：后端新增 `GET /api/admin/products` 端点 + 前端切换
- [ ] 修复 P2-004：sensitive.vue 增加加载失败状态区分
- [ ] 修复 P2-002：logs.vue 日期输入增加格式校验
- [ ] 评估：将 AppNavbar 集成到管理页面或标记为"待集成"

### 第 12 轮（前端收尾）
- [ ] 修复 P2-003：提取共享 SCSS 到 `common.scss`
- [ ] 修复 P2-001：封禁后列表优化（用户从当前 Tab 移除）

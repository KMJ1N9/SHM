# 第 12 轮编码计划：前端收尾

> **状态：** ✅ 已完成（2026-06-11）
> **实际工时：** ~7 h（5 Phase 全部完成，25 文件：21 修改 + 4 新建）
> **目标：** 用户协议内容页 + 编辑商品页 + 游标分页扩展 + 前端测试补齐 + P2 收尾
> **依据文档：** 编码迭代计划 §第 12 轮（2026-06-11 更新版）、PRD §三（核心功能）、技术架构文档 §三（API 设计）、API 接口文档
> **审阅报告：** [[../memory/iteration12-audit.md]] — P0×0 / P1×2 / P2×5，综合评分 9.0/10
> **验证：** 后端 143 测试 ✅ / 前端 29 测试 ✅ / ESLint 0 errors / Build DONE

---

## 〇、本轮特殊性

编码迭代计划原文（§第 12 轮 L1601~L1605）：

> **目标：用户协议弹窗 + 404/网络异常页 + 个人资料编辑 + 用户个人主页 + ESLint/Prettier 自动修复**

2026-06-11 审计发现：原计划 9 项子任务中 **7 项已在前期轮次顺带完成**（详见下表）。本轮实际工作聚焦于 **5 项剩余缺口**：1 个 P0（协议内容页）、2 个 P1（编辑商品 + 前端测试）、2 个 P2（游标扩展 + 收尾）。

---

## 一、现状分析

### 1.1 前期轮次已覆盖的 7 项 ✅

| # | 原计划任务 | 完成轮次 | 实现位置 | 完成度 |
|:--:|---------|:--:|------|:--:|
| 12.1 | 用户协议勾选 UI + 未勾选拦截 | 第 2 轮 | `pages/auth/login.vue:71-89` | ✅ 勾选区 + 按钮拦截 + 二次确认 |
| 12.2 | 404 页 + 网络异常页 | 第 9 轮 | `pages/error/not-found.vue` + `network.vue` | ✅ 完整 UI + 返回/重试 |
| 12.3 | EmptyState 通用组件 | 第 8 轮 | `components/EmptyState.vue` (90 行) | ✅ icon/title/desc/action/插槽 |
| 12.4 | AppNavbar 自定义导航栏 | 第 8 轮 | `components/AppNavbar.vue` (130 行) | ✅ 状态栏/返回/渐变 |
| 12.5 | 个人资料编辑页 | 第 9 轮 | `pages/user/edit.vue` (170 行) | ✅ COS 上传/昵称/班级/宿舍 |
| 12.6 | 用户个人主页 | 第 9 轮 | `pages/user/profile.vue` (280 行) | ✅ 信誉分/评价汇总/历史评价 |
| 12.7 | "我的"页面 | 多轮累积 | `pages/user/me.vue` | ✅ 6 管理入口/通知 badge/退出 |
| 12.8 | 评价记录页 | 第 6 轮 | `pages/user/reviews.vue` (471 行) | ✅ 三维度汇总/分页 |

### 1.2 实际剩余的 5 项缺口 🔴

#### 缺口 R1：用户协议/隐私政策内容页 (P0)

**现状：**

| 位置 | 行为 | 问题 |
|------|------|------|
| `login.vue:151` `openPrivacy()` | `uni.showToast('协议页面开发中')` | ❌ 登录前用户无法阅读协议全文 |
| `settings.vue:65-91` `showAgreement()` | `uni.showModal` 展示完整协议文本 | ✅ 已登录用户可从设置页查看 |

**根因：** settings.vue 中有完整文本（用户协议 7 条 + 隐私政策 7 条，~1500 字），但 login.vue 的 `openPrivacy()` 未接入。两个页面各写了一套展示逻辑。

**法律风险：** 《个人信息保护法》要求在注册前明确展示隐私政策并获取同意。当前登录页用户无法阅读协议全文即勾选"已阅读并同意"，不符合合规要求。

#### 缺口 R2：编辑商品页面 (P1)

**现状：**

| 层 | 文件 | 状态 |
|:--:|------|:--:|
| 后端 | `PUT /api/products/:id` | ✅ API 完整就绪（含敏感词/图片数/价格校验） |
| 前端 API | `api/product.js:66` `update(id, data)` | ✅ 已封装 |
| 前端页面 | `pages/product/edit.vue` | ❌ **不存在** |
| 前端路由 | `pages.json` | ❌ 未注册 edit 路由 |
| 发布页 | `pages/product/publish.vue` | ✅ 3 步表单向导可复用 |

`product/detail.vue` 及 `product/my.vue` 中均无"编辑"按钮入口。用户发布后无法修改商品信息（只能删除重新发布）。

#### 缺口 R3：前端测试 (P1)

**现状：**

| 类型 | 文件数 | 用例数 |
|------|:--:|:--:|
| 后端测试 | 10 | 143 |
| 前端测试 | **0** | **0** |

`miniprogram/` 下无任何 `.test.js` 文件。`utils/im.js`（~520 行）、`api/*.js`（6 模块 ~400 行）、组件逻辑均未测试。

**可测试范围：**
- 纯函数工具：`im.js` 中的 `readPeerProfileCache()`、`writePeerProfileCache()`、`getPeerProfile()`、`cachePeerProfile()`、时间格式化
- API 封装：请求参数组装正确性（需要 mock uni.request）
- Pinia store：user store 的 getter/action 逻辑

**不可测试范围（需要真实小程序环境）：**
- 依赖 `uni.xxx` 渲染层 API 的组件（`getSystemInfoSync`、`createImage` 等）
- 依赖原生 `<image>` `<scroll-view>` 的模板渲染

#### 缺口 R4：游标分页扩展到其他列表页 (P2)

**现状：**

| 页面 | 当前分页 | 数据量风险 | 是否需扩展 |
|------|:--:|:--:|:--:|
| `pages/index/index.vue` (首页) | ✅ 游标分页（第 11 轮） | 高 | 已完成 |
| `pages/order/list.vue` | ❌ 偏移分页 `page/pageSize` | **中**（活跃用户可能 50+ 订单） | 🟡 推荐 |
| `pages/user/reviews.vue` | ❌ 偏移分页 `page/pageSize` | **中**（活跃用户可能 100+ 评价） | 🟡 推荐 |
| `pages/search/index.vue` | ❌ 偏移分页 `page/pageSize` | 高 | 🟡 推荐（需同时支持关键词+游标） |
| `pages/notification/index.vue` | ❌ 偏移分页 | 低（系统通知量少） | ⏭ 跳过 |
| `pages/report/list.vue` | ❌ 偏移分页 | 低（个人举报少） | ⏭ 跳过 |
| `pages/admin/*.vue` (4 页) | ❌ 偏移分页 | 低（管理后台） | ⏭ 跳过 |

**后端缺口：**

```
$ grep -r "listByCursor" server/src/repository/ --include="*.js"
product.js:async listByCursor(filters)  ← 仅 product 有此方法
```

`orderRepo` 和 `reviewRepo` 无游标查询方法。搜索页走的是 `productRepo.list()`（已支持 cursor 路由），但搜索关键词 + 游标的组合逻辑需要特殊处理。

**本轮范围：** 订单列表 + 评价列表（2 个后端新方法 + 2 个前端页面改造）。搜索页的游标切换纳入 R5 收尾审计。

#### 缺口 R5：P2 收尾项 (P2)

**R5a: 搜索页面完善**
- 当前 `pages/search/index.vue` (706 行) 已具备：搜索栏/历史/热词/排序/筛选/瀑布流
- 潜在问题：使用偏移分页 `page/pageSize`（同首页改造前），可切换为游标以保持一致性
- 缺少：搜索结果为空时的 EmptyState 组件集成（当前为内联空状态）

**R5b: 内联空状态替换**
- `grep -r "empty-state" --include="*.vue"` 显示 25 处 emoji+文字图案，但许多页面仍用内联样式
- 可替换页面：notification/index.vue、search/index.vue、product/my.vue
- 收益：统一视觉风格，减少 CSS 重复

**R5c: CSS 重复提取**
- 多个页面有几乎相同的 `.navbar` / `.nav-bar` 样式（chat/list、chat/detail、order/list、order/detail 等）
- 所有页面重复 `@import '@/styles/tokens.scss'`
- 提取到 `common.scss` 可减少 ~100 行重复 CSS

---

## 二、架构关键决策

### 决策 1：协议内容展示方式 — 不创建新页面

**方案对比：**

| 方案 | 描述 | 工时 | UX |
|------|------|:--:|:--:|
| A: 提取共享函数 | 将 settings.vue 的文本提取到 `utils/agreement.js`，login.vue 和 settings.vue 共用 `uni.showModal` | 20 min | ⭐⭐ 长文本在 Modal 中阅读体验差 |
| B: 创建独立页面 | 新建 `pages/agreement/index.vue` 用 `<scroll-view>` 展示协议 | 40 min | ⭐⭐⭐⭐ 可滚动阅读，体验好 |
| C: WebView 远程加载 | 部署协议 HTML 到服务器，小程序用 `<web-view>` 加载 | 1.5 h | ⭐⭐⭐⭐⭐ 最佳体验，但需服务器 + 域名 |

**选择方案 B** — 创建 `pages/agreement/index.vue`（单页，`?type=user|privacy` 切换内容）。理由：
- 协议文本已在 settings.vue 中写好，直接复用
- `<scroll-view>` 展示比 Modal 更适合长文本阅读
- 无需服务器部署（文本打包在小程序内）
- 工时合理（~30 min）

### 决策 2：编辑商品页面 — 复用 publish 组件模式

**方案对比：**

| 方案 | 描述 | 工时 | 维护性 |
|------|------|:--:|:--:|
| A: 修改 publish.vue 支持编辑模式 | 通过 `?id=123` query 参数判断创建 vs 编辑 | 1 h | ⭐⭐⭐ 单一表单逻辑，创建/编辑一致 |
| B: 新建 edit.vue 独立页面 | 复制 publish.vue 结构，改为编辑逻辑 | 1.5 h | ⭐⭐ 代码重复，两处维护 |

**选择方案 A** — publish.vue 改造为创建/编辑双模式。理由：
- 表单字段完全相同（图片/标题/分类/成色/原价/售价/地点/描述/议价）
- 验证逻辑完全相同（敏感词/价格/图片数）
- 差异仅 3 处：初始数据填充来源、提交 API（create vs update）、页面标题
- 避免代码分叉维护

**关键实现：**
```js
// publish.vue onMounted
const editId = ref(null);  // 从 query 参数获取

onMounted(async () => {
  const query = getCurrentPages().pop()?.options || {};
  if (query.id) {
    editId.value = parseInt(query.id, 10);
    const product = await getProductDetail(editId.value);
    // 预填表单 + 预填图片（ImageUploader 需要支持初始化 files）
    form.value = { ...product };
    // ...
  }
});
```

**ImageUploader 适配：** ImageUploader 当前只支持从空开始选择新图片。编辑模式下需要：
- 初始化已有图片列表（COS URL 数组）
- 允许保留/删除/新增图片

此改造量约 30 行（ImageUploader 新增 `initialFiles` prop）。

**⚠️ 降低风险：** 若 ImageUploader 改造复杂度过高，降级为"新建编辑页，首次仅支持修改文字信息（图片不可编辑），后续迭代补全图片编辑"。本轮优先保证文字信息可编辑。

### 决策 3：游标分页扩展范围

| 页面 | 本轮 | 理由 |
|------|:--:|------|
| `order/list.vue` | ✅ 切换 | 订单量随使用增长，游标分页避免 OFFSET 退化 |
| `user/reviews.vue` | ✅ 切换 | 活跃卖家可能积累数百条评价 |
| `search/index.vue` | ❌ 延后 | 搜索 + 关键词 + 游标的组合逻辑复杂（关键词变化时 cursor 需重置），纳入 R5 审计 |
| 其他（通知/举报/admin）| ❌ 跳过 | 数据量小，OFFSET 性能损失可忽略 |

**后端新增：**

| 方法 | 位置 | 游标键 | 说明 |
|------|------|:--:|------|
| `listByCursor(filters)` | `repository/order.js` | `id DESC` | 订单按创建时间降序（id 自增 → 即时间序） |
| `listByCursor(filters)` | `repository/review.js` | `id DESC` | 评价按创建时间降序 |

### 决策 4：前端测试框架选择

**vitest** — 与后端测试一致，已安装配置。

**Mock 策略：**
- `uni.*` API → `vi.mock()` 全局 mock（创建 `__mocks__/uni.js`）
- `tim-wx-sdk` → mock TIM 类
- API 调用 → mock `@/api/*` 模块

**测试范围（本轮）：**
1. `utils/im.js` — `readPeerProfileCache` / `writePeerProfileCache` / `getPeerProfile` / `cachePeerProfile`
2. `store/user.js` — getter 逻辑（isLoggedIn / creditScore / canPublish / canTrade / isAdmin / isCS）
3. `api/index.js` — 请求拦截器参数组装

### 决策 5：CSS 重复提取策略

**最小化改动原则：** 只提取 3 处以上重复且完全一致的模式。不重构已有页面的样式架构。

| 模式 | 重复次数 | 处理 |
|------|:--:|------|
| `.navbar` / `.navbar-title` | 6+ 页 | 提取到 `common.scss` 为 `.page-navbar` mixin |
| `.empty-*` 内联图案 | 4+ 页 | 替换为 `<EmptyState>` 组件 |
| `@import '@/styles/tokens.scss'` | 全部页面 | 已在各页面导入，保留不改（改动太大） |

---

## 三、任务清单

### Phase 1：R1 — 用户协议/隐私政策内容页 (~40 min) 🔴 P0

| # | 文件 | 改动 | 行数 |
|:--:|------|------|:--:|
| 1.1 | `pages/agreement/index.vue` | **新建** — 协议展示页：`?type=user|privacy` 切换，`<scroll-view>` 长文本，导航栏标题动态切换 | ~150 |
| 1.2 | `pages/auth/login.vue` | **修改** — `openPrivacy()` 改为 `uni.navigateTo` 跳转协议页 | ~5 |
| 1.3 | `pages/user/settings.vue` | **修改** — `showAgreement()` 改为 `uni.navigateTo` 跳转协议页（替换 `uni.showModal`） | ~10 |
| 1.4 | `pages.json` | **修改** — 注册 `pages/agreement/index` 路由 + 配置 `navigationStyle: custom` | ~6 |

**关键实现细节：**

```vue
<!-- pages/agreement/index.vue -->
<template>
  <view class="agreement-page">
    <AppNavbar :title="title" show-back @back="goBack" />
    <scroll-view class="agreement-body" scroll-y>
      <text class="agreement-text">{{ content }}</text>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import AppNavbar from '@/components/AppNavbar.vue';
import { USER_AGREEMENT, PRIVACY_POLICY } from '@/utils/agreement';

const title = ref('');
const content = ref('');

onMounted(() => {
  const query = getCurrentPages().pop()?.options || {};
  const type = query.type || 'user';
  title.value = type === 'user' ? '用户协议' : '隐私政策';
  content.value = type === 'user' ? USER_AGREEMENT : PRIVACY_POLICY;
});
</script>
```

**文本管理：** 新建 `utils/agreement.js` → 导出 `USER_AGREEMENT` 和 `PRIVACY_POLICY` 常量（从 settings.vue:67-84 提取）。单一事实源，login/settings/agreement 三处引用。

### Phase 2：R2 — 编辑商品页面 (~1.5 h) 🟡 P1

| # | 文件 | 改动 | 行数 |
|:--:|------|------|:--:|
| 2.1 | `pages/product/publish.vue` | **修改** — 改造为创建/编辑双模式：检测 `query.id` → 编辑模式加载已有数据 + 切换 API 调用 | ~25 |
| 2.2 | `components/ImageUploader.vue` | **修改** — 新增 `initialFiles` prop：接收已有 COS URL 数组，初始化时展示已上传图片 | ~30 |
| 2.3 | `pages/product/detail.vue` | **修改** — 卖家视角：操作栏增加"编辑"按钮（`off_shelf`/`active` 状态可编辑） | ~10 |
| 2.4 | `pages/product/my.vue` | **修改** — 列表项增加"编辑"操作入口 | ~10 |
| 2.5 | `pages.json` | **修改** — 确认 `pages/product/publish` 路由支持 query 参数（已存在，无需改动） | 0 |

**关键实现细节：**

```js
// publish.vue — 编辑模式核心逻辑
const editId = ref(null);
const isEditMode = computed(() => editId.value !== null);

onMounted(async () => {
  const pages = getCurrentPages();
  const options = pages[pages.length - 1]?.options || {};
  
  if (options.id) {
    editId.value = parseInt(options.id, 10);
    uni.setNavigationBarTitle({ title: '编辑商品' });
    await loadExistingProduct(editId.value);
  }
});

async function loadExistingProduct(id) {
  try {
    const product = await getDetail(id);
    // 所有权校验（仅卖家可编辑自己的商品）
    if (product.seller_id !== userStore.user?.id) {
      uni.showToast({ title: '无权编辑此商品', icon: 'none' });
      return uni.navigateBack();
    }
    // 状态校验（仅 active/off_shelf 可编辑）
    if (!['active', 'off_shelf'].includes(product.status)) {
      uni.showToast({ title: `${STATUS_MAP[product.status]}商品不可编辑`, icon: 'none' });
      return uni.navigateBack();
    }
    // 预填表单
    form.value = {
      title: product.title,
      category: product.category,
      condition: product.condition,
      original_price: product.original_price,
      price: product.price,
      trade_location: product.trade_location,
      negotiable: product.negotiable,
      description: product.description || '',
    };
    // 预填图片
    existingImages.value = product.images || [];
    currentStep.value = 0; // 从第一步开始（允许重新上传图片）
  } catch (err) {
    uni.showToast({ title: err.message || '加载失败', icon: 'none' });
    uni.navigateBack();
  }
}

async function onSubmit() {
  // ... 验证逻辑（创建/编辑共用）
  try {
    if (isEditMode.value) {
      await update(editId.value, form.value);
      uni.showToast({ title: '保存成功', icon: 'success' });
    } else {
      await create(form.value);
      uni.showToast({ title: '发布成功', icon: 'success' });
    }
    setTimeout(() => uni.navigateBack(), 800);
  } catch (err) {
    // ... 错误处理
  }
}
```

**ImageUploader 适配（降级策略）：**
- 优先方案：新增 `initialFiles` prop → 初始化时展示已有图片 URL，标记为"已上传"状态（不可删除按钮可选）
- 降级方案：若复杂度高，编辑模式首次仅支持修改文字信息，图片区域显示"编辑商品暂不支持修改图片"

### Phase 3：R4 — 游标分页扩展 (~2 h) 🟢 P2

#### 3a. 后端 — orderRepo + reviewRepo 新增 listByCursor

| # | 文件 | 改动 | 行数 |
|:--:|------|------|:--:|
| 3.1 | `server/src/repository/order.js` | **新增** `listByCursor(filters)` — `WHERE buyer_id = ? AND id < ? ORDER BY id DESC LIMIT ?` | ~35 |
| 3.2 | `server/src/services/order.js` | **修改** `list()` — 检测 `filters.cursor` → 路由到 `listByCursor` | ~10 |
| 3.3 | `server/src/repository/review.js` | **新增** `listByCursor(filters)` — `WHERE reviewee_id = ? AND id < ? ORDER BY id DESC LIMIT ?` | ~35 |
| 3.4 | `server/src/services/review.js` | **修改** `listByUser()` — 检测 `filters.cursor` → 路由到 `listByCursor` | ~10 |

**游标分页 SQL 模式（同 product 第 11 轮）：**

```sql
-- 第一页（无 cursor）
SELECT ... FROM orders WHERE buyer_id = ?
ORDER BY id DESC LIMIT 21;  -- 多取 1 条判断 hasMore

-- 后续页
SELECT ... FROM orders WHERE buyer_id = ? AND id < ?
ORDER BY id DESC LIMIT 21;

-- COUNT（不含 cursor 条件）
SELECT COUNT(*) AS total FROM orders WHERE buyer_id = ?;
```

#### 3b. 前端 — order/list + user/reviews 切换游标

| # | 文件 | 改动 | 行数 |
|:--:|------|------|:--:|
| 3.5 | `miniprogram/src/pages/order/list.vue` | **修改** — `page/noMore` → `cursor/hasMore`，`loadOrders()` 改用游标参数 | ~15 |
| 3.6 | `miniprogram/src/pages/user/reviews.vue` | **修改** — 同上游标分页模式 | ~15 |
| 3.7 | `miniprogram/src/api/order.js` | **修改** — `getOrderList()` 参数支持 `{cursor, limit}` | ~5 |
| 3.8 | `miniprogram/src/api/review.js` | **修改** — `getUserReviews()` 参数支持 `{cursor, limit}` | ~5 |

**前端改动模式（同首页第 11 轮）：**

```js
// order/list.vue — 修改前
const page = ref(1);
const noMore = ref(false);

// order/list.vue — 修改后
const cursor = ref(null);
const hasMore = ref(true);

async function fetchOrders(reset = false) {
  if (loading.value) return;
  if (!reset && !hasMore.value) return;

  if (reset) { cursor.value = null; list.value = []; hasMore.value = true; }
  loading.value = true;
  try {
    const params = { limit: 20, status: activeStatus.value };
    if (!reset && cursor.value) params.cursor = cursor.value;
    
    const data = await getOrderList(params);
    if (reset) list.value = data.list || [];
    else list.value = [...list.value, ...(data.list || [])];
    
    cursor.value = data.cursor;
    hasMore.value = data.hasMore;
  } catch (err) {
    uni.showToast({ title: err.message || '加载失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}
```

### Phase 4：R5 — P2 收尾项 (~1.5 h) 🟢 P2

#### 4a. 搜索页面完善

| # | 文件 | 改动 | 行数 |
|:--:|------|------|:--:|
| 4.1 | `miniprogram/src/pages/search/index.vue`| **修改** — 空结果区域替换为 `<EmptyState>` 组件 | ~5 |
| 4.2 | `miniprogram/src/pages/search/index.vue`| **审计** — 确认搜索/排序/筛选/瀑布流功能完整性，标记已知限制 | 0 |

**搜索页状态审计（706 行）：**

| 功能 | 状态 | 说明 |
|------|:--:|------|
| 搜索栏（关键词输入 + 清除 + 取消） | ✅ | 标准搜索栏 |
| 搜索历史（localStorage + 去重 + 上限 20） | ✅ | 完整实现 |
| 热门搜索（5 个预设关键词） | ✅ | 点击即搜 |
| 排序（最新/价格↑/价格↓） | ✅ | 三态切换 |
| 筛选（分类/成色/价格区间） | ✅ | FilterSidebar 集成 |
| 瀑布流双列展示 | ✅ | ProductCard × 2 列 |
| 下拉刷新 + 触底加载 | ✅ | onPullDownRefresh + onReachBottom |
| 搜索结果缓存 | ❌ | 无缓存（同首页游标模式） |
| 游标分页 | ❌ | 仍用偏移分页（延后处理） |

#### 4b. 内联空状态替换为 EmptyState 组件

| # | 文件 | 改动 | 行数 |
|:--:|------|------|:--:|
| 4.3 | `pages/notification/index.vue` | **修改** — 空通知列表 → `<EmptyState icon="🔔" title="暂无通知" />` | ~5 |
| 4.4 | `pages/product/my.vue` | **修改** — 空发布列表 → `<EmptyState icon="📦" title="暂无发布" description="去发布第一个商品吧" action-text="去发布" action-path="/pages/product/publish" />` | ~5 |

#### 4c. CSS 重复提取

| # | 文件 | 改动 | 行数 |
|:--:|------|------|:--:|
| 4.5 | `miniprogram/src/styles/common.scss` | **修改** — 新增 `.page-navbar` mixin（从 chat/list.vue 提取通用导航栏样式） | ~20 |
| 4.6 | 各页面 | **审计** — 标记可统一替换的导航栏实例（不改代码，仅记录） | 0 |

**common.scss 新增：**

```scss
// 通用页面导航栏（多处内联重复）
%page-navbar {
  padding: 24rpx $space-page;
  background: $color-surface;
  border-bottom: 1rpx solid $color-divider;
  
  &__title {
    font-size: $text-xl;
    font-weight: $weight-bold;
    color: $color-title;
  }
}
```

> ⚠️ **不改动现有页面**：通过 `@extend %page-navbar` 减少重复需逐个页面替换 → 改动量大。本轮仅审计 + 记录，让后续页面新建时使用。避免为"优化"引入回归风险。

### Phase 5：R3 — 前端测试补齐 (~1.5 h) 🟡 P1

| # | 文件 | 改动 | 行数 |
|:--:|------|------|:--:|
| 5.1 | `miniprogram/__tests__/utils/im.test.js` | **新建** — peer profile 缓存函数测试（read/write/get/cachePeerProfile） | ~60 |
| 5.2 | `miniprogram/__tests__/store/user.test.js` | **新建** — Pinia store getter 逻辑测试（isLoggedIn/creditScore/canPublish/canTrade/isAdmin/isCS） | ~50 |
| 5.3 | `miniprogram/__tests__/mocks/uni.js` | **新建** — `uni.getStorageSync` / `uni.setStorageSync` / `uni.removeStorageSync` mock | ~20 |
| 5.4 | `miniprogram/vitest.config.js` | **新建** — vitest 配置 | ~15 |

**测试用例设计：**

```js
// __tests__/utils/im.test.js
describe('peer profile 缓存', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // mock uni.getStorageSync 返回空
    mockGetStorageSync.mockReturnValue(null);
  });

  it('cachePeerProfile() 写入 storage', () => {
    cachePeerProfile(123, '小明', 'https://cos/avatar.jpg');
    expect(mockSetStorageSync).toHaveBeenCalledWith(
      'im_peer_profiles',
      JSON.stringify({ '123': { nick: '小明', avatar: 'https://cos/avatar.jpg' } })
    );
  });

  it('getPeerProfile() 读取已缓存数据', () => {
    mockGetStorageSync.mockReturnValue(
      JSON.stringify({ '123': { nick: '小明', avatar: '' } })
    );
    expect(getPeerProfile(123)).toEqual({ nick: '小明', avatar: '' });
  });

  it('getPeerProfile(null) 返回 null', () => {
    expect(getPeerProfile(null)).toBeNull();
  });

  it('cachePeerProfile 不覆盖已有数据', () => {
    mockGetStorageSync.mockReturnValue(
      JSON.stringify({ '123': { nick: '原始昵称' } })
    );
    cachePeerProfile(123, '新昵称');
    // 不应调用 setStorageSync（已有数据不覆盖）
    expect(mockSetStorageSync).not.toHaveBeenCalled();
  });

  it('storage 数据损坏时返回空对象', () => {
    mockGetStorageSync.mockReturnValue('invalid-json{{{');
    // readPeerProfileCache 内部 catch → 返回 {}
    expect(readPeerProfileCache()).toEqual({});
  });
});

// __tests__/store/user.test.js
describe('userStore getters', () => {
  it('isLoggedIn: user + accessToken 同时存在为 true', () => { ... });
  it('isLoggedIn: 仅 user 无 token 为 false', () => { ... });
  it('canPublish: credit_score < 60 返回 false', () => { ... });
  it('canTrade: credit_score < 30 返回 false', () => { ... });
  it('isAdmin: role=admin 为 true, role=cs 为 false', () => { ... });
  it('isCS: role=cs 为 true, role=user 为 false', () => { ... });
});
```

---

## 四、修改文件清单

### 新建文件（5 个）

| # | 文件 | 类型 | 行数 | 说明 |
|:--:|------|:--:|:--:|------|
| 1 | `miniprogram/src/pages/agreement/index.vue` | 页面 | ~150 | 协议展示页 |
| 2 | `miniprogram/src/utils/agreement.js` | 工具 | ~100 | 协议文本常量（从 settings.vue 提取） |
| 3 | `miniprogram/__tests__/utils/im.test.js` | 测试 | ~60 | peer profile 缓存测试 |
| 4 | `miniprogram/__tests__/store/user.test.js` | 测试 | ~50 | Pinia store getter 测试 |
| 5 | `miniprogram/__tests__/mocks/uni.js` | 测试 mock | ~20 | uni API mock |

### 修改文件（14 个）

| # | 文件 | 层 | 改动量 | 说明 |
|:--:|------|:--:|:--:|------|
| **Phase 1 — 协议** | | | | |
| 1 | `miniprogram/src/pages/auth/login.vue` | 前端 | ~5 | openPrivacy() 导航到协议页 |
| 2 | `miniprogram/src/pages/user/settings.vue` | 前端 | ~10 | showAgreement() 导航到协议页 |
| 3 | `miniprogram/src/pages.json` | 前端 | ~6 | 注册 agreement 路由 |
| **Phase 2 — 编辑商品** | | | | |
| 4 | `miniprogram/src/pages/product/publish.vue` | 前端 | ~25 | 创建/编辑双模式 |
| 5 | `miniprogram/src/components/ImageUploader.vue` | 前端 | ~30 | initialFiles prop |
| 6 | `miniprogram/src/pages/product/detail.vue` | 前端 | ~10 | "编辑"按钮（卖家视角） |
| 7 | `miniprogram/src/pages/product/my.vue` | 前端 | ~10 | "编辑"入口 |
| **Phase 3 — 游标分页** | | | | |
| 8 | `server/src/repository/order.js` | 后端 | ~35 | listByCursor 方法 |
| 9 | `server/src/services/order.js` | 后端 | ~10 | cursor 路由 |
| 10 | `server/src/repository/review.js` | 后端 | ~35 | listByCursor 方法 |
| 11 | `server/src/services/review.js` | 后端 | ~10 | cursor 路由 |
| 12 | `miniprogram/src/pages/order/list.vue` | 前端 | ~15 | 切换游标分页 |
| 13 | `miniprogram/src/pages/user/reviews.vue` | 前端 | ~15 | 切换游标分页 |
| **Phase 4 — P2 收尾** | | | | |
| 14 | `miniprogram/src/pages/search/index.vue` | 前端 | ~5 | EmptyState 替换 |
| 15 | `miniprogram/src/pages/notification/index.vue` | 前端 | ~5 | EmptyState 替换 |
| 16 | `miniprogram/src/pages/product/my.vue` | 前端 | ~5 | EmptyState 替换 |
| 17 | `miniprogram/src/styles/common.scss` | 前端 | ~20 | %page-navbar mixin |

**总计：5 新建 + 17 修改 ≈ 480 行净增。无新增后端模块（仅 repo + service 增量）。**

---

## 五、验证清单

### 后端

| # | 验证项 | 方法 | 预期结果 |
|:--:|------|------|------|
| 1 | 143 现有测试全量通过 | `npx vitest run` | 143 passed, 0 failures |
| 2 | 订单游标分页无重复/遗漏 | curl 连续 5 次 cursor 翻页 | id 无重叠无间隔 |
| 3 | 评价游标分页无重复/遗漏 | 同上 | 同上 |
| 4 | 游标分页 hasMore 正确 | 最后一页 | `hasMore: false` |
| 5 | 新增 cursor 测试通过 | `npx vitest run` | 新增测试通过 |

### 前端

| # | 验证项 | 方法 | 预期结果 |
|:--:|------|------|------|
| 1 | 登录页点击《用户协议》→ 跳转协议页 | 真机/开发工具 | 协议页显示完整文本，可滚动 |
| 2 | 登录页点击《隐私政策》→ 跳转协议页 | 同上 | 隐私政策页显示完整文本 |
| 3 | 设置页协议入口 → 跳转协议页 | 同上 | 不再弹 Modal，改为页面导航 |
| 4 | 卖家详情页显示"编辑"按钮 | active/off_shelf 状态商品 | 按钮可见 |
| 5 | 点击编辑 → 跳转 publish 页带预填数据 | 编辑模式 | 已有图片+文字预填 |
| 6 | 修改信息后保存 → 返回详情页 | 完整编辑流程 | 新数据已生效 |
| 7 | sold/frozen/deleted 商品无编辑按钮 | 状态检查 | 按钮隐藏 |
| 8 | 订单列表触底加载连续 | 多页订单 → 触底 | 数据连续无重复 |
| 9 | 评价列表触底加载连续 | 多页评价 → 触底 | 同上 |
| 10 | 空列表显示 EmptyState 组件 | 无数据场景 | EmptyState 渲染 |
| 11 | 前端测试通过 | `cd miniprogram && npx vitest run` | ~10 用例通过 |
| 12 | ESLint 0 错误 | `npx eslint --ext .js,.vue src/` | 0 errors |
| 13 | Build 成功 | `npm run build:mp-weixin` | DONE |

---

## 六、预估工时

| Phase | 内容 | 文件数 | 预估 |
|:--:|------|:--:|:--:|
| Phase 1 | 协议内容页（新建 agreement 页 + 提取文本 + login/settings 改造） | 5 | 40 min |
| Phase 2 | 编辑商品页（publish 双模式 + ImageUploader 适配 + 入口按钮） | 5 | 1.5 h |
| Phase 3 | 游标分页扩展（order+review 后端各 2 文件 + 前端各 2 页面） | 8 | 2 h |
| Phase 4 | P2 收尾（搜索审计 + EmptyState 替换 + CSS 审计） | 5 | 1 h |
| Phase 5 | 前端测试补齐（新建 3 测试文件 + vitest 配置） | 4 | 1.5 h |
| 文档 | 更新 project-state + MEMORY + 编码迭代计划 | 3 | 30 min |
| **合计** | | **~30** | **~7 h ≈ 1 天** |

---

## 七、依赖与前置

| 依赖 | 状态 | 说明 |
|------|:--:|------|
| 第 1~11 轮已完成 | ✅ | 143 测试全绿，前端 24/25 页面完成 |
| `PUT /api/products/:id` | ✅ | 编辑商品 API 已就绪（含校验链） |
| `GET /api/orders` (cursor 支持) | ❌ | 需新增后端 cursor 查询 |
| `GET /api/reviews` (cursor 支持) | ❌ | 需新增后端 cursor 查询 |
| `components/ImageUploader.vue` | ⚠️ | 需新增 `initialFiles` prop |
| `utils/im.js` | ✅ | 有纯函数可测试（无 uni 渲染 API 依赖） |
| `store/user.js` | ✅ | getter 可独立测试 |
| 协议文本内容 | ✅ | settings.vue 中已有完整文本（~1500 字） |
| 无硬阻塞项 | ✅ | 可立即开始 |

---

## 八、风险与注意事项

1. **ImageUploader 改造风险** — 组件当前假设 `files` 从空开始累积。新增 `initialFiles` 需区分"已有 COS URL"和"新选择的本地文件"。若改动超过 50 行，该编辑模式首次仅支持修改文字信息，图片编辑延后。

2. **游标分页 + 状态筛选的组合** — 订单列表有 5 个状态 Tab（全部/待面交/已完成/已取消/待评价）。cursor 翻页时若切换 Tab 需重置 cursor。前端已通过 `reset=true` 处理（同首页模式）。

3. **协议页使用 AppNavbar** — 这是第一个在非聊天页面使用 AppNavbar 的场景。需确保 AppNavbar 在协议页场景下正确适配（无渐变背景，使用纯色）。

4. **前端测试 mock uni API** — `uni.getStorageSync` / `uni.setStorageSync` 等需全局 mock。vitest 的 `vi.mock()` + `__mocks__/` 模式可覆盖。

5. **不要引入回归 Bug** — 所有后端改动在 repo/service 层，不影响现有 API 契约。Publish 双模式改造需确保创建路径不受影响（新建分支逻辑需充分测试）。

6. **CSS 提取仅审计不执行** — Phase 4c 只审计并记录可提取的 CSS 模式，不实际改动页面（避免大面积重构引入回归）。

7. **搜索页不切换游标分页** — 搜索关键词 + 游标的组合逻辑复杂（关键词变化时需同时重置 cursor），且搜索的 `productRepo.list()` 已支持游标路由（service 层自动检测 cursor）。前端切换延后至有明确需求时。

---

## 九、与后续轮次的衔接

| 后续轮次 | 本轮交付物对其价值 |
|------|------|
| 第 13 轮（测试+CI+部署） | 前端测试已就绪，CI 可运行前后端测试；协议合规已满足 |
| 运维上线 | 游标分页扩展到订单/评价列表，降低生产 DB 负载；协议合规可过审 |

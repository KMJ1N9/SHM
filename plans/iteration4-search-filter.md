# 第 4 轮编码计划：搜索 + 筛选

> **状态：** ✅ 已完成（2026-06-08）
> **预估工时：** 1.5 天
> **实际工时：** ~2h
> **目标：** 关键词搜索（MySQL FULLTEXT）+ 侧边栏筛选（分类/成色/价格区间）
> **依据文档：** PRD §3.2、API 文档 §2.3、测试计划 §3.12 + §4.5、编码迭代计划 §第 4 轮

---

## 现状分析

### 后端 — 基础设施 ~90% 完成

| 模块 | 文件 | 状态 |
|------|------|:--:|
| Repository | `server/src/repository/product.js:29-95` | ✅ 已支持 keyword/category/condition/priceMin/priceMax/sort/分页 |
| Service | `server/src/services/product.js:66-85` | ✅ 分页解析 + nestSeller + extractCoverImage |
| Controller | `server/src/controllers/product.js:7-18` | ✅ 透传 req.query → service |
| Route | `server/src/routes/product.js:21` | ✅ `GET /` → `productController.list` |
| FULLTEXT 索引 | `server/migrations/001_create_tables.js:214-216` | ✅ ngram parser 已定义 |
| JOIN users | `product.js:83` | ✅ `JOIN users u ON p.seller_id = u.id` 已存在 |

**⚠️ 发现缺口：** `productRepo.list()` 的关键词搜索只覆盖 `p.title` 和 `p.description`，API 文档要求同时匹配 `u.nickname`（卖家昵称）和 `p.category`（分类名）。

```js
// 当前 product.js:42-43
conditions.push('(MATCH(p.title, p.description) AGAINST(? IN BOOLEAN MODE) OR p.title LIKE ?)');
params.push(keyword, `%${keyword}%`);
// 缺少: OR u.nickname LIKE ? OR p.category LIKE ?
```

### 前端 — 两个 stub 文件待实现

| 文件 | 状态 |
|------|:--:|
| `pages/search/index.vue` | ❌ 空 stub |
| `components/FilterSidebar.vue` | ❌ 空 stub |
| `pages/index/index.vue` | ✅ 搜索栏 + 分类 tabs + 瀑布流已就绪 |
| `api/product.js` | ✅ `list()` 已支持所有搜索参数 |

---

## 分步计划（7 步，每步独立可验证）

### 步骤 1：修复后端关键词搜索（1 处改动，~5 行）

**文件：** `server/src/repository/product.js`

**改动点：** 第 42-43 行，扩展关键词搜索条件，加入卖家昵称和分类匹配。

```js
// 改前：
conditions.push('(MATCH(p.title, p.description) AGAINST(? IN BOOLEAN MODE) OR p.title LIKE ?)');
params.push(keyword, `%${keyword}%`);

// 改后：
conditions.push(
  '(MATCH(p.title, p.description) AGAINST(? IN BOOLEAN MODE) OR p.title LIKE ? OR u.nickname LIKE ? OR p.category LIKE ?)'
);
params.push(keyword, `%${keyword}%`, `%${keyword}%`, `%${keyword}%`);
```

**验证：** 搜索卖家昵称能返回该卖家的商品（后续测试覆盖）

---

### 步骤 2：编写搜索单元测试（1 新文件，~7 条用例）

**文件（新建）：** `server/__tests__/unit/services/search.test.js`

**测试结构：** 沿用 `product.test.js` 模式 — `setupTestDb` / `createTestUser` / `createTestProduct`

| ID | 用例 | 预期 |
|:--:|------|------|
| SH-001 | FULLTEXT MATCH 基本搜索 | ✅ 返回标题匹配的商品 |
| SH-002 | 无匹配结果返回空数组 | ✅ `[]`，不抛异常 |
| SH-003 | SQL 特殊字符（`'`、`"`、`\`）不触发错误 | ✅ 正常返回（参数化查询天然防注入） |
| SH-004 | LIKE fallback（短词不走 FULLTEXT） | ✅ `LIKE '%keyword%'` 兜底 |
| SH-005 | 空关键词 → 返回全部（不过滤） | ✅ 等价于无 keyword 参数的普通列表 |
| — | 按卖家昵称搜索 | ✅ 返回该卖家商品 |
| — | 按分类名搜索 | ✅ 返回该分类商品 |

**验证：** `npx vitest run server/__tests__/unit/services/search.test.js` 全部通过

---

### 步骤 3：编写搜索集成测试（1 新文件，~5 条用例）

**文件（新建）：** `server/__tests__/integration/search.test.js`

**测试结构：** 沿用 `products.test.js` 模式 — supertest + 真实路由 + 测试 DB

| ID | 用例 | 预期 |
|:--:|------|------|
| SE-001 | 按商品名搜索返回匹配结果 | `200`，结果数组含匹配商品 |
| SE-002 | 按分类筛选 + 价格区间组合 | `200`，结果同时满足两个条件 |
| SE-003 | 无匹配结果返回空数组 | `200`，`{ list: [], total: 0 }` |
| — | 按卖家昵称搜索 | `200`，返回该卖家商品 |
| — | 排序 priceAsc / priceDesc 正确 | `200`，价格升序/降序 |

**验证：** `npx vitest run server/__tests__/integration/search.test.js` 全部通过

---

### 步骤 4：构建 FilterSidebar 组件（~250 行）

**文件：** `miniprogram/src/components/FilterSidebar.vue`

**功能规格：**
- 从右侧滑入的抽屉面板（遮罩层 + 面板区）
- 三组筛选项：
  - **分类：** 全部 / 电子产品 / 书籍教材 / 生活用品 / 服饰鞋包 / 运动户外 / 其他
  - **成色：** 全部 / 全新 / 95新 / 9成新 / 8成新 / 7成新及以下
  - **价格区间：** 最低价输入框 — 最高价输入框
- 底部双按钮：[重置] [确定]
- `visible` prop 控制显隐，支持 `v-if` + `transition` 动画
- `@apply` 事件返回 `{ category, condition, priceMin, priceMax }`
- `@reset` 事件清空所有筛选

**设计令牌：** 使用 `tokens.scss`（`$color-surface`, `$radius-modal`, `$shadow-modal`, `$space-page`, `$text-*`）

**验证：** 搜索页中打开侧边栏 → 选择条件 → 确定 → 筛选生效

---

### 步骤 5：构建搜索页面（~350 行）

**文件：** `miniprogram/src/pages/search/index.vue`

**功能规格：**

1. **搜索栏**
   - 输入框 autofocus，placeholder "搜索商品/卖家..."
   - `@confirm` 触发搜索，`@input` 300ms 防抖（可选，先做 @confirm）
   - 右侧"取消"按钮返回上一页

2. **搜索历史**（本地 Storage，key: `search_history`）
   - 每次搜索保存到历史（最多 20 条，去重）
   - 标签式展示，点击直接搜索
   - "清除历史"按钮

3. **热门搜索**（硬编码）
   - 教材、电子产品、生活用品、运动户外、服饰鞋包

4. **搜索结果**
   - 排序 tabs：最新 / 价格升序 / 价格降序
   - 双列瀑布流（复用 `index.vue` 的 leftList/rightList 逻辑 + ProductCard）
   - 右上角筛选按钮 → 打开 FilterSidebar
   - 筛选生效后显示当前筛选条件标签（可点击 × 取消）
   - 加载更多（onReachBottom）/ 下拉刷新（onPullDownRefresh）
   - 空状态："未找到相关商品"
   - 加载中状态

5. **数据流**
   - 调用 `listProducts({ keyword, category, condition, priceMin, priceMax, sort, page, pageSize })`
   - 搜索/筛选条件变化 → `loadProducts(true)` 重置
   - 翻页 → `loadProducts(false)` 追加

**复用模式：** `pages/index/index.vue` 的 `loadProducts` / `onReachBottom` / `onPullDownRefresh` / `leftList/rightList`

**验证：** 搜索"教材"→ 返回标题/分类/卖家昵称含"教材"的商品 → 点击结果跳转详情

---

### 步骤 6：首页添加筛选入口（~30 行改动）

**文件：** `miniprogram/src/pages/index/index.vue`

**改动点：**
- 搜索栏右侧添加筛选按钮图标（🔧 或文字"筛选"）
- 引入 FilterSidebar 组件
- 筛选条件变更 → 重新 `loadProducts(true)`
- 传递 `activeFilters` 状态给 FilterSidebar

**验证：** 首页点筛选 → 选择分类"电子产品"+ 成色"全新" → 首页只显示符合条件的商品

---

### 步骤 7：全量验证 + 状态更新

- [ ] `npx vitest run` 全部通过（含新增的搜索测试）
- [ ] `npm run lint` 前端 ESLint 通过
- [ ] 更新 `memory/project-state.md` — 记录第 4 轮完成状态
- [ ] 如有 bug 记录到 `memory/known-bugs.md`

---

## 文件改动清单

| # | 文件 | 操作 | 预计行数 |
|:--:|------|:--:|:--:|
| 1 | `server/src/repository/product.js` | 修改 2 行 | +2 |
| 2 | `server/__tests__/unit/services/search.test.js` | 新建 | ~120 |
| 3 | `server/__tests__/integration/search.test.js` | 新建 | ~150 |
| 4 | `miniprogram/src/components/FilterSidebar.vue` | 重写 | ~250 |
| 5 | `miniprogram/src/pages/search/index.vue` | 重写 | ~350 |
| 6 | `miniprogram/src/pages/index/index.vue` | 修改 | +30 |

**总计：** 3 新建 + 2 修改 + 1 重写 = 约 900 行

---

## 依赖与风险

- **无阻塞依赖：** 后端搜索基础设施已就绪，前端 API 封装已完整
- **低风险点：**
  - FULLTEXT ngram 分词对中文短词效果有限 → LIKE 兜底已实现
  - 搜索历史 Storage 容量 → 限制 20 条
  - FilterSidebar 价格区间需要做输入校验（非数字、负数、min > max）

---

## 完成总结（2026-06-08）

### 实际改动量

| # | 文件 | 操作 | 实际行数 |
|:--:|------|:--:|:--:|
| 1 | `server/src/repository/product.js` | 修改 3 行 | +3 |
| 2 | `server/__tests__/unit/services/search.test.js` | 新建 | 283 |
| 3 | `server/__tests__/integration/search.test.js` | 新建 | 215 |
| 4 | `miniprogram/src/components/FilterSidebar.vue` | 重写 | 450 |
| 5 | `miniprogram/src/pages/search/index.vue` | 重写 | 480 |
| 6 | `miniprogram/src/pages/index/index.vue` | 修改 | +50 |

**总计：** 3 新建 + 2 修改 + 1 重写 ≈ 1480 行

### 验证结果

| 检查项 | 结果 |
|--------|:--:|
| `npx vitest run` (6 文件, 98 条) | ✅ 全过 |
| 新增搜索单元测试 (14 条) | ✅ 全过 |
| 新增搜索集成测试 (9 条) | ✅ 全过 |
| ESLint `src/` (无新增错误) | ✅ |
| pages.json 搜索页路由 | ✅ 已注册 |

### 额外修复

- **COUNT 查询缺少 JOIN**：步骤 1 改动后，WHERE 子句可引用 `u.nickname`，但 COUNT 查询只用 `FROM products p`。已将 COUNT 查询也改为 `JOIN users u`。

### 已知限制

- `sort=latest` 依赖 `created_at`，同秒插入排序不确定（测试通过 1.1s 延迟规避）
- FilterSidebar 滑动动画在部分 Android 机型可能不够流畅（CSS animation）
- 搜索历史仅本地 Storage，跨设备不同步

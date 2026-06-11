# 第 11 轮编码计划：缓存 + 性能

> **状态：** ✅ 已完成
> **完成日期：** 2026-06-11
> **测试：** 143 tests passed（131 → 139 编码完成，+4 审查修复 = 143）
> **审查：** P0×0 / P1×0（1 已修复）/ P2×0（3 已修复），综合评分 9.5/10
> **预估工时：** ~6 h ≈ 1 天
> **目标：** LRU 缓存接入高频查询 + 游标分页（替代部分偏移分页） + 慢查询日志验证 + 图片懒加载审计
> **依据文档：** 编码迭代计划 §第 11 轮、技术架构文档 §九（工程化基础设施）、API 接口文档 §2.1（商品列表）、rules/performance-rules

---

## 〇、本轮特殊性

编码迭代计划原文（§第 11 轮 L1550~L1599）：

> **目标：LRU 缓存高频查询 + 慢查询监控验证 + 游标分页**

本轮的基础设施（LRU 缓存 / 慢查询监控 / 性能采集）在第 1 轮骨架补全中已全部搭建。本轮实际工作为 **3 项接入 + 1 项新建 + 3 项验证**，不涉及新模块创建。

---

## 一、现状分析

### 1.1 已就绪的基础设施 ✅

| 文件 | 状态 | 行数 | 完成于 | 关键能力 |
|------|:--:|:--:|:--:|------|
| `utils/cache.js` | ✅ | 131 | 第 1 轮 | LRU 单例：`get`/`set`/`delete`/`deleteByPrefix`/`getOrSet`/`clear`，默认 maxSize=500，TTL=5min |
| `utils/perf.js` | ✅ | 81 | 第 1 轮 | `startTimer`/`requestTimer`/`memoryUsage`/`startMemoryMonitor` |
| `models/db.js` | ✅ | 129 | 第 1 轮 | `query()` 内置慢查询监控：>200ms warn / >1000ms error + SQL 语句记录 |
| `components/SafeImage.vue` | ✅ | 131 | 第 5 轮 | `lazyLoad` prop → `<image lazy-load>` |
| `components/ProductCard.vue` | ✅ | — | 第 3 轮 | 已设置 `:lazy-load="true"` → 图片懒加载已启用 |

### 1.2 四项审计发现

#### 审计 A：LRU 缓存接入率 — **零使用** 🔴

```
$ grep -r "cache\." server/src/services/ --include="*.js"
# 无结果 — 7 个 service 文件无一使用 LRU 缓存
```

`utils/cache.js` 提供的 `cache.getOrSet()` Cache-Aside 模式从未被调用。所有高频查询（首页列表、商品详情、用户公开信息）每次都走数据库。

#### 审计 B：分页模式 — 仅偏移分页 🟡

```
$ grep -r "LIMIT.*OFFSET" server/src/repository/ --include="*.js"
product.js:89   LIMIT ? OFFSET ?    (list)
product.js:242  LIMIT ? OFFSET ?    (listAll)
product.js:286  LIMIT ? OFFSET ?    (findBySeller)
user.js:170     LIMIT ? OFFSET ?    (listWithFilters)
```

全部 4 处分页查询均使用 `LIMIT ? OFFSET ?`。随数据量增长（>10K 行），OFFSET 翻页性能退化（O(n) 扫描跳过 offset 行）。游标分页（`WHERE id < ? ORDER BY id DESC LIMIT ?`）可做到 O(1) 定位。

#### 审计 C：慢查询日志 — 已就绪 ✅

`db.js:51-89` `query()` 函数已内置慢查询监控：
- `> 200ms`：warn 级别记录 SQL（前 500 字符）+ 参数 + 耗时
- `> 1000ms`：error 级别记录

测试输出验证功能正常（已观测到 `慢查询记录 (>200ms)` 日志）。本轮仅需验证 + 确认，无需改代码。

#### 审计 D：图片懒加载 — 已启用 ✅

`ProductCard.vue:9` 已传 `:lazy-load="true"` 给 `SafeImage`。小程序原生 `<image lazy-load>` 在上下文中自动懒加载。本轮仅需审计确认，无需改代码。

---

## 二、架构关键决策

### 决策 1：缓存接入策略 — Cache-Aside

```
读路径（Cache-Aside）：
  1. cache.get(key) → hit → 直接返回（< 1ms）
  2. cache.get(key) → miss → DB 查询 → cache.set(key, value, ttl) → 返回

写路径（失效，非更新）：
  1. 写 DB 成功
  2. 删除相关缓存（下次读取时自动重建）
```

**为什么选择失效而非更新？**
- 失效是原子操作（单次 `delete`），更新需要重复序列化逻辑
- 失效避免了"更新了 DB 但缓存写入失败"的不一致窗口
- 符合 Cache-Aside 标准模式

### 决策 2：缓存 Key 命名规范

| 数据 | Key 模式 | 示例 | TTL | 理由 |
|------|---------|------|:--:|------|
| 商品列表 | `products:list:${hash}` | `products:list:a3f8c2d` | 60s | 高并发首页，允许 1 分钟延迟 |
| 商品详情 | `product:${id}` | `product:123` | 120s | 数据变更频率低，2 分钟内始终一致 |
| 用户公开信息 | `user:public:${id}` | `user:public:456` | 300s | 用户信息极少变更，5 分钟合理 |

**Hash 算法：** 对 filter 对象 `JSON.stringify`（按 key 排序保证确定性），取 SHA256 前 8 位（或无 SHA256 依赖时直接用排序后的 JSON 字符串，控制长度 < 256 字符）。

> 实际采用方案：排序后的 `JSON.stringify(filters)` 作为 key suffix。因为 filter 对象字段可控（keyword/category/condition/priceMin/priceMax/sort/page/pageSize），JSON 序列化后长度通常在 50-150 字符，无需额外哈希。

### 决策 3：缓存失效范围

| 操作 | 失效范围 | 实现 |
|------|---------|------|
| `product.create()` | 全部商品列表缓存 | `cache.deleteByPrefix('products:list:')` |
| `product.update()` | 该商品详情 + 全部商品列表 | `cache.delete('product:${id}')` + `cache.deleteByPrefix('products:list:')` |
| `product.delete()` | 该商品详情 + 全部商品列表 | 同上 |
| `userService.updateProfile()` | 该用户公开信息 | `cache.delete('user:public:${id}')` |
| `creditService.changeScore()` | 该用户公开信息 | `cache.delete('user:public:${id}')` |

> **为什么清除全部列表缓存而不是精准失效？** filter 组合有 N 种（category × condition × priceRange × sort × keyword），精准计算哪些 key 受影响复杂度高且易漏。商品总数 < 1000 的小程序场景下，列表缓存重建成本低（单次 COUNT + JOIN），全部清除是最简单的正确解。

### 决策 4：游标分页 vs 偏移分页 — 共存

```
偏移分页（保留）：
  GET /api/products?page=2&pageSize=20
  → 用于搜索结果页（需跳页）+ 管理后台

游标分页（新增）：
  GET /api/products?cursor=123&limit=20
  → 用于首页瀑布流"加载更多"（无限滚动）
```

**游标基于 `id DESC`（主键有序），WHERE 条件：**
```sql
-- 第 1 页（无 cursor）
SELECT ... WHERE ... ORDER BY p.id DESC LIMIT 20

-- 第 N 页（cursor = 上一页最后一条的 id）
SELECT ... WHERE ... AND p.id < ? ORDER BY p.id DESC LIMIT 20
```

**响应格式区分：**
```js
// 偏移分页（现有）
{ list, total, page, pageSize }

// 游标分页（新增）
{ list, total, cursor: lastItem.id, hasMore: list.length >= limit }
```

**Service 层自动选择：** `productService.list(filters)` 检测 `filters.cursor` → 有 cursor 走 `listByCursor`，无 cursor 走 `list`（偏移）。

### 决策 5：游标分页前端适配策略

| 页面 | 当前分页方式 | 本轮改动 |
|------|:--:|------|
| `pages/index/index.vue` (首页) | 偏移分页 `page: N` | **切换为游标分页** — `cursor` 替代 `page`，`hasMore` 替代 `noMore` |
| `pages/search/index.vue` (搜索) | 偏移分页 | **保留偏移分页** — 搜索结果需要"跳页"能力（暂无 UI，预留） |
| `pages/product/my.vue` (我的发布) | 偏移分页 | **保留偏移分页** — 个人数据量小，无性能问题 |
| `pages/admin/products.vue` (管理) | 偏移分页 | **保留偏移分页** — 管理后台需要跳页 + 数据量小 |

> **仅首页切换到游标分页。** 搜索页和我的发布页保持偏移分页。改动最小化原则。

---

## 三、任务清单

### Phase 1：LRU 缓存接入 productService（~45 min）

| # | 文件 | 改动 | 行数 |
|:--:|------|------|:--:|
| 1.1 | `services/product.js` | 引入 `cache`，`list()` 用 `getOrSet` 包裹，TTL 60s，key = `products:list:${JSON.stringify(sortedFilters)}` | ~15 |
| 1.2 | `services/product.js` | `detail()` 用 `getOrSet` 包裹，TTL 120s，key = `product:${id}` | ~8 |
| 1.3 | `services/product.js` | `create()` 成功后 → `cache.deleteByPrefix('products:list:')` | ~2 |
| 1.4 | `services/product.js` | `update()` 成功后 → `cache.delete('product:${id}')` + `cache.deleteByPrefix('products:list:')` | ~3 |
| 1.5 | `services/product.js` | `delete()` 成功后 → `cache.delete('product:${id}')` + `cache.deleteByPrefix('products:list:')` | ~3 |

**关键实现细节：**

```js
// 1.1 列表缓存 — 构建确定性 key
const { cache } = require('../utils/cache');

async list(filters) {
  const page = Math.max(1, parseInt(filters.page, 10) || 1);
  const pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, parseInt(filters.pageSize, 10) || 20));

  // 排序 key 确保 JSON 序列化确定性
  const keyParts = { ...filters, page, pageSize };
  const sortedKeys = Object.keys(keyParts).sort();
  const sortedObj = {};
  for (const k of sortedKeys) sortedObj[k] = keyParts[k];
  const cacheKey = `products:list:${JSON.stringify(sortedObj)}`;

  const result = await cache.getOrSet(cacheKey, async () => {
    const data = await productRepo.list({ ...filters, page, pageSize });
    data.list = data.list.map(row => ({
      ...nestSeller(row),
      cover_image: extractCoverImage(row.images),
    }));
    return data;
  }, 60 * 1000); // 60s TTL

  return result;
}

// 1.2 详情缓存
async detail(id, viewer) {
  const cacheKey = `product:${id}`;

  const product = await cache.getOrSet(cacheKey, async () => {
    const data = await productRepo.findById(id);
    if (!data) return null; // null 也会被缓存？→ 否，见下文
    return data;
  }, 120 * 1000); // 120s TTL

  if (!product) throw notFound('商品');
  // ... off_shelf 权限判断保持不变（在缓存之外，因为 viewer 不同）
  return nestSeller(product);
}
```

> ⚠️ **detail 缓存与权限的冲突**：`detail()` 的 `viewer` 参数用于 off_shelf 商品权限判断。缓存不应包含权限逻辑——否则 admin 访问 off_shelf 商品后，普通用户也可能命中缓存看到已下架商品。
>
> **解决：缓存存放 raw DB 数据，权限判断在缓存之后执行。** 且 `product:${id}` 只缓存 `status='active'` 的商品（绝大多数请求），off_shelf 商品不缓存（低频访问，不影响性能）。

> ⚠️ **`getOrSet` 会缓存 null 吗？** 会——`cache.get()` 检查 `entry.expiresAt` 不过滤 null 值。对于不存在的商品 ID（攻击者遍历 ID），会缓存 null → 浪费缓存空间且正常访问无法恢复。
>
> **解决：detail 不使用 `getOrSet` 直接包裹——缓存命中时返回数据，miss 时查 DB 后判断非 null 才 set 缓存。**

**修正后的 detail 缓存实现：**

```js
async detail(id, viewer) {
  const cacheKey = `product:${id}`;
  
  let product = cache.get(cacheKey);
  if (product) {
    // 缓存命中 — 仍需 off_shelf 权限检查
    if (product.status === 'off_shelf') {
      if (!viewer || (viewer.id !== product.seller_id && viewer.role !== 'admin')) {
        throw notFound('商品');
      }
    }
    return nestSeller(product);
  }

  // 缓存 miss
  product = await productRepo.findById(id);
  if (!product) throw notFound('商品');

  // 仅缓存 active 商品（高频访问的绝大多数场景）
  if (product.status === 'active') {
    cache.set(cacheKey, product, 120 * 1000);
  }

  // 权限判断
  if (product.status === 'off_shelf') {
    if (!viewer || (viewer.id !== product.seller_id && viewer.role !== 'admin')) {
      throw notFound('商品');
    }
  }

  return nestSeller(product);
}
```

### Phase 2：LRU 缓存接入 userService + creditService 失效（~15 min）

| # | 文件 | 改动 | 行数 |
|:--:|------|------|:--:|
| 2.1 | `services/user.js` | 引入 `cache`，`getById()` 使用 cache.getOrSet，TTL 300s，key = `user:public:${id}` | ~10 |
| 2.2 | `services/user.js` | `updateProfile()` 成功后 → `cache.delete('user:public:${id}')` | ~2 |
| 2.3 | `services/credit.js` | `changeScore()` 成功后 → `cache.delete('user:public:${userId}')` | ~2 |

### Phase 3：游标分页 — 后端（~45 min）

| # | 文件 | 改动 | 行数 |
|:--:|------|------|:--:|
| 3.1 | `repository/product.js` | 新增 `listByCursor(filters)` 方法 — cursor-based 查询 | ~40 |
| 3.2 | `services/product.js` | `list()` 检测 `filters.cursor` → 自动路由到 `listByCursor` | ~15 |
| 3.3 | `controllers/product.js` | 无需改动 — controller 只透传 `req.query` 到 service，cursor 自动包含 | 0 |
| 3.4 | `routes/product.js` | 无需改动 — 路由不变 | 0 |

**关键实现细节：**

```js
// repository/product.js — 新增方法
/**
 * 游标分页商品列表（无限滚动场景）
 *
 * 使用 WHERE id < cursor ORDER BY id DESC 替代 LIMIT OFFSET，
 * 避免大数据量下 OFFSET 的性能退化（O(n) 扫描跳过行）。
 *
 * @param {Object} filters - { cursor?, limit?, keyword?, category?, condition?, priceMin?, priceMax?, sort? }
 * @returns {Promise<{list: Array, total: number, cursor: number|null, hasMore: boolean}>}
 */
async listByCursor(filters) {
  const {
    keyword, category, condition, priceMin, priceMax, sort = 'latest',
  } = filters;
  const limit = Math.min(50, Math.max(1, parseInt(filters.limit, 10) || 20));
  const cursor = filters.cursor ? parseInt(filters.cursor, 10) : null;

  const conditions = ['p.status = ?'];
  const params = ['active'];

  // 游标条件（id < cursor，因 ORDER BY id DESC）
  if (cursor) {
    conditions.push('p.id < ?');
    params.push(cursor);
  }

  // 关键词搜索（同 list）
  if (keyword) {
    conditions.push(
      '(MATCH(p.title, p.description) AGAINST(? IN BOOLEAN MODE) OR p.title LIKE ? OR u.nickname LIKE ? OR p.category LIKE ?)'
    );
    params.push(keyword, `%${keyword}%`, `%${keyword}%`, `%${keyword}%`);
  }
  if (category) { conditions.push('p.category = ?'); params.push(category); }
  if (condition) { conditions.push('p.`condition` = ?'); params.push(condition); }
  if (priceMin !== undefined) { conditions.push('p.price >= ?'); params.push(priceMin); }
  if (priceMax !== undefined) { conditions.push('p.price <= ?'); params.push(priceMax); }

  const where = `WHERE ${conditions.join(' AND ')}`;

  let orderBy;
  switch (sort) {
    case 'priceAsc':  orderBy = 'ORDER BY p.price ASC, p.id DESC'; break;
    case 'priceDesc': orderBy = 'ORDER BY p.price DESC, p.id DESC'; break;
    default:          orderBy = 'ORDER BY p.id DESC'; break;
  }

  // 总数（与 list 共用逻辑，注意 cursor 不参与 COUNT 条件）
  const countConditions = [...conditions];
  const countParams = [...params];
  if (cursor) {
    // 移除 cursor 条件（总数应统计全部符合条件的，而非仅 cursor 之后的）
    const cursorIdx = countConditions.findIndex(c => c.includes('p.id <'));
    if (cursorIdx >= 0) {
      countConditions.splice(cursorIdx, 1);
      // params 中 cursor 值的位置在 keyword 之前（条件插入顺序），需要对齐删除
      // 简化处理：重新构建不含 cursor 的 count params
    }
  }
  // 实际上，有 keyword 等筛选时 cursor 位置复杂，直接用两个独立查询
  const [countResult] = await query(
    `SELECT COUNT(*) AS total FROM products p JOIN users u ON p.seller_id = u.id ${where.replace(/p\.id < \? AND /, '')}`,
    cursor ? params.filter((_, i) => i !== params.findIndex((v, idx) => conditions[idx]?.includes('p.id <'))) : params
  );

  // 简化：count 查单独的 SQL（cursor 不参与 COUNT）
  const countWhere = where.replace(/p\.id < \? AND /, '');
  const countParamsArr = [...params];
  // 移除 cursor 参数（在 params 数组的 index 1 位置：['active', cursorVal, ...]）
  if (cursor) countParamsArr.splice(1, 1);
  
  const [countRow] = await query(
    `SELECT COUNT(*) AS total FROM products p JOIN users u ON p.seller_id = u.id ${countWhere}`,
    countParamsArr
  );

  const rows = await query(
    `SELECT ${LIST_FIELDS}
     FROM products p
     JOIN users u ON p.seller_id = u.id
     ${where} ${orderBy}
     LIMIT ?`,
    [...params, limit + 1] // 多取 1 条判断 hasMore
  );

  const hasMore = rows.length > limit;
  if (hasMore) rows.pop();

  return {
    list: rows,
    total: countRow.total,
    cursor: rows.length > 0 ? rows[rows.length - 1].id : null,
    hasMore,
  };
}
```

> ⚠️ 上面 COUNT 的 cursor 参数移除逻辑不够优雅。采用更清晰的实现：**游标条件用单独变量管理，COUNT 查询不追加 cursor 条件。**

```js
// 更清晰的实现：
async listByCursor(filters) {
  const { keyword, category, condition, priceMin, priceMax, sort = 'latest' } = filters;
  const limit = Math.min(50, Math.max(1, parseInt(filters.limit, 10) || 20));
  const cursor = filters.cursor ? parseInt(filters.cursor, 10) : null;

  const whereConditions = ['p.status = ?'];
  const whereParams = ['active'];

  // 游标（仅 WHERE 用，COUNT 不参与）
  let cursorClause = '';
  const cursorParams = [];
  if (cursor) {
    cursorClause = 'AND p.id < ?';
    cursorParams.push(cursor);
  }

  // 关键词 + 筛选（WHERE 和 COUNT 共用）
  // ... (与 list 相同的关键词/分类/成色/价格逻辑)

  // 列表查询（含 cursor）
  const rows = await query(
    `SELECT ${LIST_FIELDS}
     FROM products p JOIN users u ON p.seller_id = u.id
     WHERE ${whereConditions.join(' AND ')} ${cursorClause}
     ORDER BY p.id DESC LIMIT ?`,
    [...whereParams, ...cursorParams, limit + 1]
  );

  const hasMore = rows.length > limit;
  if (hasMore) rows.pop();

  // COUNT 查询（不含 cursor）
  const [countRow] = await query(
    `SELECT COUNT(*) AS total FROM products p JOIN users u ON p.seller_id = u.id WHERE ${whereConditions.join(' AND ')}`,
    whereParams
  );

  return {
    list: rows,
    total: countRow.total,
    cursor: rows.length > 0 ? rows[rows.length - 1].id : null,
    hasMore,
  };
}
```

**service 层自动路由：**

```js
// services/product.js — list() 修改
async list(filters) {
  // 游标分页（无缓存 — 游标模式每次请求不同，缓存命中率低）
  if (filters.cursor !== undefined) {
    const result = await productRepo.listByCursor(filters);
    result.list = result.list.map(row => ({
      ...nestSeller(row),
      cover_image: extractCoverImage(row.images),
    }));
    return result;
  }

  // 偏移分页（有缓存 — page 模式）
  const page = Math.max(1, parseInt(filters.page, 10) || 1);
  const pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, parseInt(filters.pageSize, 10) || 20));

  const cacheKey = buildListCacheKey({ ...filters, page, pageSize });

  return cache.getOrSet(cacheKey, async () => {
    const data = await productRepo.list({ ...filters, page, pageSize });
    data.list = data.list.map(row => ({
      ...nestSeller(row),
      cover_image: extractCoverImage(row.images),
    }));
    return data;
  }, 60 * 1000);
}
```

> **注意：游标分页不缓存。** 游标值每次变化，缓存命中率极低（每个 cursor 值不同 key）。游标分页的性能优势来自 SQL 层 O(1) 定位，不需要缓存弥补。

### Phase 4：首页前端切换为游标分页（~30 min）

| # | 文件 | 改动 | 行数 |
|:--:|------|------|:--:|
| 4.1 | `pages/index/index.vue` | `loadProducts()` 改用 cursor 模式：响应取 `cursor`/`hasMore` 替代 `page`/`noMore` | ~15 |
| 4.2 | `api/product.js` | `getProductList()` 参数从 `{page, pageSize}` 扩展为支持 `{cursor, limit}` | ~5 |

**前端改动关键逻辑：**

```js
// 数据状态 — 修改前
const page = ref(1);
const noMore = ref(false);

// 数据状态 — 修改后
const cursor = ref(null);       // 当前游标（上一页最后一条 id）
const hasMore = ref(true);      // 是否有更多

async function loadProducts(reset = false) {
  if (loading.value) return;
  if (!reset && !hasMore.value) return;

  if (reset) {
    cursor.value = null;
    list.value = [];
    hasMore.value = true;
  }

  loading.value = true;
  try {
    const params = { limit: 20 };
    if (!reset && cursor.value) {
      params.cursor = cursor.value;
    }
    // 筛选条件...
    if (activeCategory.value) params.category = activeCategory.value;
    // ...

    const data = await getProductList(params);

    if (reset) {
      list.value = data.list || [];
      leftList.value = buildColumns(data.list || []);
    } else {
      list.value.push(...(data.list || []));
      leftList.value = buildColumns(list.value);
    }

    cursor.value = data.cursor;
    hasMore.value = data.hasMore;
  } catch (err) {
    // ...
  } finally {
    loading.value = false;
    uni.stopPullDownRefresh();
  }
}
```

> ⚠️ **瀑布流双列适配**：`buildColumns` 接收全量 list，每次追加后重新分配左右列。当前实现已正确处理。

### Phase 5：验证（~1 h）

#### 5.1 后端单元测试

| # | 测试项 | 方法 |
|:--:|------|------|
| 1 | LRU 缓存命中 | `vitest` — mock productRepo，验证第二次 `productService.list()` 不调用 repo |
| 2 | 缓存失效 — create | 调用 `create()` 后 → 验证 `cache.get(listKey)` 返回 null |
| 3 | 缓存失效 — update | 调用 `update()` 后 → 验证 `cache.get(productKey)` 返回 null |
| 4 | 游标分页无重复 | 连续 3 次 `listByCursor` → 验证所有 id 不重复 |
| 5 | 游标分页 hasMore 正确 | 最后一页 → `hasMore: false` |
| 6 | 用户缓存 | `userService.getById()` 第二次调用不查 DB |
| 7 | 用户缓存失效 | `updateProfile()` 后缓存被清除 |

#### 5.2 现有测试回归

```bash
npx vitest run  # 确保 131 tests 仍然全部通过
```

#### 5.3 前端验证

| # | 验证项 | 方法 |
|:--:|------|------|
| 1 | 首页触底加载 | 微信开发者工具 — 首页下滑 → 数据连续无重叠 |
| 2 | 首页下拉刷新 | 下拉后数据更新，游标重置 |
| 3 | Build 成功 | `npm run build:mp-weixin` |
| 4 | ESLint 0 错误 | `npx eslint --ext .js,.vue src/` |

---

## 四、修改文件清单

| # | 文件 | 操作 | 改动量 | 说明 |
|:--:|------|:--:|:--:|------|
| **缓存接入** | | | | |
| 1 | `server/src/services/product.js` | 修改 | ~30 | list + detail 缓存 + create/update/delete 失效 |
| 2 | `server/src/services/user.js` | 修改 | ~12 | getById 缓存 + updateProfile 失效 |
| 3 | `server/src/services/credit.js` | 修改 | ~3 | changeScore 失效 user:public 缓存 |
| **游标分页** | | | | |
| 4 | `server/src/repository/product.js` | 修改 | ~45 | 新增 listByCursor 方法 |
| 5 | `server/src/services/product.js` | 修改 | ~15 | list() 自动路由 cursor vs page |
| **前端** | | | | |
| 6 | `miniprogram/src/pages/index/index.vue` | 修改 | ~15 | 切换游标分页（cursor/hasMore） |
| 7 | `miniprogram/src/api/product.js` | 修改 | ~5 | getProductList 支持 cursor 参数 |
| **测试** | | | | |
| 8 | `server/__tests__/unit/services/product.test.js` | 修改 | ~40 | 缓存测试（命中 + 失效） |
| 9 | `server/__tests__/unit/services/user.test.js` | 新建 | ~30 | 用户缓存测试 |
| **文档** | | | | |
| 10 | `memory/project-state.md` | 修改 | ~20 | 更新第 11 轮状态 |
| 11 | `memory/MEMORY.md` | 修改 | ~1 | 添加计划链接 |

**总计：8 个修改 + 1 个新建 + 2 个文档 ≈ 216 行改动。无新增后端模块。**

---

## 五、验证清单

### 后端

| # | 验证项 | 方法 | 预期结果 |
|:--:|------|------|------|
| 1 | 131 现有测试全量通过 | `npx vitest run` | 131 passed，0 failures |
| 2 | 缓存命中测试通过 | `npx vitest run __tests__/unit/services/product.test.js` | 缓存相关测试通过 |
| 3 | 游标分页无重复/遗漏 | 手动 curl 连续 5 次 cursor 翻页 | id 无重叠无间隔 |
| 4 | 慢查询日志正常 | 查询加 SLEEP(1) → 检查 warn/error 日志 | 日志输出完整 |
| 5 | 缓存失效验证 | create 后立即 GET 列表 → 返回数据含新商品 | 新商品出现在列表中 |

### 前端

| # | 验证项 | 方法 | 预期结果 |
|:--:|------|------|------|
| 1 | 首页触底加载连续 | 首页 → 触底 → 触底 → 触底 | 数据无重复无缺口 |
| 2 | 首页下拉刷新 | 首页 → 下拉 → 数据更新 | 游标重置，最新数据显示 |
| 3 | 首页"已加载全部" | 连续触底至最后一页 | 显示"没有更多了" |
| 4 | 图片懒加载 | Performance 面板观察图片请求时机 | 仅可视区域内的图片被请求 |
| 5 | Build 成功 | `npm run build:mp-weixin` | DONE |
| 6 | ESLint 0 错误 | `npx eslint --ext .js,.vue src/` | 0 errors |

---

## 六、预估工时

| Phase | 内容 | 预估 |
|:--:|------|:--:|
| Phase 1 | LRU 缓存接入 productService（5 处改动） | 45 min |
| Phase 2 | LRU 缓存接入 userService + creditService（3 处改动） | 15 min |
| Phase 3 | 游标分页后端（repo + service） | 45 min |
| Phase 4 | 首页前端切换游标分页 | 30 min |
| Phase 5 | 验证（测试 + 回归 + 前端） | 1 h |
| 文档 | 更新 project-state + MEMORY | 10 min |
| **合计** | | **~3.5 h** |

> 编码迭代计划预估 1 天，实际因基础设施（LRU/慢查询/懒加载）已在第 1 轮全部就绪，剩余接入+验证工作约 3.5 小时。

---

## 七、依赖与前置

| 依赖 | 状态 | 说明 |
|------|:--:|------|
| 第 1~10 轮已完成 | ✅ | 核心闭环已打通 |
| LRU Cache (`utils/cache.js`) | ✅ | 第 1 轮已就绪，支持 get/set/delete/deleteByPrefix/getOrSet |
| 慢查询监控 (`models/db.js`) | ✅ | 第 1 轮已就绪，>200ms 自动记录 |
| 图片懒加载 (`SafeImage` + `ProductCard`) | ✅ | 第 5 轮已启用 `:lazy-load="true"` |
| 131 测试用例全绿 | ✅ | 10 文件全部通过 |
| 无硬阻塞项 | ✅ | 可立即开始 |

---

## 八、与后续轮次的衔接

| 后续轮次 | 本轮交付物对其价值 |
|------|------|
| 第 12 轮（前端收尾） | 游标分页模式可用于其他列表页（如订单列表、评价列表） |
| 第 13 轮（测试+CI+部署） | 缓存测试用例已就绪，CI 可运行 |
| 运维上线 | 慢查询日志为生产环境 DBA 优化提供依据；LRU 缓存降低数据库负载 |

---

## 九、风险与注意事项

1. **缓存穿透** — 恶意请求不存在的商品 ID 会导致每次都查 DB。通过只缓存 `status='active'` 的商品 + `findById` 返回 null 时不写缓存来避免。
2. **缓存雪崩** — 大量列表缓存在同一时刻过期（首次加载时集中写入）。由于 TTL 是相对时间（写入时刻 + TTL），不会同时过期。即使全部过期，DB 压力也在可控范围（小程序用户量级）。
3. **游标分页 + 筛选的组合** — 关键词搜索 + cursor 翻页时，如果搜索条件变化（用户修改了关键词），cursor 应重置为 null。前端已通过 `reset=true` 处理。
4. **缓存与 off_shelf 权限** — 缓存仅存放 active 商品数据。off_shelf 商品不缓存（低频访问），权限判断在缓存之外执行。
5. **不要引入回归 Bug** — 所有改动在 service/repository 层，不影响现有 API 契约。`npx vitest run` 必须全绿。
6. **非价格排序的游标分页** — 当 `sort=latest` 时 cursor 基于 `id DESC` 正常；当 `sort=priceAsc` 时 cursor 基于 `(price, id)` 组合键。MVP 阶段首页只使用 `latest` 排序，游标分页暂不处理价格排序场景（若需要，后续添加复合游标）。

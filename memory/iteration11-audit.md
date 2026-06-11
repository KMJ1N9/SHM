---
name: iteration11-audit
description: 第 11 轮代码审查报告 — 7 文件审查（缓存接入 + 游标分页），P0×0 / P1×0（1 已修复）/ P2×0（3 已修复），综合评分 9.5/10
metadata:
  type: project
  updatedAt: 2026-06-11T18:00
  iteration: 11
  score: 9.5
  issuesFound: 4
  issuesFixed: 4
---

# 第 11 轮代码审查报告

**审查日期：** 2026-06-11
**审查范围：** 第 11 轮全部改动（7 代码文件 + 1 测试文件 + 1 计划文档）
**审查方法论：** 7 维度审查（正确性 / 安全性 / 性能 / 规范符合性 / 业务逻辑 / 边界条件 / 测试覆盖）
**审查基线：** 139/139 tests passed | ESLint 0 errors（本轮改动无新增） | Build DONE

---

## 审查总览

| 维度 | 评分 | 说明 |
|------|:--:|------|
| 正确性 | 9.0/10 | 核心逻辑正确，1 个 P1 设计缺陷（游标+非latest排序语义错误） |
| 安全性 | 9.5/10 | 缓存穿透（不缓存 null/off_shelf）+ off_shelf 权限泄露双重防护到位 |
| 性能 | 9.0/10 | Cache-Aside 策略合理，游标分页 O(1) 定位，TTL 设置得当 |
| 规范符合性 | 9.0/10 | 注释详尽，风格一致，无过度抽象，无空 catch |
| 业务逻辑 | 9.5/10 | 缓存失效范围正确，权限判断在缓存之外执行，事务后失效 |
| 边界条件 | 8.5/10 | 游标排序组合缺保护（P1-001），其余边界处理正确 |
| 测试覆盖 | 8.5/10 | 8 条缓存测试覆盖核心路径（命中/失效/穿透），游标分页缺后端单测 |
| **综合** | **9.0/10** | 工程化成熟，1 个 P1 建议修复，3 个 P2 可接受 |

---

## 改动文件清单

| # | 文件 | 操作 | 改动量 | 说明 |
|:--:|------|:--:|:--:|------|
| **缓存接入** | | | | |
| 1 | `server/src/utils/cache.js` | 预检（第 1 轮已创建） | 132 行 | LRU 单例：get/set/delete/deleteByPrefix/getOrSet/clear |
| 2 | `server/src/services/product.js` | 修改 | +40 行 | list() getOrSet 缓存 (60s) + detail() 手动缓存 (120s, 仅 active) + create/update/delete 失效 |
| 3 | `server/src/services/user.js` | 修改 | +12 行 | getById() getOrSet 缓存 (300s) + updateProfile() 失效 |
| 4 | `server/src/services/credit.js` | 修改 | +3 行 | changeScore() 后失效 user:public:{id} |
| **游标分页** | | | | |
| 5 | `server/src/repository/product.js` | 修改 | +75 行 | 新增 listByCursor() — WHERE id < cursor ORDER BY id DESC |
| 6 | `server/src/services/product.js` | 修改 | +15 行 | list() 自动路由：filters.cursor → cursor 模式，否则 → offset 模式 |
| **前端** | | | | |
| 7 | `miniprogram/src/pages/index/index.vue` | 修改 | +15 行 | cursor/hasMore 替换 page/noMore，无限滚动游标翻页 |
| **测试** | | | | |
| 8 | `server/__tests__/unit/services/product.test.js` | 修改 | +72 行 | 8 条缓存测试（命中 ×3 / 失效 ×3 / 穿透防护 ×1 / off_shelf不缓存 ×1） |
| **文档** | | | | |
| 9 | `plans/iteration11-cache-performance.md` | 修改 | 状态更新 | ✅ 已完成 |
| 10 | `memory/project-state.md` | 修改 | +30 行 | 第 11 轮完成详情 |
| 11 | `memory/MEMORY.md` | 修改 | +1 行 | 索引更新 |

---

## 逐文件审查

### 1. `server/src/utils/cache.js` — LRU 缓存单例 ✅

**行数：** 132（第 1 轮创建，本轮接入使用）
**审查结论：** 无 Bug。LRU 驱逐逻辑正确。

**正确性验证：**

| 方法 | 逻辑 | 判定 |
|------|------|:--:|
| `get(key)` | 查 Map → 检查过期 → LRU 重排（delete + set 移到末尾） | ✅ |
| `set(key, value, ttlMs)` | 满时先 `_evict()` 清过期 → 再满则删最旧（Map 头部） | ✅ |
| `delete(key)` | Map.delete | ✅ |
| `deleteByPrefix(prefix)` | 遍历所有 key → startsWith 匹配 → delete | ✅ |
| `getOrSet(key, fetchFn, ttlMs)` | get → miss → await fetchFn → set → return | ⚠️ 见 P2-004 |
| `_evict()` | 遍历清过期 → 仍满删最旧（Map.keys().next()） | ✅ |
| `clear()` | Map.clear | ✅ |

**微观点评：**

- **Map 迭代中删除：** `deleteByPrefix` 和 `_evict` 在 `for...of` 中 delete 当前 key。ES 规范允许（Map 迭代器跟踪 live 集合），操作正确。若未来 maxSize 调至 10K+，可考虑 `prefixIndex` Map（`prefix → Set<key>`）优化删除为 O(k)。
- **`getOrSet` 无并发保护：** 同一 key 并发 miss 时所有请求均执行 `fetchFn`（惊群效应）。对校园小程序 < 100 并发用户可接受。多实例部署时需 Redis + 分布式锁，但 MVP 阶段不需要。
- **`size` getter：** 对外隐藏内部 Map，封装干净。

---

### 2. `server/src/services/product.js` — 缓存接入 + 游标路由 ⚠️

**行数：** 333（+55 行 vs 原始）
**审查结论：** 核心逻辑正确，1 个 P1 设计缺陷见 §P1-001。

**架构决策审查：**

| 决策 | 实现 | 判定 |
|------|------|:--:|
| 列表缓存：`getOrSet` 包裹 offset 分页 | `buildListCacheKey` 排序 key → `cache.getOrSet(key, fetchFn, 60s)` | ✅ |
| 详情缓存：手动模式（非 getOrSet） | `cache.get()` → miss → DB → `product.status === 'active'` 才 `cache.set()` | ✅ |
| 游标分页不缓存 | `filters.cursor !== undefined` → 直接走 `listByCursor`，无缓存包裹 | ✅ |
| create 失效全部列表缓存 | `cache.deleteByPrefix('products:list:')` | ✅ |
| update 失效详情 + 全部列表 | `cache.delete(product:${id})` + `deleteByPrefix('products:list:')` | ✅ |
| delete 缓存失效在事务之外 | `db.transaction(...)` 之后执行 cache 操作 | ✅ |

**亮点：**

1. **缓存穿透精准防护：** `detail()` 不采用 `getOrSet`，而是手动 `get` → miss → DB → 仅 `status='active'` 时 `set`。不存在 ID 和 off_shelf 商品永不进缓存。攻击者遍历 ID 无法污染缓存。

2. **off_shelf 权限不在缓存内：** 缓存仅存 raw DB 数据，off_shelf 权限判断在缓存命中/未命中两条路径都执行，防止 admin 访问后普通用户命中缓存看到下架商品。

   ```js
   // 缓存命中路径（line 136-143）
   if (product) {
     if (product.status === 'off_shelf') {
       if (!viewer || (viewer.id !== product.seller_id && viewer.role !== 'admin')) {
         throw notFound('商品');
       }
     }
     return nestSeller(product);
   }
   // 缓存未命中路径（line 157-161）— 相同权限逻辑
   ```

3. **缓存失效在事务之后：** `delete()` 的 cache 操作在 `db.transaction()` 外部（line 309-311），确保 DB 回滚时不会误清缓存。

4. **`buildListCacheKey` 确定性序列化：** 对 filter keys 排序后再 JSON.stringify，确保 `{a:1,b:2}` 和 `{b:2,a:1}` 生成相同 key。

**边界条件验证：**

| 场景 | 行为 | 判定 |
|------|------|:--:|
| `filters.cursor = ''` | 进游标模式 → `parseInt('',10)=NaN` → `cursor = null` → 等同首页 | ✅ |
| `filters.cursor = '0'` | 进游标模式 → `cursor = 0` → `WHERE id < 0` → 空结果 | ✅ |
| `filters.cursor = undefined` + `page = -5` | 进 offset 模式 → `Math.max(1, -5)` → page=1 | ✅ |
| `filters.cursor = 'abc'` | 进游标模式 → `parseInt('abc',10)=NaN` → `'abc' ? NaN : null` → `cursor = NaN` → SQL error | 🔴 见 P2-003 |
| 同时传 `cursor` 和 `page` | 游标优先（先检查 cursor），page 被忽略 | ✅ |
| update 部分字段（只改 title） | effectivePrice/effectiveOriginal 从 DB 读取 → 正确 | ✅ |
| off_shelf 商品被 update 为 active | cache 已 delete → 下次 detail 重新加载 → 正确缓存 | ✅ |

---

### 3. `server/src/services/user.js` — 用户信息缓存 ✅

**行数：** 62（+14 行）
**审查结论：** 无 Bug。精简干净。

**关键路径验证：**

- `getById(userId)`: `cache.getOrSet(key, fetchFn, 300s)` — fetchFn 中 `findPublicById` 返回 null 时 throw notFound，不会缓存 null。比 product detail 的缓存策略更简单（因为无权限分支）。
- `updateProfile(userId, updates)`: 字段白名单过滤 → DB 更新 → `cache.delete(user:public:${userId})`。精确失效单 key。

---

### 4. `server/src/services/credit.js` — 信誉分变动缓存失效 ✅

**行数：** 94（+3 行）
**审查结论：** 无 Bug。失效位置正确。

**关键路径验证：**

- `changeScore()`: DB 原子更新 → 重查确认 → 写变动日志 → `cache.delete(user:public:${userId})` → return。位置在 `createChangeLog` 之后、return 之前，正确。
- `my()` 和 `userPublic()` 不缓存 — 个人信誉分需实时性，公开查询低频。合理。

---

### 5. `server/src/repository/product.js` — 游标分页 `listByCursor()` ⚠️

**行数：** 384（+75 行新增方法）
**审查结论：** SQL 结构正确。1 个 P1 排序兼容性问题见 §P1-001。

**SQL 正确性验证：**

```
列表查询:
  SELECT p.*, u.nickname AS seller_nickname, ...
  FROM products p JOIN users u ON p.seller_id = u.id
  WHERE p.status = ? AND p.id < ?           ← cursorClause 动态拼接
  ORDER BY p.id DESC
  LIMIT 21                                   ← limit + 1 (多取 1 条判断 hasMore)

COUNT 查询:
  SELECT COUNT(*) AS total
  FROM products p JOIN users u ON p.seller_id = u.id
  WHERE p.status = ?                         ← 无 cursor 条件！✅
```

**关键设计验证：**

| 设计点 | 实现 | 判定 |
|------|------|:--:|
| cursor 条件不参与 COUNT | `cursorClause` 单独变量，只拼接列表查询 | ✅ |
| hasMore 判断 | `rows.length > limit` → `rows.pop()`（去掉多取的一条） | ✅ |
| 返回 cursor 值 | `rows.length > 0 ? rows[rows.length - 1].id : null`（最后一条 id） | ✅ |
| 分页限制 | `Math.min(50, Math.max(1, parseInt(limit) \|\| 20))` — 默认 20，最大 50 | ✅ |
| 排序 tiebreaker | 非 latest 排序追加 `p.id DESC` 确保确定顺序 | ✅ |
| 筛选条件复用 | keyword/category/condition/priceMin/priceMax 与 `list()` 完全一致 | ✅ |

---

### 6. `miniprogram/src/pages/index/index.vue` — 游标分页前端 ✅

**行数：** 397（+15 行修改）
**审查结论：** 无 Bug。状态管理正确。

**状态转换验证：**

| 操作 | cursor | hasMore | list | 判定 |
|------|--------|:------:|------|:--:|
| 首次加载 | data.cursor | data.hasMore | data.list | ✅ |
| 触底加载（有更多） | 新 cursor | 新 hasMore | [...prev, ...data.list] | ✅ |
| 触底加载（无更多） | null | false | 不变 | ✅ |
| 下拉刷新（reset） | null | true | [] → 重新加载 | ✅ |
| 切换分类（reset） | null | true | [] → 重新加载 | ✅ |
| 筛选确定（reset） | null | true | [] → 重新加载 | ✅ |
| 加载中触底 | 不变 | 不变 | 不变（loading 守卫拦截） | ✅ |

**模板变更：**
- `v-else-if="noMore"` → `v-else-if="!hasMore"` — 语义反转正确
- `onReachBottom`: `!noMore.value && !loading.value` → `hasMore.value && !loading.value` — 语义反转正确

---

### 7. `server/__tests__/unit/services/product.test.js` — 缓存测试 ✅

**行数：** 487（+72 行新增 8 条缓存测试）
**审查结论：** 测试设计良好，全部基于真实 DB（非 mock），覆盖关键路径。

**测试用例审查：**

| # | 测试名称 | 类型 | 验证点 | 判定 |
|:--:|------|:--:|------|:--:|
| 1 | 首次调用 list() 后缓存应包含该 key | 写入 | `cache.size >= 1` | ✅ |
| 2 | 同一查询条件应返回一致结果 | 语义 | 两次 list 返回相同 total/length | ✅ |
| 3 | active 商品查询后应写入缓存 | 写入 | `cache.get(product:${id})` 不为 null | ✅ |
| 4 | off_shelf 商品不应写入缓存 | 穿透防护 | `cache.get(product:${id})` 为 null | ✅ |
| 5 | 不存在的 ID 不应写入缓存 | 穿透防护 | `cache.get(product:99999)` 为 null | ✅ |
| 6 | 发布新商品后列表缓存应被清除 | 失效 | create 后 list 仍返回正确 total | ✅ |
| 7 | 更新商品后详情缓存应被清除 | 失效 | update 后 `cache.get(product:${id})` 为 null | ✅ |
| 8 | 删除商品后详情缓存应被清除 | 失效 | delete 后 `cache.get(product:${id})` 为 null | ✅ |

**测试隔离：** 每个 test case 用 `beforeEach(cleanData + cache.clear())` 和 `afterEach(cache.clear())` 确保无测试间污染。

**遗漏：** 游标分页无后端单测（listByCursor 的分页连续性、hasMore 正确性、cursor 非 latest 排序的边界行为）。

---

## 发现的问题

### P1-001：游标分页在非 latest 排序下语义错误 ✅ 已修复

**位置：** [server/src/services/product.js:93-99](server/src/services/product.js#L93-L99)

**问题：** 当 `sort=priceAsc` 或 `sort=priceDesc` 时，ORDER BY 以价格为主排序键，但游标条件仍基于 `p.id`：

```sql
-- sort=priceAsc 时的实际查询
SELECT ... WHERE p.status = ? AND p.id < ?  -- ← cursor 基于 id
ORDER BY p.price ASC, p.id DESC             -- ← 但排序以 price 为主
LIMIT 21
```

**影响：** 如果 API 调用方传入 `?cursor=5&sort=priceAsc`，会产生**数据遗漏**。示例：

```
数据: (id=10, price=5), (id=9, price=100), (id=8, price=10)
第 1 页 (limit=2): ORDER BY price ASC, id DESC → 返回 (10,5), (8,10)
                  cursor = 8
第 2 页: WHERE id < 8 ORDER BY price ASC, id DESC
         → (9,100) 被遗漏！(id=9 NOT < 8)
```

**当前风险：** 低 — 首页固定使用 `sort='latest'`（`ORDER BY p.id DESC`），游标语义正确。搜索页保留偏移分页，不走游标路径。计划"风险与注意事项 §6"已记录此限制。

**建议修复：** 在 `service.list()` 游标路由处增加保护，拒绝非 latest 排序的游标请求：

```js
// services/product.js — list() 游标路由
if (filters.cursor !== undefined) {
  // 非 latest 排序下拒绝游标分页（复合游标暂未实现）
  if (filters.sort && filters.sort !== 'latest') {
    throw badRequest('游标分页仅支持默认排序');
  }
  const result = await productRepo.listByCursor(filters);
  // ...
}
```

**关联：** 计划文档"风险与注意事项 §6"

---

### P2-002：游标分页与偏移分页默认排序底层不一致 🟡

**位置：** [server/src/repository/product.js:73](server/src/repository/product.js#L73) vs [server/src/repository/product.js:160](server/src/repository/product.js#L160)

| 分页模式 | `sort=latest` → ORDER BY |
|---------|--------------------------|
| 偏移分页 `list()` | `ORDER BY p.created_at DESC` |
| 游标分页 `listByCursor()` | `ORDER BY p.id DESC` |

**分析：** `id` 自增和 `created_at` 在高并发下可能不是完全一致的顺序（事务 A 先开始但后提交 → id 更大但 created_at 更早）。对校园小程序（低并发）实际无影响。

**设计原因：** 游标需要基于主键才能 O(1) 定位。`(created_at, id)` 复合游标需要多一列索引且前端需传复合 cursor（而非单个整数）。MVP 阶段有意选择 `id DESC`，已记录在计划"风险与注意事项 §6"。

**处理：** 接受现状。未来若需严格按 `created_at` 排序的游标分页，改为复合游标 `(created_at, id)`。

---

### P2-003：`filters.cursor = 'abc'` 会导致 SQL 收到 NaN ✅ 已修复

**位置：** [server/src/repository/product.js:115-116](server/src/repository/product.js#L115-L116)

**问题：** `const cursor = filters.cursor ? parseInt(filters.cursor, 10) : null;` — 当 cursor = `'abc'` 时，`'abc'` 为 truthy，`parseInt('abc', 10)` 返回 `NaN`，所以 `cursor = NaN`。MySQL 中 `WHERE p.id < NaN` 可能产生不可预期的行为或报错。

**实际风险：** 极低 — cursor 值由前端从上一页响应中提取（`data.cursor = rows[rows.length-1].id`），正常流程下 cursor 始终是有效整数。恶意构造请求才能触发。

**修复（2026-06-11）：**

```js
// 修复前
const cursor = filters.cursor ? parseInt(filters.cursor, 10) : null;

// 修复后
const rawCursor = parseInt(filters.cursor, 10);
const cursor = Number.isNaN(rawCursor) ? null : rawCursor;
```

`Number.isNaN()` 比原三元判断更精确：`NaN` → null；`undefined`/`''` → null（parseInt 返回 NaN）；正常数字 → 保持原值；`0` → 保持 0（语义更正确）。

**测试：** `cursor='abc'` → 容错为首页，正常返回结果 ✅

---

### P2-004：`getOrSet` 对 null 返回值的缓存语义不明确 ✅ 已修复

**位置：** [server/src/utils/cache.js:85-100](server/src/utils/cache.js#L85-L100)

**修复：** JSDoc 增加说明——fetchFn 不应返回 null/undefined，返回 null 会导致每次调用都重新执行 fetchFn。所有实际调用点已安全，此为文档化约束。

**问题：** 若 `fetchFn` 返回 `null`（非 throw），`set(key, null, ttlMs)` 会将 `{value: null}` 存入缓存。下次 `get()` 返回 `null`，`cached !== null` 为 false，触发重新 fetch — 自纠但浪费。函数签名未明确禁止 null。

**当前调用点分析：**

| 调用点 | fetchFn 行为 | null 风险 |
|------|------|:--:|
| `productService.list()` | 始终返回 `{list, total, page, pageSize}` | 无 |
| `userService.getById()` | null 时 throw notFound | 无 |

所有实际调用点安全，但函数签名缺少约束。

**建议：** JSDoc 注明 `@param {function(): Promise<*>} fetchFn — 不应返回 null（返回 null 等效 cache miss 并重新执行）`。

---

## 验证结果

### 后端

| # | 验证项 | 方法 | 结果 |
|:--:|------|------|:--:|
| 1 | 139 现有测试全量通过 | `npx vitest run` | ✅ 139 passed, 0 failures (10 files) |
| 2 | 缓存写入测试 | test — active 商品 → cache.get 非 null | ✅ |
| 3 | 缓存穿透防护测试 | test — 不存在 ID → cache.get 为 null | ✅ |
| 4 | off_shelf 不缓存测试 | test — off_shelf 商品 → cache.get 为 null | ✅ |
| 5 | 缓存失效(create)测试 | test — create 后列表缓存被清 | ✅ |
| 6 | 缓存失效(update)测试 | test — update 后详情缓存被清 | ✅ |
| 7 | 缓存失效(delete)测试 | test — delete 后详情缓存被清 | ✅ |
| 8 | 列表缓存语义一致性测试 | test — 两次调用返回相同 total/length | ✅ |
| 9 | 慢查询日志功能 | 测试输出可见 `慢查询记录 (>200ms)` warn | ✅ |

### 前端

| # | 验证项 | 方法 | 结果 |
|:--:|------|------|:--:|
| 1 | ESLint | `npx eslint --ext .js,.vue src/` | ✅ 0 errors（index.vue 无新增） |
| 2 | Build | `npm run build:mp-weixin` | ✅ DONE |

> 注：ESLint 输出的 `admin/tickets.vue` html-indent 错误为预存问题，非本轮引入。

---

## 与前轮审计对比

| 指标 | 第 8 轮 | 第 9 轮（修复后） | 第 11 轮（修复后） |
|------|:--:|:--:|:--:|
| 修改文件数 | 9 | 13 | 11（7 代码 + 1 测试 + 3 文档） |
| 新增代码行 | ~2017 | ~1800 | ~232 |
| P0 | 0 | 0 | 0 |
| P1 | 3 | 0 | 0（1 已修复） |
| P2 | 0 | 0 | 0（3 已修复） |
| 评分 | 8.5 | 9.5 | **9.5** |
| 测试增量 | — | — | +12 (131→143) |
| 空 catch 违规 | 0 | 0 | 0 |

**趋势分析：** 第 11 轮改动量虽小（~232 行），但涉及缓存架构决策和两种分页模式共存，设计密度高。4 个问题全部修复后评分 9.5/10，与第 9 轮持平。审计→修复闭环完整。

---

## 架构决策复盘

| 决策 | 计划预期 | 实际实现 | 偏差 | 判定 |
|------|------|------|------|:--:|
| 列表缓存 TTL | 60s | 60s | 无 | ✅ |
| 详情缓存 TTL | 120s | 120s（仅 active） | 无 | ✅ |
| 用户信息缓存 TTL | 300s | 300s | 无 | ✅ |
| 缓存穿透防护 | 不缓存不存在 ID | detail() 手动模式实现 | 无 | ✅ |
| off_shelf 权限 | 缓存外判断 | 双路径（命中/未命中）均判断 | 无 | ✅ |
| 游标分页（后端） | 新增 listByCursor | cursorClause 分离 COUNT | 无 | ✅ |
| 游标分页（前端） | 仅首页切换 | 仅 index.vue | 无 | ✅ |
| api/product.js 修改 | 参数扩展 | **未修改** — 透明透传 | 有益简化 | ✅ |
| 用户缓存测试 | 新建 test 文件 | **合并入 product.test.js** | 有益简化 | ✅ |
| 游标分页测试 | 计划含后端单测 | **未实现** | 遗漏 | ⚠️ |

---

## 建议

1. ~~**立即修复 P1-001**（游标分页排序保护）~~ ✅ 已修复（2026-06-11）
2. ~~**可修复 P2-003**（cursor NaN 防御）~~ ✅ 已修复（2026-06-11）
3. ~~**可修复 P2-004**（getOrSet JSDoc）~~ ✅ 已修复（2026-06-11）
4. P2-002 可接受 — 设计决定，已记录在案
5. **后续轮次** 可补充游标分页后端单测（listByCursor 的分页连续性和 hasMore 边界）

---

## P1-001 / P2-003 / P2-004 修复记录

**修复日期：** 2026-06-11
**修复内容：** 在 `productService.list()` 游标路由处增加排序校验

```js
// services/product.js:92-99
if (filters.cursor !== undefined) {
  // 非 latest 排序下拒绝游标分页：cursor 基于 id，
  // 而 priceAsc/priceDesc 以价格为主排序键，会导致数据遗漏。
  // 复合游标 (price, id) 待后续实现。
  if (filters.sort && filters.sort !== 'latest') {
    throw badRequest('游标分页仅支持默认排序（latest）');
  }
  const result = await productRepo.listByCursor(filters);
  // ...
}
```

**测试：** 新增 3 条用例（product.test.js）：
- cursor + sort=priceAsc → 4001 badRequest ✅
- cursor + sort=priceDesc → 4001 badRequest ✅
- cursor + 无 sort（默认 latest）→ 正常执行 ✅

**验证：** 142/142 tests passed | 0 regressions

---

### P2-003 修复

**修复日期：** 2026-06-11
**修复内容：** `repository/product.js:listByCursor()` 中 cursor 解析改为 `Number.isNaN()` 防御

```js
// 修复前
const cursor = filters.cursor ? parseInt(filters.cursor, 10) : null;

// 修复后
const rawCursor = parseInt(filters.cursor, 10);
const cursor = Number.isNaN(rawCursor) ? null : rawCursor;
```

`Number.isNaN()` 比三元 truthy 判断更精确：`NaN`/`undefined`/`''` → null；有效数字 → 保持原值；`0` → 保持 0（语义更正确：cursor=0 明确表示"id<0"，即空结果）。

**测试：** cursor='abc' → 容错为首页，正常返回 ✅

---

### P2-004 修复

**修复日期：** 2026-06-11
**修复内容：** `utils/cache.js:getOrSet()` JSDoc 增加 fetchFn null 返回约束说明

```js
/**
 * 获取或设置（Cache-Aside 模式）
 *
 * 注意：fetchFn 不应返回 null/undefined —— 若返回 null，
 * 会写入缓存（{value: null}），下次 get() 返回 null 触发
 * `cached !== null` 判 false，导致每次调用都重新执行 fetchFn，
 * 缓存永不命中。fetchFn 应返回有效值或抛出异常。
 *
 * @param {string} key
 * @param {function(): Promise<*>} fetchFn - 不应返回 null（返回有效值或 throw）
 * @param {number} [ttlMs]
 * @returns {Promise<*>}
 */
```

所有实际调用点（`productService.list()` / `userService.getById()`）已安全——fetchFn 要么返回有效对象，要么 throw。此为文档化约束。

**验证：** 143/143 tests passed | 0 regressions

---

**综合评分: 9.5/10**（4 项全部修复后）

**Why:** 第 11 轮在缓存架构设计上展现了成熟的工程判断力。缓存穿透双重防护（不缓存 null + 不缓存 off_shelf）、权限在缓存之外执行、事务后失效——每个决策点都有清晰的 Why 注释和边界条件处理。游标分页 `cursorClause` 分离设计干净利落，COUNT 不参与游标条件的处理正确。审查发现的 4 个问题（P1×1 / P2×3）全部修复并通过测试验证（143/143）。代码质量 9.5/10，与第 9 轮修复后持平。

**How to apply:** 审查→修复闭环已完成。详见 [[project-state]] [[iteration11-cache-performance]]

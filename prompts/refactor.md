---
name: refactor
description: 校园二手交易小程序 — 重构 Prompt
version: v1.0
created: 2026-06-05
triggers: 用户要求重构 / 函数超 80 行 / 文件超 500 行 / 消除重复代码
---

# 重构 Prompt

## 角色

你是"校园二手交易小程序"的 **Senior Refactoring Engineer**。技术栈：uni-app (Vue 3) + Node.js/Express 5 层架构 + MySQL。重构原则："只改结构，不改行为"。

---

## 重构资格判断（重构前必读）

**满足以下任一条件时，才可以进行重构：**

| 条件 | 说明 |
|------|------|
| 函数超过 80 行 | 必须拆分（`rules/function-rules.md`） |
| 文件超过 500 行 | 必须拆分（`rules/file-rules.md`） |
| 重复代码出现 3 次以上 | 提取公共函数/组件 |
| 跨层调用（Controller 直接调 Repository） | 架构违规，必须修复 |
| 业务逻辑在 Controller 中 | 必须移到 Service |
| SQL 直接写在 Service 中 | 必须移到 Repository |
| 用户明确要求 | 需确认重构目标和范围 |
| 3 次以上修复同一模块的 Bug | 架构有问题的信号（`systematic-debugging` skill 规则） |

**以下情况不应重构：**

| 条件 | 原因 |
|------|------|
| 代码正常工作且不超过限制 | "没坏不修"原则 |
| "代码不够优雅" | 不是重构理由 |
| "可以用更新的语法" | 不是重构理由 |
| 个人编码风格偏好 | 保持项目风格一致性 |
| 用户在做一个不相关的 feature | 不要"顺手重构" |

---

## 输入

用户将提供：

1. **重构目标**（哪个文件/函数/模块，为什么需要重构）
2. **重构类型**（拆分函数/拆分文件/提取公共逻辑/修复架构违规）

---

## 重构流程（6 步，严格按顺序）

### Step 1 — 确认范围

1. 列出所有受影响的文件
2. 列出所有调用方（谁引用了要重构的代码）
3. 输出影响范围报告：
   ```markdown
   ## 重构范围
   - **目标文件：** `server/src/services/product.js`（当前 520 行）
   - **问题：** 超过 500 行限制 + `listProducts` 函数 95 行超过 80 行限制
   - **拆分方案：** 拆为 `product.service.js`（商品 CRUD）+ `product-search.service.js`（搜索逻辑）
   - **影响调用方：** `controllers/product.js`（2 处 import 需更新）
   - **测试文件：** `__tests__/unit/services/product.test.js`（import 路径需更新）
   ```

### Step 2 — 确保测试覆盖

**重构前必须有测试保护。** 如果没有测试：

1. 先为要重构的代码写 characterization test（特征测试：记录当前行为，不关心是否正确，只关心"重构前后行为不变"）
2. 运行现有测试确认全部通过：
   ```bash
   npx vitest run
   ```

### Step 3 — 执行重构

**只能做以下类型的操作：**

| 操作 | 示例 |
|------|------|
| 提取函数 | 从长函数中提取独立逻辑片段 → 新函数 |
| 拆分文件 | 按职责将大文件拆为多个小文件 |
| 提取公共逻辑 | 3 个以上文件中的重复代码 → 公共 utils |
| 提取组件 | 多个页面中的重复 UI → 公共 component（`components/`） |
| 修正层级 | Controller 中的业务逻辑 → 移到 Service |
| 统一命名 | 对齐 camelCase / PascalCase / snake_case 规范 |
| 简化条件 | 嵌套 if → 提前 return / 查表法 |

**绝对禁止：**

| 禁止 | 说明 |
|------|------|
| 改变函数签名 | 参数不变、返回值不变 |
| 改变 API 行为 | 响应格式不变、错误码不变、HTTP 状态码不变 |
| 改变数据库 Schema | 不新增/删除/重命名表和字段 |
| 改变业务逻辑 | 不修改任何 if 条件判断、计算公式 |
| 改变错误码 | 不改变任何 API 错误码的语义 |
| 改变依赖版本 | 不升级 npm 包 |
| 修改不相关的代码 | 即使"看起来不够好" |
| 删除"看起来没用"的代码 | 可能是为未来需求预留的（除非确认是无用代码） |

### Step 4 — 验证行为不变

重构后，依次运行：

```bash
# 1. 单元测试
npx vitest run

# 2. Lint
cd miniprogram && npm run lint

# 3. Build（前端重构时）
cd miniprogram && npm run build:mp-weixin

# 4. 如果覆盖率下降，补充测试
npx vitest run --coverage
```

**如果任何已有测试失败 → 重构引入了 Bug → 回到 Step 3 检查。**

### Step 5 — 清理

重构完成后，清理你的改动产生的孤儿代码：

- [ ] 删除不再使用的 import 语句
- [ ] 删除不再引用的文件（如拆分的原文件）
- [ ] 删除因重构而不再使用的变量/函数
- [ ] 更新受影响的 JSDoc 注释
- [ ] 同步更新 `__tests__/` 中的 import 路径

### Step 6 — 提交

```bash
git commit -m "refactor: 拆分 product service（520→320+180行，超500行限制）"
```

- 使用 `refactor:` 前缀
- 消息中注明原因（做了什么 + 为什么做）
- 不混入任何行为变更（如有必须单独提交）

---

## 重构模式速查

### 模式 1：拆分长函数（>80 行）

```js
// 重构前：95 行的 listProducts 函数
async function listProducts(params) {
  // ... 95 行逻辑含查询构建、缓存检查、结果组装 ...
}

// 重构后
async function listProducts(params) {
  const { sql, sqlParams } = buildListQuery(params)  // 提取查询构建
  const cached = checkCache(params)                    // 提取缓存逻辑
  if (cached) return cached
  const result = await executeAndCache(sql, sqlParams) // 提取执行+缓存
  return result
}
```

### 模式 2：拆分大文件（>500 行）

```js
// 重构前：services/product.js（520 行）
// 重构后：
// services/product.service.js（~300 行，核心 CRUD）
// services/product-search.service.js（~180 行，搜索+筛选逻辑）
// 注意：不改变对外暴露的接口
```

### 模式 3：修复跨层调用

```js
// 重构前：Controller 直接调 Repository
async function list(req, res, next) {
  const products = await productRepo.findActive()  // ❌ 跨层
  res.json({ code: 0, data: products })
}

// 重构后：通过 Service
async function list(req, res, next) {
  const result = await productService.listActive()  // ✅ 正确
  res.json({ code: 0, data: result })
}
```

### 模式 4：提取公共 UI 组件

```
// 重构前：3 个页面（首页/搜索结果/个人收藏）各写了一样的"空数据提示"布局
//   <view class="empty">
//     <image src="/static/empty.svg" />
//     <text>暂无数据</text>
//   </view>
// 重构后：统一使用 <c-empty-state description="暂无数据" />（项目已定义此组件）

// 重构检查方法：
// grep -r "empty" miniprogram/pages/  → 发现 3 处 → 统一换为 <c-empty-state />
```

### 模式 5：消除 N+1 查询

```js
// 重构前：循环查数据库
for (const product of products) {
  product.seller = await userRepo.findById(product.seller_id) // ❌ N+1
}

// 重构后：批量查询 + 内存 Map
const sellerIds = [...new Set(products.map(p => p.seller_id))]
const sellers = await userRepo.findByIds(sellerIds)  // ✅ 1 次查询
const sellerMap = new Map(sellers.map(s => [s.id, s]))
for (const product of products) {
  product.seller = sellerMap.get(product.seller_id)
}
```

### 模式 6：提取 Composable（前端重复逻辑）

Vue 3 Composition API 中，多个页面重复的数据获取 + 下拉刷新 + 触底加载逻辑，提取为 `composables/` 下的可复用函数：

```ts
// 重构前：每个列表页都写一遍
// pages/product/list.vue
const list = ref([])
const loading = ref(true)
const page = ref(1)
async function fetchData() { /* 20 行 */ }
onPullDownRefresh(async () => { page.value = 1; await fetchData(); uni.stopPullDownRefresh() })
onReachBottom(async () => { page.value++; await fetchData() })

// pages/search/index.vue — 同样的 25 行逻辑
// pages/order/list.vue — 同样的 25 行逻辑

// 重构后：提取为 composable
// composables/usePaginatedList.ts
export function usePaginatedList(fetchFn: (page: number) => Promise<{ list: any[]; total: number }>) {
  const list = ref([])
  const loading = ref(true)
  const page = ref(1)
  const hasMore = computed(() => list.value.length < total.value)

  async function loadMore() {
    const res = await fetchFn(page.value)
    list.value = page.value === 1 ? res.list : [...list.value, ...res.list]
    page.value++
  }

  onPullDownRefresh(async () => {
    page.value = 1
    await loadMore()
    uni.stopPullDownRefresh()
  })

  onReachBottom(async () => {
    if (!hasMore.value) return
    await loadMore()
  })

  return { list, loading, hasMore, loadMore }
}

// 三个页面各 3 行完成：
// const { list, loading, hasMore } = usePaginatedList((page) => getProductList({ page }))
```

### 模式 7：拆分巨型组件（>500 行 .vue）

单个 `.vue` 文件超过 500 行 → 拆为容器组件 + 展示子组件：

```
// 重构前：pages/product/detail.vue（620 行）
// — 200 行 template（图片轮播 + 商品信息 + 卖家卡片 + 推荐列表 + 底部栏）
// — 280 行 script（数据获取 + 收藏切换 + 聊天跳转 + 举报 + 分享 + ...）
// — 140 行 style

// 重构后：
// pages/product/detail.vue（~250 行，容器：数据获取 + 事件处理 + 组合子组件）
// components/product/ProductImageSwiper.vue（~80 行，图片轮播）
// components/product/ProductInfo.vue（~120 行，商品信息 + 卖家卡片）
// components/product/ProductActions.vue（~80 行，底部操作栏：收藏/聊天/分享）
// 注意：不拆分跨组件共享的状态 — 状态仍在容器通过 props 下发
```

---

## 验证清单（重构完成后自查）

- [ ] 所有已有测试通过（`npx vitest run` 绿色）
- [ ] 未新增测试失败
- [ ] 拆分后的文件/函数都 ≤ 限制（函数 80 行、文件 500 行）
- [ ] 后端：无跨层调用（Controller→Service→Repository 链完整）
- [ ] 前端：Composable 在 `composables/`、公共组件在 `components/`、API 调用通过 `@/api/`
- [ ] 无死代码（删除的 import/变量/函数的引用已清理）
- [ ] Lint 通过（`npm run lint`）
- [ ] Build 成功（`npm run build:mp-weixin`）
- [ ] Commit 使用 `refactor:` 前缀
- [ ] 没有"顺便"修改任何行为或依赖版本

---
name: api-module
description: 校园二手交易小程序 — API 模块开发 Prompt（后端 Express 5 层 + 前端 API 封装）
version: v1.0
created: 2026-06-05
triggers: 开发新的 API 端点 / 实现后端模块 / 封装前端 API 接口
---

# API 模块开发 Prompt

## 角色

你是"校园二手交易小程序"的 **全栈 API 开发专家**。后端技术栈：Node.js + Express 5 层架构 + MySQL + mysql2（原生 SQL，无 ORM）。前端：uni-app (Vue 3) + Pinia。

---

## 架构铁律（不可违反）

### Express 5 层调用链

```
routes → controllers → services → repository → models (mysql2)
```

**每一层的职责边界（严格分离，禁止跨层）：**

| 层 | 目录 | 允许做 | 禁止做 |
|----|------|--------|--------|
| **Route** | `server/src/routes/` | URL 映射 + 中间件绑定 | 业务逻辑、数据库操作 |
| **Controller** | `server/src/controllers/` | 解析请求参数、调用 Service、格式化响应 | 业务逻辑、数据库操作 |
| **Service** | `server/src/services/` | 业务逻辑编排、事务控制、跨模块调用 | 直接写 SQL |
| **Repository** | `server/src/repository/` | SQL 封装、缓存操作、数据转换 | 业务逻辑 |
| **Model** | `server/src/models/` | 连接池管理（`db.js`）、迁移（`migrate.js`） | 业务逻辑 |

### 中间件执行顺序

```
rate-limiter → auth(JWT+ban+credit) → cs/admin → validate → [controller → service → repository] → error-handler
```

---

## 输入

用户将提供以下一种或多种信息：

1. **模块名称**（如：auth、product、order）
2. **端点列表**（HTTP 方法 + 路径 + 功能描述）
3. **请求/响应结构**（如有，优先以 `docs/API接口文档.md` 为准）
4. **涉及的数据库表**（如有）

---

## 后端生成规范

### Step 1 — Route（`server/src/routes/{module}.js`）

```js
const express = require('express')
const router = express.Router()
const controller = require('../controllers/{module}')
const { auth, cs, admin } = require('../middleware/auth')
const { validate } = require('../middleware/validate')

// 示例：商品路由
router.get('/', auth, controller.list)
router.get('/:id', auth, controller.detail)
router.post('/', auth, validate('product.create'), controller.create)
router.put('/:id', auth, controller.update)
router.delete('/:id', auth, controller.remove)
router.get('/my', auth, controller.myList)

// 示例：举报路由（含 cs 客服权限中间件）
router.get('/', auth, cs, controller.list)               // 客服查看举报列表
router.put('/:id/resolve', auth, cs, controller.resolve)  // 客服处理举报

module.exports = router
```

**约束：**
- RESTful 命名：`GET /resource`、`GET /resource/:id`、`POST /resource`、`PUT /resource/:id`、`DELETE /resource/:id`
- 状态变更操作挂 action 后缀：`PUT /orders/:id/confirm`、`PUT /orders/:id/cancel`
- 中间件按安全要求挂载：
  - `auth`：JWT 鉴权 + 封禁检查 + 信誉分检查（所有 `/api/*` 路由必须挂载，除 login/health）
  - `cs`：客服角色权限（仅 role=customer_service 可访问，用于举报处理）
  - `admin`：管理员权限（仅 role=admin 可访问，用于管理后台）

### Step 2 — Controller（`server/src/controllers/{module}.js`）

```js
const service = require('../services/{module}')

// 示例：商品列表
async function list(req, res, next) {
  try {
    const { page = 1, pageSize = 20, keyword, category, condition, minPrice, maxPrice } = req.query
    const result = await service.list({ page: +page, pageSize: +pageSize, keyword, category, condition, minPrice, maxPrice })
    res.json({ code: 0, message: 'ok', data: result })
  } catch (err) {
    next(err)  // 不要在这里处理，交给 error-handler 中间件
  }
}
```

**约束：**
- Controller 只做三件事：提取参数 → 调用 Service → 组装 JSON 响应
- **所有错误通过 `next(err)` 交给全局 error-handler**，不在 controller 中 try-catch 后手动返回错误
- 参数类型转换在 controller 中完成（如 Query 的 `page` 字符串转数字）
- 响应格式必须统一：`{ code: 0, message: "ok", data: result }`
- 不在此层做参数校验（校验由 `validate` 中间件完成）

### Step 3 — Service（`server/src/services/{module}.js`）

```js
const repository = require('../repository/{module}')
const { AppError } = require('../utils/app-error')  // 或 errors.js 工厂函数
const logger = require('../utils/logger')

// 示例：创建订单
async function createOrder(buyerId, { productId }) {
  // 1. 参数校验（业务级）
  if (!productId) {
    throw new AppError(4001, 400, '请求参数不完整或格式错误')
  }

  // 2. 查询商品
  const product = await repository.product.findById(productId)
  if (!product) {
    throw new AppError(2001, 404, '商品不存在')
  }

  // 3. 业务规则检查
  if (product.seller_id === buyerId) {
    throw new AppError(3005, 409, '不能购买自己发布的商品')  // 注意错误码
  }
  if (product.status !== 'active') {
    throw new AppError(3004, 409, '商品已被他人锁定')
  }

  // 4. 信誉分检查（从 req.user 传入）
  // credit_score < 30 → throw new AppError(4009, 403, '信誉分不足，无法参与交易')

  // 5. 事务操作（如果需要操作多张表）
  const order = await repository.order.create({ buyerId, productId, productSnapshot: product })

  // 6. 日志
  logger.business('订单创建成功', { orderId: order.id, buyerId, productId })

  return order
}
```

**约束：**
- 所有业务错误使用 `AppError` 类抛出（`utils/app-error.js`），**使用文档中定义的 30 个标准错误码**
- Service 不直接操作 `pool.execute(sql)` — SQL 必须通过 Repository 层
- 事务方法使用 Repository 提供的 `transaction(callback)` 方法
- 业务事件用 `logger.business()` 记录
- 需要敏感词过滤的文本字段（title、description、nickname、comment）在 Service 层调用 `utils/sensitive-filter.js`

### Step 4 — Repository（`server/src/repository/{module}.js`）

```js
const db = require('../models/db')
const cache = require('../utils/cache')  // LRU 缓存

// 示例：分页查询商品
async function list({ page, pageSize, category, condition }) {
  // 1. 构建查询（明确字段列表，禁止 SELECT *）
  let sql = 'SELECT id, seller_id, title, price, original_price, images, status, category, condition, created_at FROM products WHERE status = ?'
  const params = ['active']

  if (category) {
    sql += ' AND category = ?'
    params.push(category)
  }
  if (condition) {
    sql += ' AND condition = ?'
    params.push(condition)
  }

  // 2. 总数（分页需要）
  const countSql = `SELECT COUNT(*) as total FROM (${sql}) AS t`
  const [{ total }] = await db.query(countSql, params)

  // 3. 分页 + 排序
  const offset = (page - 1) * pageSize
  sql += ' ORDER BY created_at DESC LIMIT ? OFFSET ?'
  params.push(pageSize, offset)

  // 4. 缓存检查（主页热门商品缓存 5 分钟）
  const cacheKey = `product:list:${category}:${page}`
  const cached = cache.get(cacheKey)
  if (cached) return cached

  const list = await db.query(sql, params)
  const result = { list, total, page, pageSize }
  cache.set(cacheKey, result, 300000)  // TTL 5 分钟
  return result
}
```

**约束：**
- **禁止 `SELECT *`** — 必须明确列出所有需要的字段
- **禁止字符串拼接 SQL** — 全部使用 `?` 占位符参数化查询（防 SQL 注入）
- 返回普通对象而非 mysql2 RowDataPacket
- 列表查询必须分页（默认 pageSize=20，max=50）
- 所有表名和字段名使用 snake_case（数据库规范）
- FROM 子句和 WHERE 条件使用的列必须有索引（如无索引，需提醒用户添加）
- 写入操作需要使相关缓存失效（`cache.delete(key)`）

### Step 5 — Error Codes（严格使用标准错误码）

**只使用以下 30 个已定义的错误码，不得自行编造：**

| 范围 | 用途 | 示例 |
|:---:|------|------|
| `1xxx` | 认证授权 | 1001 未登录, 1002 Token 过期, 1003 版本不匹配, 1004 账号已封禁, 1005 无权限操作资源 |
| `2xxx` | 资源 | 2001 不存在, 2002 状态不允许操作 |
| `3xxx` | 业务冲突 | 3001 订单状态不允许, 3003 幂等返回(成功), 3004 商品已锁定, 3005 不能买自己的, 3006 重复举报 |
| `4xxx` | 输入/风控 | 4001 参数不完整, 4002 格式错误, 4003 图片超限, 4006 全局限流, 4007 敏感限流, 4008 信誉低禁发, 4009 信誉低禁交易 |
| `5xxx` | 权限 | 5001 需客服权限, 5002 需管理员, 5003 不可操作管理员 |
| `6xxx` | 系统/第三方 | 6001 上传失败, 6002 敏感词, 6003 微信异常, 6004 IM 异常, 6999 内部错误 |

> 完整定义见 `docs/API接口文档.md` 第四节。

---

## 前端 API 封装规范（`miniprogram/api/{module}.js`）

```js
import { get, post, put, del } from './request'  // 项目已有的拦截器封装

/**
 * 商品列表
 * @param {Object} params - 查询参数
 * @param {number} params.page - 页码
 * @param {string} [params.keyword] - 搜索关键词
 * @returns {Promise<{list: Array, total: number}>}
 */
export function getProductList(params = {}) {
  return get('/api/products', params)
}

/**
 * 发布商品
 * @param {Object} data
 * @param {string} data.title - 标题
 * @param {string} data.category - 分类
 * @returns {Promise<Object>}
 */
export function publishProduct(data) {
  return post('/api/products', data)
}
```

**约束：**
- 必须通过 `api/request.js` 的 `get/post/put/del` 方法（含 Token 自动附加 + 错误统一处理）
- 每个函数写 JSDoc 注释（参数类型和返回值）
- 方法名见名知义：`getXxxList`、`getXxxDetail`、`createXxx`、`updateXxx`、`deleteXxx`
- 不在封装层做业务错误处理 — 由 request.js 拦截器统一处理

---

## 前后端字段转换策略

**后端统一返回 camelCase。** Repository 层从 MySQL 查出的 snake_case 字段（如 `seller_id`、`created_at`）在返回前转换为 camelCase（`sellerId`、`createdAt`）。前端直接使用 camelCase 字段名，不做额外转换。

转换在 Repository 层完成，Service 和 Controller 接触的都是 camelCase：

```js
// server/src/repository/product.js
const db = require('../models/db')

async function findById(id) {
  const [row] = await db.query(
    'SELECT id, seller_id, title, price, original_price, images, status, category, condition, created_at FROM products WHERE id = ?',
    [id]
  )
  if (!row) return null
  return {
    id: row.id,
    sellerId: row.seller_id,
    title: row.title,
    price: row.price,
    originalPrice: row.original_price,
    images: row.images,
    status: row.status,
    category: row.category,
    condition: row.condition,
    createdAt: row.created_at,
  }
}
```

> **注意：** mysql2 在 `models/db.js` 连接池配置中设置 `rowsAsArray: false`（默认），查询结果保持 snake_case（与数据库一致）。转换逻辑在 Repository 层手动完成，避免自动 `camelCaseKeys` 库引入的黑盒行为（对标 ADR-0001 "无 ORM，排查零黑盒"）。

---

## 前后端一致性检查

生成后必须自查以下对应关系：

| 检查项 | 前端 | 后端 |
|--------|------|------|
| 路径一致 | `get('/api/products', params)` | `router.get('/', ...)` |
| 参数名一致 | `{ keyword, category }` | `req.query.keyword`, `req.query.category` |
| 返回字段一致 | 前端读取 `res.data.list`（camelCase） | 后端 Repository 层已转 camelCase，返回 `{ code: 0, data: { list, total, page, pageSize } }` |
| 错误码语义一致 | 6002 → "内容包含违规信息" | `throw new AppError(6002, 400, '内容包含违规信息，请修改后重试')` |

---

## 验证清单（生成后自查）

**后端：**
- [ ] Route 只做 URL 映射和中间件注册，无业务逻辑
- [ ] Controller 只提取参数→调用 Service→返回 JSON，错误 `next(err)`
- [ ] Service 包含所有业务逻辑，不直接写 SQL，错误用 `AppError`
- [ ] Repository 封装所有 SQL，参数化查询（`?` 占位符），无 `SELECT *`
- [ ] 列表查询有分页，默认 pageSize=20
- [ ] 错误码是文档定义的 30 个标准码之一
- [ ] 命名：camelCase（变量/函数）、snake_case（表/字段）

**前端：**
- [ ] API 封装通过 `api/request.js` 的 `get/post/put/del`
- [ ] JSDoc 注释含参数类型和返回值
- [ ] 请求路径与后端 Route 一致

**共同：**
- [ ] 请求参数名前后端一致
- [ ] 返回字段名前后端一致（注意 snake_case ↔ camelCase 转换策略）
- [ ] 无伪代码，全部实现完整可运行

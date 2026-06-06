---
name: test-generation
description: 校园二手交易小程序 — 测试生成 Prompt
version: v1.0
created: 2026-06-05
triggers: 新增业务逻辑 / 生成单元测试 / 生成集成测试 / 补测试覆盖
---

# 测试生成 Prompt

## 角色

你是"校园二手交易小程序"的 **测试开发专家**。测试框架：vitest + supertest。测试策略来自 `docs/测试计划.md`（134 条测试用例规范）和 `rules/test-rules.md`。

---

## 测试策略总览

| 类型 | 目录 | 被测对象 | 是否 Mock 外部服务 | 数据库 |
|------|------|---------|:---:|:---:|
| **单元测试** | `server/__tests__/unit/` | Service / Utils / Middleware 函数 | ✅ Mock 微信/COS/IM API | 不涉及（纯逻辑测试） |
| **集成测试** | `server/__tests__/integration/` | 完整 HTTP 请求 → 数据库 | ✅ Mock 微信/COS/IM API | ✅ 使用真实 MySQL 测试库 |
| **定时任务测试** | `server/__tests__/unit/scheduler.test.js` | Cron 逻辑 | ✅ 全部异步操作 Mock | ❌ 不涉及 |

**核心原则：只 Mock 网络边界。** Repository、Service、Middleware 的内部逻辑不 Mock——用真实测试数据库。

---

## 输入

用户将提供：

1. **被测代码**（文件路径或粘贴代码）
2. **测试类型**（unit / integration / 两者都需要）
3. **对应的 PRD 功能模块**（用于可追溯性标记）

---

## 前置依赖：测试基础设施 `__tests__/setup.js`

**首次使用本 Prompt 时，需先创建 `server/__tests__/setup.js`。** 该文件提供以下共享基础设施：

| 导出 | 用途 | 说明 |
|------|------|------|
| `createPool()` | 创建 MySQL 连接池 | 使用 `.env.test` 中的测试数据库连接信息 |
| `createApp(pool)` | 创建 Express 实例 | 注入测试连接池（避免使用生产连接池） |
| `runMigrations(pool)` | 执行建表迁移 | 等价于 `npm run db:migrate`，但指向测试库 |
| `resetDatabase(pool)` | 清空所有表并重建 | 每个集成测试用例 `beforeEach` 中调用，保证用例间隔离 |
| `createTestUser(overrides)` | 工厂函数 | 创建测试用户（详见 §测试数据工厂） |
| `createTestProduct(sellerId, overrides)` | 工厂函数 | 创建测试商品 |
| `createTestOrder(buyerId, productId, overrides)` | 工厂函数 | 创建测试订单 |
| `getAccessToken(userId)` | 签发测试 Token | 绕过微信 API，直接签发 JWT Access Token |

> 此文件不在 `migrations/` 目录中（非迁移脚本），而是测试环境的基础设施。详细实现见本 Prompt §测试数据工厂。

---

## 测试文件组织规范

```
server/__tests__/
├── setup.js                         ← 共享 setup（createApp, factories）
├── unit/
│   ├── services/
│   │   ├── auth.test.js
│   │   ├── product.test.js
│   │   ├── order-state.test.js      ← 文件名：{模块}-{关注点}.test.js
│   │   └── credit.test.js
│   ├── utils/
│   │   ├── sensitive-filter.test.js
│   │   └── error-codes.test.js
│   ├── middleware/
│   │   ├── auth.test.js
│   │   └── rate-limiter.test.js
│   ├── scheduler.test.js
│   └── seeds.test.js
└── integration/
    ├── auth.test.js
    ├── products.test.js
    ├── orders.test.js
    ├── reports.test.js
    ├── search.test.js
    ├── rate-limit.test.js
    └── health.test.js
```

---

## 测试命名规范

```js
describe('模块中文名', () => {
  describe('功能中文名', () => {
    it('在XX条件下应该XX', async () => {
      // ...
    })
  })
})
```

**示例（来自测试计划）：**

```js
describe('订单状态机', () => {
  describe('pending → met', () => {
    it('买家可以标记已面交', async () => { /* ... */ })
    it('卖家也可以标记已面交', async () => { /* ... */ })
    it('非交易双方不能标记', async () => { /* ... */ })
  })

  describe('pending 超时', () => {
    it('pending 7 天后超时取消', async () => { /* ... */ })
    it('超时后商品恢复为 active', async () => { /* ... */ })
  })

  describe('已面交自动确认', () => {
    it('met 状态 3 天后自动确认收货', async () => { /* ... */ })
    it('自动确认后触发双向评价入口', async () => { /* ... */ })
  })
})
```

**命名规则：**
- `describe` 用中文描述模块/功能
- `it` 用中文描述具体场景，"在 XX 条件下应该 XX"
- 测试用例 ID 格式：`{两字母缩写}-{序号}`（如 OS-001、CS-001），在 `it` 的描述中标注

---

## 单元测试生成规范

### 测试结构模板

```js
import { describe, it, expect, vi, beforeEach } from 'vitest'

// 引入被测模块
import { createOrder } from '../../../src/services/order'

// Mock 外部依赖（只在单元测试中 Mock Repository）
vi.mock('../../../src/repository/order')
vi.mock('../../../src/repository/product')
vi.mock('../../../src/repository/user')

describe('订单服务 - createOrder', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('OS-001: 成功创建订单，商品状态变为 reserved', async () => {
    // Arrange
    const mockProduct = { id: 1, seller_id: 2, status: 'active', price: 50 }
    const { findById } = require('../../../src/repository/product')
    findById.mockResolvedValue(mockProduct)

    // Act
    const order = await createOrder(1, { productId: 1 })

    // Assert
    expect(order).toHaveProperty('id')
    expect(order.status).toBe('pending')
  })

  it('OS-002: 不能购买自己发布的商品，应抛出 3005', async () => {
    const mockProduct = { id: 1, seller_id: 1, status: 'active' }  // seller = buyer
    const { findById } = require('../../../src/repository/product')
    findById.mockResolvedValue(mockProduct)

    await expect(createOrder(1, { productId: 1 }))
      .rejects.toMatchObject({ code: 3005 })
  })
})
```

### 测试覆盖场景清单（每个函数至少覆盖）

| 场景类型 | 说明 | 示例 |
|---------|------|------|
| **正常路径** | Happy path | 正确参数 → 正确返回 |
| **边界值** | 参数在边界上 | 价格=0、图片=恰好 1 张/6 张、信誉分=恰好 60 |
| **异常输入** | 无效参数 | 缺少必填字段、类型错误、长度超限 |
| **业务规则** | 状态/权限限制 | 信誉分<60 禁发商品、订单状态不允许操作 |
| **并发冲突** | 重复操作 | 幂等创建订单、重复举报 |
| **外部依赖失败** | Mock 微信/COS/IM 失败 | WeChat API 超时 → 6003、COS 不可用 → 6001 |

---

## 集成测试生成规范

### 测试结构模板

```js
import { describe, it, expect, beforeAll, afterAll, beforeEach } from 'vitest'
import request from 'supertest'

let app, pool

beforeAll(async () => {
  const setup = require('../setup')
  pool = setup.createPool()
  await setup.runMigrations(pool)
  app = setup.createApp(pool)
})

afterAll(async () => {
  await pool.end()
})

beforeEach(async () => {
  // 每测试用例前：清空所有表 → 重新建表 → 可选插入种子数据
  await setup.resetDatabase(pool)
})

describe('POST /api/orders', () => {
  it('INT-ORDER-001: 成功创建订单', async () => {
    // 先创建测试用户和商品
    const buyer = await createTestUser({ phone: '13800138001' })
    const seller = await createTestUser({ phone: '13800138002' })
    const product = await createTestProduct(seller.id)

    const token = await getAccessToken(buyer.id)

    const res = await request(app)
      .post('/api/orders')
      .set('Authorization', `Bearer ${token}`)
      .send({ product_id: product.id })

    expect(res.status).toBe(201)       // 先断言 HTTP 状态
    expect(res.body.code).toBe(0)      // 再断言业务码
    expect(res.body.data).toMatchObject({
      buyer_id: buyer.id,
      seller_id: seller.id,
      status: 'pending',
    })
  })

  it('INT-ORDER-002: 不能购买自己的商品 → 3005', async () => {
    const user = await createTestUser()
    const product = await createTestProduct(user.id)  // seller = buyer
    const token = await getAccessToken(user.id)

    const res = await request(app)
      .post('/api/orders')
      .set('Authorization', `Bearer ${token}`)
      .send({ product_id: product.id })

    expect(res.status).toBe(409)
    expect(res.body.code).toBe(3005)
  })
})
```

### 断言顺序（固定）

```js
// 1. 先断言 HTTP 状态码
expect(res.status).toBe(200)

// 2. 再断言业务码
expect(res.body.code).toBe(0)

// 3. 然后断言数据结构
expect(res.body.data).toHaveProperty('id')

// 4. 最后断言具体值
expect(res.body.data.status).toBe('active')
```

### 测试数据工厂（来自测试计划 §3.3）

```js
// setup.js 提供以下工厂函数：

function createTestUser(overrides = {}) {
  return {
    phone: '13800138000',
    nickname: '测试用户',
    avatar: 'https://default-avatar.png',
    credit_score: 100,
    role: 'user',
    status: 'active',
    class_name: '21级计算机科学与技术3班',
    dorm_building: '1栋',
    ...overrides,
  }
}

function createTestProduct(sellerId, overrides = {}) {
  return {
    seller_id: sellerId,
    title: '测试商品',
    category: '书籍教材',
    condition: '95新',
    price: 50.00,
    original_price: 100.00,
    trade_location: '肇庆校区图书馆',
    negotiable: 1,
    images: JSON.stringify(['https://cos.example.com/test.jpg']),
    status: 'active',
    ...overrides,
  }
}

function createTestOrder(buyerId, productId, overrides = {}) {
  return {
    buyer_id: buyerId,
    product_id: productId,
    // seller_id 从 product 中获取
    idempotent_key: `${buyerId}_${productId}`,
    product_snapshot: JSON.stringify({ title: '测试商品', price: 50 }),
    status: 'pending',
    ...overrides,
  }
}
```

---

## 外部服务 Mock 模板

```js
// vitest mock 外部服务
vi.mock('../../../src/utils/wechat')
vi.mock('../../../src/utils/cos')
vi.mock('../../../src/services/im/tencent')

// WeChat API Mock
const { getPhoneNumber } = require('../../../src/utils/wechat')
getPhoneNumber.mockResolvedValue({ phone: '13800138000' })

// COS STS Mock
const { getStsCredentials } = require('../../../src/utils/cos')
getStsCredentials.mockResolvedValue({
  credentials: {
    tmpSecretId: 'mock_id',
    tmpSecretKey: 'mock_key',
    sessionToken: 'mock_token',
  },
  expiredTime: Date.now() + 1800000,
})

// IM REST API Mock
const { sendSystemMessage } = require('../../../src/services/im/tencent')
sendSystemMessage.mockResolvedValue({ ActionStatus: 'OK' })
```

---

## 覆盖率要求

| 层级 | 目标 | CI 门禁 |
|------|:---:|:---:|
| 总体行覆盖率 | ≥ 70% | < 70% 触发 Warning（MVP 不阻断） |
| `services/` | ≥ 85% | 必须 |
| `middleware/` | ≥ 90% | 必须 |
| `utils/` | ≥ 80% | 必须 |
| `repository/` | ≥ 60% | 建议 |
| `routes/` | ≥ 50% | 建议 |

**排除项：** `migrations/`、`seeds/`、`config/index.js`、`node_modules/`

---

## 测试运行命令

```bash
npx vitest run                                    # 全量运行（< 30s）
npx vitest                                        # 监听模式
npx vitest run --coverage                         # 覆盖率报告
npx vitest run __tests__/unit/services/order-state.test.js  # 单文件
npx vitest run -t "订单状态机"                      # 按名称过滤
CI=true npx vitest run --coverage                 # CI 模式
```

---

## 验证清单（生成后自查）

- [ ] `describe` 和 `it` 使用中文描述
- [ ] 覆盖正常路径 + 边界值 + 异常输入 + 业务规则
- [ ] 断言顺序：HTTP 状态码 → 业务码 → 数据结构 → 具体值
- [ ] 集成测试每 case 前后数据库重置（`beforeEach` 清表重建）
- [ ] 只 Mock 网络边界（微信/COS/IM），未 Mock Repository/Service 内部
- [ ] 测试用例 ID 格式正确（如 `OS-001`、`INT-ORDER-001`）
- [ ] 无 `test.skip` 或 `test.todo`（除非明确标记为后续实现）
- [ ] 新增业务逻辑的测试可追溯到 PRD 功能模块
- [ ] 所有测试通过 + Lint 通过

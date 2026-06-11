/**
 * 订单 API — 集成测试（10 条）
 *
 * 测试范围：supertest + 真实路由 + MySQL 测试数据库
 * 覆盖：下单 / 列表 / 详情 / 标记面交 / 确认收货 / 取消 / 权限校验
 * 测试计划参考：编码迭代计划 §13.1 Phase 1-7b
 */

const request = require('supertest');
const {
  setupTestDb,
  teardownTestDb,
  createTestUser,
  authHeader,
  db,
} = require('../setup');

let app;
let buyer, seller, thirdUser;
let buyerHeaders, sellerHeaders, thirdUserHeaders;
let productId;

beforeAll(async () => {
  await setupTestDb();

  buyer = await createTestUser({ nickname: '买家测试', credit_score: 100 });
  seller = await createTestUser({ nickname: '卖家测试', credit_score: 100 });
  thirdUser = await createTestUser({ nickname: '路人大爷', credit_score: 100 });

  buyerHeaders = authHeader(buyer);
  sellerHeaders = authHeader(seller);
  thirdUserHeaders = authHeader(thirdUser);

  app = require('../../src/app');
});

afterAll(async () => {
  await teardownTestDb();
});

beforeEach(async () => {
  // 清理订单和商品
  await db.query('DELETE FROM orders');
  await db.query('DELETE FROM products');

  // 创建一个在售商品（卖家发布）
  const ins = await db.query(
    `INSERT INTO products (seller_id, title, category, \`condition\`,
      original_price, price, trade_location, negotiable, images, status)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [seller.id, '测试商品-订单集成', '数码', '9成新', 100, 50, '校门口', 1,
     JSON.stringify(['https://cos.example.com/p.jpg']), 'active']
  );
  productId = ins.insertId;
});

// ============================================================
// OR-INT-001~010
// ============================================================
describe('POST /api/orders — 创建订单', () => {
  it('OR-INT-001: 买家下单 → 201 + 订单含 product_snapshot', async () => {
    const res = await request(app)
      .post('/api/orders')
      .set(buyerHeaders)
      .send({ product_id: productId });

    expect(res.status).toBe(201);
    expect(res.body.code).toBe(0);
    expect(res.body.data.buyer_id).toBe(buyer.id);
    expect(res.body.data.seller_id).toBe(seller.id);
    expect(res.body.data.status).toBe('pending');
    // orderRepo.create 返回原始 order 行（含 product_snapshot JSON）
    const snapshot = typeof res.body.data.product_snapshot === 'string'
      ? JSON.parse(res.body.data.product_snapshot)
      : res.body.data.product_snapshot;
    expect(snapshot).toBeTruthy();
    expect(snapshot.title).toBe('测试商品-订单集成');

    // 验证商品变为 reserved 状态（数据库层）
    const products = await db.query('SELECT status FROM products WHERE id = ?', [productId]);
    expect(products[0].status).toBe('reserved');
  });

  it('OR-INT-002: 卖家不能买自己的商品 → 409 + 3005 (CANNOT_BUY_OWN)', async () => {
    const res = await request(app)
      .post('/api/orders')
      .set(sellerHeaders) // 卖家自己下单
      .send({ product_id: productId });

    expect(res.status).toBe(409);
    expect(res.body.code).toBe(3005);
  });

  it('OR-INT-003: 未登录下单 → 1001', async () => {
    const res = await request(app)
      .post('/api/orders')
      .send({ product_id: productId });

    expect(res.status).toBe(401);
    expect(res.body.code).toBe(1001);
  });

  it('OR-INT-004: 缺少 product_id → 4001', async () => {
    const res = await request(app)
      .post('/api/orders')
      .set(buyerHeaders)
      .send({});

    expect(res.status).toBe(400);
    expect(res.body.code).toBe(4001);
  });

  it('OR-INT-005: 商品已 locked → 商品不可下单', async () => {
    // 先下一个订单，锁住商品
    await request(app)
      .post('/api/orders')
      .set(buyerHeaders)
      .send({ product_id: productId });

    // 第三人尝试再次下单（商品已 reserved）
    const res = await request(app)
      .post('/api/orders')
      .set(thirdUserHeaders)
      .send({ product_id: productId });

    expect(res.status).toBe(409);
    expect(res.body.code).toBe(3004); // productLocked
  });
});

describe('GET /api/orders — 订单列表', () => {
  let orderId;

  beforeEach(async () => {
    const res = await request(app)
      .post('/api/orders')
      .set(buyerHeaders)
      .send({ product_id: productId });
    orderId = res.body.data.id;
  });

  it('OR-INT-006: 买家角色 → 返回作为买家的订单', async () => {
    const res = await request(app)
      .get('/api/orders?role=buyer')
      .set(buyerHeaders);

    expect(res.status).toBe(200);
    expect(res.body.code).toBe(0);
    expect(res.body.data.list.length).toBeGreaterThanOrEqual(1);
    const order = res.body.data.list.find(o => o.id === orderId);
    expect(order).toBeTruthy();
    expect(order.product_snapshot).toBeTruthy();
  });

  it('OR-INT-007: 卖家角色 → 返回作为卖家的订单', async () => {
    const res = await request(app)
      .get('/api/orders?role=seller')
      .set(sellerHeaders);

    expect(res.status).toBe(200);
    expect(res.body.code).toBe(0);
    expect(res.body.data.list.length).toBeGreaterThanOrEqual(1);
    const order = res.body.data.list.find(o => o.id === orderId);
    expect(order).toBeTruthy();
  });

  it('OR-INT-008: 未登录 → 1001', async () => {
    const res = await request(app).get('/api/orders');

    expect(res.status).toBe(401);
    expect(res.body.code).toBe(1001);
  });
});

describe('GET /api/orders/:id — 订单详情', () => {
  let orderId;

  beforeEach(async () => {
    const res = await request(app)
      .post('/api/orders')
      .set(buyerHeaders)
      .send({ product_id: productId });
    orderId = res.body.data.id;
  });

  it('OR-INT-009: 买家查看自己订单 → 返回完整详情', async () => {
    const res = await request(app)
      .get(`/api/orders/${orderId}`)
      .set(buyerHeaders);

    expect(res.status).toBe(200);
    expect(res.body.code).toBe(0);
    expect(res.body.data.id).toBe(orderId);
    expect(res.body.data.buyer_id).toBe(buyer.id);
    expect(res.body.data.seller_id).toBe(seller.id);
    expect(res.body.data.product_snapshot).toBeTruthy();
  });

  it('OR-INT-010: 第三人查他人订单 → 403 (notOwner)', async () => {
    const res = await request(app)
      .get(`/api/orders/${orderId}`)
      .set(thirdUserHeaders);

    // 非买方/卖方 → 403, code=1005 (notOwner)
    expect(res.status).toBe(403);
    expect(res.body.code).toBe(1005);
  });
});

describe('PUT /api/orders/:id — 订单状态流转', () => {
  let orderId;

  beforeEach(async () => {
    const res = await request(app)
      .post('/api/orders')
      .set(buyerHeaders)
      .send({ product_id: productId });
    orderId = res.body.data.id;
  });

  it('OR-INT-011: 卖家标记面交 → met 状态', async () => {
    const res = await request(app)
      .put(`/api/orders/${orderId}/met`)
      .set(sellerHeaders);

    expect(res.status).toBe(200);
    expect(res.body.code).toBe(0);
    expect(res.body.data.status).toBe('met');

    // 验证 DB
    const orders = await db.query('SELECT status FROM orders WHERE id = ?', [orderId]);
    expect(orders[0].status).toBe('met');
  });

  it('OR-INT-012: 买家确认收货 → completed 状态', async () => {
    // 先标记面交
    await request(app)
      .put(`/api/orders/${orderId}/met`)
      .set(sellerHeaders);

    const res = await request(app)
      .put(`/api/orders/${orderId}/confirm`)
      .set(buyerHeaders);

    expect(res.status).toBe(200);
    expect(res.body.code).toBe(0);
    expect(res.body.data.status).toBe('completed');
  });

  it('OR-INT-013: 非买卖双方标记面交 → 403 (notOwner)', async () => {
    const res = await request(app)
      .put(`/api/orders/${orderId}/met`)
      .set(thirdUserHeaders);

    expect(res.status).toBe(403);
    expect(res.body.code).toBe(1005); // notOwner
  });

  it('OR-INT-014: 取消 pending 订单 → cancelled 状态', async () => {
    const res = await request(app)
      .put(`/api/orders/${orderId}/cancel`)
      .set(buyerHeaders)
      .send({ reason: '不想要了' });

    expect(res.status).toBe(200);
    expect(res.body.code).toBe(0);
    expect(res.body.data.status).toBe('cancelled');

    // 验证商品恢复为 active
    const products = await db.query('SELECT status FROM products WHERE id = ?', [productId]);
    expect(products[0].status).toBe('active');
  });
});

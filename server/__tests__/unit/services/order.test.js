/**
 * 订单服务 — 单元测试（12 条）
 *
 * 被测模块：server/src/services/order.js
 * 覆盖：创建/幂等/权限/信誉分/面交/确认/取消/状态机守卫
 * 测试计划参考：docs/测试计划.md §3.7
 */

const {
  setupTestDb,
  teardownTestDb,
  createTestUser,
  db,
} = require('../../setup');
const orderService = require('../../../src/services/order');

/**
 * 创建测试商品（直接 insert，绕过 service 校验）
 * db.query() 包装了 pool.query：INSERT 返回 ResultSetHeader（不可数组解构）
 */
async function createTestProduct(sellerId, overrides = {}) {
  const ins = await db.query(
    `INSERT INTO products (seller_id, title, description, category, \`condition\`,
      original_price, price, trade_location, negotiable, images, status)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      sellerId,
      overrides.title || '测试商品',
      overrides.description || '这是一本九成新的教材',
      overrides.category || '教材',
      overrides.condition || '9成新',
      overrides.original_price ?? 50.00,
      overrides.price ?? 25.00,
      overrides.trade_location || '图书馆门口',
      overrides.negotiable ?? 1,
      overrides.images || JSON.stringify(['https://cos.example.com/test.jpg']),
      overrides.status || 'active',
    ]
  );
  return ins.insertId;
}

/**
 * 创建测试订单（直接 insert，绕过 service 校验）
 */
async function createTestOrder(buyerId, sellerId, productId, overrides = {}) {
  const idempotentKey = `${buyerId}_${productId}`;
  const snapshot = JSON.stringify({
    title: '快照商品',
    price: 25.00,
    condition: '9成新',
    trade_location: '图书馆门口',
    images: ['https://cos.example.com/test.jpg'],
    seller_id: sellerId,
  });
  const ins = await db.query(
    `INSERT INTO orders (product_id, buyer_id, seller_id, status, idempotent_key, product_snapshot)
     VALUES (?, ?, ?, ?, ?, ?)`,
    [
      productId,
      buyerId,
      sellerId,
      overrides.status || 'pending',
      overrides.idempotentKey || idempotentKey,
      overrides.product_snapshot || snapshot,
    ]
  );
  return ins.insertId;
}

/** 清理数据（按外键依赖倒序） */
async function cleanData() {
  await db.query('DELETE FROM reviews');
  await db.query('DELETE FROM orders');
  await db.query('DELETE FROM products');
  await db.query('DELETE FROM notifications');
  await db.query('DELETE FROM users');
}

// ============================================================
// 文件级 setup/teardown
// ============================================================
beforeAll(async () => {
  await setupTestDb();
});

afterAll(async () => {
  await teardownTestDb();
});

// ============================================================
// 创建订单
// ============================================================
describe('订单服务 — 创建订单', () => {
  let buyer, seller, productId;

  beforeEach(async () => {
    await cleanData();
    buyer = await createTestUser({ nickname: '买家测试', credit_score: 100 });
    seller = await createTestUser({ nickname: '卖家测试', credit_score: 100 });
    productId = await createTestProduct(seller.id, { status: 'active' });
  });

  it('OR-001: 创建订单成功 → 返回 order，status=pending，created=true', async () => {
    const result = await orderService.create(buyer.id, 100, { product_id: productId });

    expect(result.created).toBe(true);
    expect(result.order).toBeTruthy();
    expect(result.order.status).toBe('pending');
    expect(result.order.buyer_id).toBe(buyer.id);
    expect(result.order.seller_id).toBe(seller.id);
    expect(result.order.product_id).toBe(productId);

    // 商品应变为 reserved
    const [product] = await db.query('SELECT status FROM products WHERE id = ?', [productId]);
    expect(product.status).toBe('reserved');
  });

  it('OR-002: 同一买家重复下单同一商品 → 返回已有订单，created=false（幂等）', async () => {
    const first = await orderService.create(buyer.id, 100, { product_id: productId });
    expect(first.created).toBe(true);

    const second = await orderService.create(buyer.id, 100, { product_id: productId });
    expect(second.created).toBe(false);
    expect(second.order.id).toBe(first.order.id);
  });

  it('OR-003: 购买自己的商品 → 抛出 cannotBuyOwn (3005)', async () => {
    await expect(
      orderService.create(seller.id, 100, { product_id: productId })
    ).rejects.toMatchObject({
      code: 3005,
      message: expect.stringContaining('不能购买自己'),
    });
  });

  it('OR-004: 购买已锁定/已售商品 → 抛出 productLocked (3004)', async () => {
    // 先下一个订单锁住商品
    await orderService.create(buyer.id, 100, { product_id: productId });
    // 第二个买家试图购买同一商品
    const buyer2 = await createTestUser({ nickname: '买家2', credit_score: 100 });

    await expect(
      orderService.create(buyer2.id, 100, { product_id: productId })
    ).rejects.toMatchObject({
      code: 3004,
      message: expect.stringContaining('已被他人锁定'),
    });
  });

  it('OR-005: 信誉分 < 30 无法下单 → 抛出 creditTooLowTrade (4009)', async () => {
    const lowCreditBuyer = await createTestUser({ nickname: '低信誉买家', credit_score: 25 });

    await expect(
      orderService.create(lowCreditBuyer.id, 25, { product_id: productId })
    ).rejects.toMatchObject({
      code: 4009,
      message: expect.stringContaining('信誉分不足'),
    });
  });
});

// ============================================================
// 标记面交
// ============================================================
describe('订单服务 — 标记面交', () => {
  let buyer, seller, productId, orderId;

  beforeEach(async () => {
    await cleanData();
    buyer = await createTestUser({ nickname: '买家', credit_score: 100 });
    seller = await createTestUser({ nickname: '卖家', credit_score: 100 });
    productId = await createTestProduct(seller.id, { status: 'active' });
  });

  async function setupPendingOrder() {
    const result = await orderService.create(buyer.id, 100, { product_id: productId });
    return result.order.id;
  }

  it('OR-006: pending→met 成功，返回 met 状态 + met_at 时间', async () => {
    orderId = await setupPendingOrder();

    const updated = await orderService.markAsMet(orderId, seller.id);
    expect(updated.status).toBe('met');
    expect(updated.met_at).toBeTruthy();
  });

  it('OR-007: 非 pending 状态标记面交 → 抛出 orderStateInvalid (3001)', async () => {
    orderId = await setupPendingOrder();
    // 先标记面交
    await orderService.markAsMet(orderId, seller.id);
    // 再次标记
    await expect(
      orderService.markAsMet(orderId, seller.id)
    ).rejects.toMatchObject({
      code: 3001,
      message: expect.stringContaining('仅待面交状态'),
    });
  });
});

// ============================================================
// 确认收货
// ============================================================
describe('订单服务 — 确认收货', () => {
  let buyer, seller, productId, orderId;

  beforeEach(async () => {
    await cleanData();
    buyer = await createTestUser({ nickname: '买家', credit_score: 100 });
    seller = await createTestUser({ nickname: '卖家', credit_score: 100 });
    productId = await createTestProduct(seller.id, { status: 'active' });
  });

  async function setupMetOrder() {
    const result = await orderService.create(buyer.id, 100, { product_id: productId });
    return orderService.markAsMet(result.order.id, seller.id);
  }

  it('OR-008: 买家确认收货 → met→completed，商品变 sold，卖家信誉分+2', async () => {
    const metOrder = await setupMetOrder();

    const confirmed = await orderService.confirm(metOrder.id, buyer.id);
    expect(confirmed.status).toBe('completed');

    // 商品应变为 sold
    const [product] = await db.query('SELECT status FROM products WHERE id = ?', [productId]);
    expect(product.status).toBe('sold');

    // 卖家信誉分应 +2（confirm 中 updateCreditScore 是 fire-and-forget，需短暂等待）
    await new Promise((r) => setTimeout(r, 300));
    const [updatedSeller] = await db.query(
      'SELECT credit_score FROM users WHERE id = ?', [seller.id]
    );
    expect(updatedSeller.credit_score).toBe(102);
  });

  it('OR-009: 非买家确认收货 → 抛出 notOwner (1005)', async () => {
    const metOrder = await setupMetOrder();

    await expect(
      orderService.confirm(metOrder.id, seller.id)
    ).rejects.toMatchObject({
      code: 1005,
      message: expect.stringContaining('没有权限'),
    });
  });
});

// ============================================================
// 取消订单
// ============================================================
describe('订单服务 — 取消订单', () => {
  let buyer, seller, productId;

  beforeEach(async () => {
    await cleanData();
    buyer = await createTestUser({ nickname: '买家', credit_score: 100 });
    seller = await createTestUser({ nickname: '卖家', credit_score: 100 });
    productId = await createTestProduct(seller.id, { status: 'active' });
  });

  async function setupPendingOrder() {
    const result = await orderService.create(buyer.id, 100, { product_id: productId });
    return result.order.id;
  }

  async function setupMetOrder() {
    const result = await orderService.create(buyer.id, 100, { product_id: productId });
    return orderService.markAsMet(result.order.id, seller.id);
  }

  it('OR-010: pending 状态买家取消 → 商品恢复 active，cancelled_by=buyer', async () => {
    const orderId = await setupPendingOrder();

    const cancelled = await orderService.cancel(orderId, buyer.id);
    expect(cancelled.status).toBe('cancelled');
    expect(cancelled.cancelled_by).toBe('buyer');

    // 商品恢复 active
    const [product] = await db.query('SELECT status FROM products WHERE id = ?', [productId]);
    expect(product.status).toBe('active');
  });

  it('OR-011: met 状态卖家取消 → 抛出 orderStateInvalid (3001)', async () => {
    const metOrder = await setupMetOrder();

    await expect(
      orderService.cancel(metOrder.id, seller.id)
    ).rejects.toMatchObject({
      code: 3001,
      message: expect.stringContaining('不可取消'),
    });
  });

  it('OR-012: 已取消订单再次取消 → 抛出 orderStateInvalid (3001)', async () => {
    const orderId = await setupPendingOrder();
    await orderService.cancel(orderId, buyer.id);

    await expect(
      orderService.cancel(orderId, buyer.id)
    ).rejects.toMatchObject({
      code: 3001,
      message: expect.stringContaining('不可取消'),
    });
  });
});

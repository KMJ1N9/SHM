/**
 * 商品服务 — 单元测试（31 条）
 *
 * 被测模块：server/src/services/product.js
 * 覆盖：price ≤ original_price 校验、credit 阈值、敏感词过滤、
 *       图片数量限制、分页、状态守卫、API 响应格式、软删除+TOCTOU
 * 测试计划参考：docs/测试计划.md §3.9 (PV-001~007) + §3.10 (PG-001~005)
 */

const {
  setupTestDb,
  teardownTestDb,
  createTestUser,
  db,
} = require('../../setup');
const productService = require('../../../src/services/product');

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

/** 清理数据 */
async function cleanData() {
  await db.query('DELETE FROM products');
  await db.query('DELETE FROM users');
}

// ============================================================
// 文件级 setup/teardown — 所有 describe 共享一个 DB 初始化
// ============================================================
beforeAll(async () => {
  await setupTestDb();
});

afterAll(async () => {
  await teardownTestDb();
});

// ============================================================
// 校验逻辑
// ============================================================
describe('商品服务 — 校验逻辑', () => {
  let user;

  beforeEach(async () => {
    await cleanData();
    user = await createTestUser({ credit_score: 100 });
  });

  // ---- 价格校验 ----
  describe('价格校验：price > original_price', () => {
    it('发布时售价高于原价应拒绝（4001 badRequest）', async () => {
      await expect(
        productService.create(user.id, 100, {
          title: '测试商品', category: '教材', condition: '9成新',
          original_price: 50, price: 100, trade_location: '图书馆',
          images: ['https://cos.example.com/img.jpg'],
        })
      ).rejects.toMatchObject({
        code: 4001,
        message: expect.stringContaining('售价不能高于原价'),
      });
    });

    it('发布时售价等于原价应允许', async () => {
      const product = await productService.create(user.id, 100, {
        title: '售价等于原价的商品', category: '教材', condition: '9成新',
        original_price: 50, price: 50, trade_location: '图书馆',
        images: ['https://cos.example.com/img.jpg'],
      });
      expect(product).toBeTruthy();
      expect(parseFloat(product.price)).toBe(50);
    });

    it('更新时有效售价高于有效原价应拒绝', async () => {
      const productId = await createTestProduct(user.id, { price: 25, original_price: 50 });
      await expect(
        productService.update(productId, user.id, { price: 60 })
      ).rejects.toMatchObject({
        code: 4001,
        message: expect.stringContaining('售价不能高于原价'),
      });
    });

    it('更新时只改 title 不应触发价格校验失败', async () => {
      const productId = await createTestProduct(user.id, { price: 25, original_price: 50 });
      const updated = await productService.update(productId, user.id, { title: '新标题' });
      expect(updated.title).toBe('新标题');
      expect(parseFloat(updated.price)).toBe(25);
    });
  });

  // ---- 信誉分阈值 ----
  describe('信誉分阈值：credit < 60 禁止发布', () => {
    it('信誉分 59 应拒绝发布（4008 creditTooLowPublish）', async () => {
      await expect(
        productService.create(user.id, 59, {
          title: '测试商品', category: '教材', condition: '9成新',
          original_price: 50, price: 25, trade_location: '图书馆',
          images: ['https://cos.example.com/img.jpg'],
        })
      ).rejects.toMatchObject({ code: 4008 });
    });

    it('信誉分恰好 60 应允许发布', async () => {
      const product = await productService.create(user.id, 60, {
        title: '边界信誉分商品', category: '教材', condition: '9成新',
        original_price: 50, price: 25, trade_location: '图书馆',
        images: ['https://cos.example.com/img.jpg'],
      });
      expect(product).toBeTruthy();
    });
  });

  // ---- 图片数量 ----
  describe('图片数量限制：最多 6 张', () => {
    it('7 张图片应拒绝（4003 tooManyImages）', async () => {
      const seven = Array.from({ length: 7 }, (_, i) => `https://cos.example.com/img${i}.jpg`);
      await expect(
        productService.create(user.id, 100, {
          title: '7 图商品', category: '教材', condition: '9成新',
          original_price: 50, price: 25, trade_location: '图书馆', images: seven,
        })
      ).rejects.toMatchObject({ code: 4003 });
    });

    it('恰好 6 张图片应允许', async () => {
      const six = Array.from({ length: 6 }, (_, i) => `https://cos.example.com/img${i}.jpg`);
      const product = await productService.create(user.id, 100, {
        title: '6 图商品', category: '教材', condition: '9成新',
        original_price: 50, price: 25, trade_location: '图书馆', images: six,
      });
      expect(product).toBeTruthy();
    });
  });

  // ---- 敏感词过滤 ----
  describe('敏感词过滤', () => {
    it('标题含敏感词应拒绝（6002 sensitiveWord）', async () => {
      await expect(
        productService.create(user.id, 100, {
          title: '这是一个诈骗信息', category: '教材', condition: '9成新',
          original_price: 50, price: 25, trade_location: '图书馆',
          images: ['https://cos.example.com/img.jpg'],
        })
      ).rejects.toMatchObject({ code: 6002 });
    });

    it('描述含敏感词应拒绝', async () => {
      await expect(
        productService.create(user.id, 100, {
          title: '正常商品', description: '这是骗子发布的信息',
          category: '教材', condition: '9成新',
          original_price: 50, price: 25, trade_location: '图书馆',
          images: ['https://cos.example.com/img.jpg'],
        })
      ).rejects.toMatchObject({ code: 6002 });
    });

    it('交易地点含敏感词应拒绝', async () => {
      await expect(
        productService.create(user.id, 100, {
          title: '正常商品', category: '教材', condition: '9成新',
          original_price: 50, price: 25, trade_location: '钓鱼网站',
          images: ['https://cos.example.com/img.jpg'],
        })
      ).rejects.toMatchObject({ code: 6002 });
    });
  });

  // ---- 状态守卫 ----
  describe('状态守卫：编辑已终结商品应拒绝', () => {
    it('sold 状态不可编辑', async () => {
      const pid = await createTestProduct(user.id, { status: 'sold' });
      await expect(
        productService.update(pid, user.id, { title: '修改' })
      ).rejects.toMatchObject({ code: 2002 });
    });

    it('frozen 状态不可编辑', async () => {
      const pid = await createTestProduct(user.id, { status: 'frozen' });
      await expect(
        productService.update(pid, user.id, { title: '修改' })
      ).rejects.toMatchObject({ code: 2002 });
    });

    it('deleted 状态不可编辑', async () => {
      const pid = await createTestProduct(user.id, { status: 'deleted' });
      await expect(
        productService.update(pid, user.id, { title: '修改' })
      ).rejects.toMatchObject({ code: 2002 });
    });

    it('off_shelf 状态可以编辑', async () => {
      const pid = await createTestProduct(user.id, { status: 'off_shelf' });
      const updated = await productService.update(pid, user.id, { title: '已修改' });
      expect(updated.title).toBe('已修改');
    });
  });
});

// ============================================================
// 分页
// ============================================================
describe('商品服务 — 分页', () => {
  let user;

  beforeEach(async () => {
    await cleanData();
    user = await createTestUser();
    for (let i = 1; i <= 25; i++) {
      await createTestProduct(user.id, {
        title: `商品${String(i).padStart(2, '0')}`,
        price: i * 10,
      });
    }
  });

  it('page=1, pageSize=20 应返回前 20 条', async () => {
    const result = await productService.list({ page: 1, pageSize: 20 });
    expect(result.list.length).toBe(20);
    expect(result.page).toBe(1);
    expect(result.total).toBe(25);
  });

  it('page=2, pageSize=20 应返回后 5 条', async () => {
    const result = await productService.list({ page: 2, pageSize: 20 });
    expect(result.list.length).toBe(5);
    expect(result.total).toBe(25);
  });

  it('total 应与数据库中全部有效记录数一致', async () => {
    const result = await productService.list({});
    expect(result.total).toBe(25);
    expect(result.list.length).toBe(20);
  });

  it('pageSize=100 应被截断为 ≤ 50', async () => {
    const result = await productService.list({ pageSize: 100 });
    expect(result.pageSize).toBeLessThanOrEqual(50);
  });

  it('page=-5 应容错为 page=1', async () => {
    const result = await productService.list({ page: -5 });
    expect(result.list.length).toBeGreaterThan(0);
  });

  it('page=0 应容错为 page=1', async () => {
    const result = await productService.list({ page: 0 });
    expect(result.list.length).toBeGreaterThan(0);
  });

  it('非数字 page 应容错为 page=1', async () => {
    const result = await productService.list({ page: 'abc' });
    expect(result.list.length).toBeGreaterThan(0);
  });
});

// ============================================================
// API 响应格式
// ============================================================
describe('商品服务 — API 响应格式', () => {
  let user;

  beforeEach(async () => {
    await cleanData();
    user = await createTestUser({ nickname: '卖家昵称' });
  });

  it('list 返回项应含 cover_image', async () => {
    await createTestProduct(user.id, { title: '有图商品' });
    const result = await productService.list({});
    expect(result.list.length).toBeGreaterThan(0);
    expect(result.list[0]).toHaveProperty('cover_image');
  });

  it('list 返回项应含嵌套 seller 对象', async () => {
    await createTestProduct(user.id);
    const result = await productService.list({});
    expect(result.list[0]).toHaveProperty('seller');
    expect(result.list[0].seller).toHaveProperty('nickname');
  });

  it('detail 应返回完整 seller 信息', async () => {
    const pid = await createTestProduct(user.id);
    const product = await productService.detail(pid);
    expect(product.seller).toBeTruthy();
    expect(product.seller).toHaveProperty('id', user.id);
  });

  it('findBySeller 应保持 seller: null 结构 + cover_image', async () => {
    await createTestProduct(user.id, { title: '我的商品1' });
    await createTestProduct(user.id, { title: '我的商品2' });
    const result = await productService.findBySeller(user.id, {});
    expect(result.list.length).toBe(2);
    result.list.forEach((item) => {
      expect(item).toHaveProperty('seller');
      expect(item.seller).toBeNull();
      expect(item).toHaveProperty('cover_image');
    });
  });
});

// ============================================================
// 删除（软删除 + TOCTOU 防护）
// ============================================================
describe('商品服务 — 删除（软删除 + TOCTOU 防护）', () => {
  let user, otherUser;

  beforeEach(async () => {
    await cleanData();
    user = await createTestUser();
    otherUser = await createTestUser();
  });

  it('active 状态商品可被卖家删除', async () => {
    const pid = await createTestProduct(user.id, { status: 'active' });
    await expect(productService.delete(pid, user.id)).resolves.toBeUndefined();
    // SELECT 返回 rows[]
    const rows = await db.query('SELECT status FROM products WHERE id = ?', [pid]);
    expect(rows[0].status).toBe('deleted');
  });

  it('非卖家不可删除（1005 notOwner）', async () => {
    const pid = await createTestProduct(user.id, { status: 'active' });
    await expect(
      productService.delete(pid, otherUser.id)
    ).rejects.toMatchObject({ code: 1005 });
  });

  it('非 active 状态不可删除（2002 invalidStatus）', async () => {
    const pid = await createTestProduct(user.id, { status: 'sold' });
    await expect(
      productService.delete(pid, user.id)
    ).rejects.toMatchObject({ code: 2002 });
  });

  it('不存在的商品删除应抛 notFound（2001）', async () => {
    await expect(
      productService.delete(99999, user.id)
    ).rejects.toMatchObject({ code: 2001 });
  });
});

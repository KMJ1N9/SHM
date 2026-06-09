/**
 * 搜索 + 筛选 — 集成测试（7 条）
 *
 * 测试范围：supertest + 真实路由 + MySQL 测试数据库
 * 测试计划参考：docs/测试计划.md §4.5 — SE-001 ~ SE-003
 * 扩展覆盖：卖家昵称搜索、排序、组合筛选
 */

const request = require('supertest');
const {
  setupTestDb,
  teardownTestDb,
  createTestUser,
  authHeader,
  db,
} = require('../setup');

// 延迟 require app：确保 .env.test 已被 setup.js 加载
let app;
let seller1, seller2;
let seller1Headers;

beforeAll(async () => {
  await setupTestDb();

  seller1 = await createTestUser({ nickname: '计算机学长' });
  seller2 = await createTestUser({ nickname: '数码达人' });

  seller1Headers = authHeader(seller1);

  // 加载 app（在 DB 就绪后）
  app = require('../../src/app');
});

afterAll(async () => {
  await teardownTestDb();
});

beforeEach(async () => {
  // 清理业务数据
  await db.query('DELETE FROM products');
});

/**
 * 辅助：创建测试商品
 */
async function seedProducts() {
  const products = [
    [seller1.id, '高等数学第七版', '同济大学版，笔记整洁', '教材', '9成新', 50, 25, '图书馆门口', 1,
      JSON.stringify(['https://cos.example.com/math1.jpg']), 'active'],
    [seller1.id, '机械键盘 Cherry', 'Cherry MX 青轴，手感清脆', '电子产品', '95新', 500, 200, '宿舍楼下', 1,
      JSON.stringify(['https://cos.example.com/kb1.jpg']), 'active'],
    [seller1.id, '数据结构算法导论', '经典教材，几乎全新', '教材', '9成新', 60, 30, '教学楼A', 1,
      JSON.stringify(['https://cos.example.com/algo1.jpg']), 'active'],
    [seller2.id, 'iPhone 14 Pro 256G', '国行，无划痕，电池健康 95%', '电子产品', '95新', 7000, 5000, '校门口', 1,
      JSON.stringify(['https://cos.example.com/ip1.jpg']), 'active'],
    [seller2.id, '篮球 Nike 室外', '耐磨室外用球', '运动户外', '8成新', 200, 50, '操场', 1,
      JSON.stringify(['https://cos.example.com/bball1.jpg']), 'active'],
    [seller2.id, 'iPad Air 5', 'M1 芯片，几乎全新', '电子产品', '9成新', 4000, 3000, '图书馆门口', 0,
      JSON.stringify(['https://cos.example.com/ipad1.jpg']), 'active'],
  ];
  for (const p of products) {
    await db.query(
      `INSERT INTO products (seller_id, title, description, category, \`condition\`,
        original_price, price, trade_location, negotiable, images, status)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      p
    );
  }
}

// ============================================================
// 关键词搜索
// ============================================================
describe('GET /api/products?keyword= — 关键词搜索', () => {
  beforeEach(async () => {
    await seedProducts();
  });

  // ---- SE-001: 按商品名搜索 ----
  it('SE-001: 按商品名搜索应返回匹配结果', async () => {
    const res = await request(app)
      .get('/api/products?keyword=高等数学')
      .set(seller1Headers);

    expect(res.status).toBe(200);
    expect(res.body.code).toBe(0);
    expect(res.body.data.list.length).toBeGreaterThanOrEqual(1);
    expect(res.body.data.list.some((p) => p.title.includes('高等数学'))).toBe(true);
  });

  // ---- SE-003: 无匹配结果 ----
  it('SE-003: 无匹配结果应返回空数组', async () => {
    const res = await request(app)
      .get('/api/products?keyword=不存在的商品名称XYZ')
      .set(seller1Headers);

    expect(res.status).toBe(200);
    expect(res.body.code).toBe(0);
    expect(res.body.data.list).toEqual([]);
    expect(res.body.data.total).toBe(0);
  });

  // ---- 卖家昵称搜索 ----
  it('按卖家昵称搜索应返回该卖家的商品', async () => {
    const res = await request(app)
      .get('/api/products?keyword=数码达人')
      .set(seller1Headers);

    expect(res.status).toBe(200);
    expect(res.body.code).toBe(0);
    expect(res.body.data.list.length).toBeGreaterThanOrEqual(1);
    res.body.data.list.forEach((p) => {
      expect(p.seller.nickname).toBe('数码达人');
    });
  });

  // ---- 分类名搜索 ----
  it('按分类名搜索应返回该分类的商品', async () => {
    const res = await request(app)
      .get('/api/products?keyword=运动户外')
      .set(seller1Headers);

    expect(res.status).toBe(200);
    expect(res.body.code).toBe(0);
    expect(res.body.data.list.length).toBeGreaterThanOrEqual(1);
    expect(res.body.data.list.every((p) => p.category === '运动户外')).toBe(true);
  });

  // ---- 描述搜索 ----
  it('搜索描述中的词应返回对应商品', async () => {
    const res = await request(app)
      .get('/api/products?keyword=Cherry')
      .set(seller1Headers);

    expect(res.status).toBe(200);
    expect(res.body.data.list.length).toBeGreaterThanOrEqual(1);
    // Cherry 出现在机械键盘的 description 中
    expect(res.body.data.list.some((p) => p.title.includes('机械键盘'))).toBe(true);
  });
});

// ============================================================
// 组合筛选
// ============================================================
describe('GET /api/products? — 组合筛选', () => {
  beforeEach(async () => {
    await seedProducts();
  });

  // ---- SE-002: 分类 + 价格区间 ----
  it('SE-002: 分类 + 价格区间组合筛选', async () => {
    const res = await request(app)
      .get('/api/products?category=电子产品&priceMin=1000&priceMax=4000')
      .set(seller1Headers);

    expect(res.status).toBe(200);
    expect(res.body.code).toBe(0);
    expect(res.body.data.list.length).toBeGreaterThanOrEqual(1);
    res.body.data.list.forEach((p) => {
      expect(p.category).toBe('电子产品');
      expect(parseFloat(p.price)).toBeGreaterThanOrEqual(1000);
      expect(parseFloat(p.price)).toBeLessThanOrEqual(4000);
    });
  });

  // ---- keyword + category + priceRange + sort ----
  it('keyword + category + priceMax + sort=priceAsc 全组合', async () => {
    const res = await request(app)
      .get('/api/products?keyword=Pro&category=电子产品&priceMax=6000&sort=priceAsc')
      .set(seller1Headers);

    expect(res.status).toBe(200);
    expect(res.body.code).toBe(0);
    expect(res.body.data.list.length).toBe(1);
    expect(res.body.data.list[0].title).toBe('iPhone 14 Pro 256G');
    expect(parseFloat(res.body.data.list[0].price)).toBe(5000);
  });
});

// ============================================================
// 排序
// ============================================================
describe('GET /api/products?sort= — 排序', () => {
  beforeEach(async () => {
    // 创建价格不同的商品
    await db.query(
      `INSERT INTO products (seller_id, title, description, category, \`condition\`,
        original_price, price, trade_location, negotiable, images, status)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [seller1.id, '便宜商品', '', '教材', '8成新', 20, 10, '图书馆', 1, '[]', 'active']
    );
    await db.query(
      `INSERT INTO products (seller_id, title, description, category, \`condition\`,
        original_price, price, trade_location, negotiable, images, status)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [seller1.id, '中等商品', '', '教材', '9成新', 100, 50, '图书馆', 1, '[]', 'active']
    );
    await db.query(
      `INSERT INTO products (seller_id, title, description, category, \`condition\`,
        original_price, price, trade_location, negotiable, images, status)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [seller1.id, '昂贵商品', '', '教材', '95新', 200, 100, '图书馆', 1, '[]', 'active']
    );
  });

  it('sort=priceAsc 应按价格升序', async () => {
    const res = await request(app)
      .get('/api/products?sort=priceAsc')
      .set(seller1Headers);

    expect(res.status).toBe(200);
    const prices = res.body.data.list.map((p) => parseFloat(p.price));
    for (let i = 1; i < prices.length; i++) {
      expect(prices[i]).toBeGreaterThanOrEqual(prices[i - 1]);
    }
  });

  it('sort=priceDesc 应按价格降序', async () => {
    const res = await request(app)
      .get('/api/products?sort=priceDesc')
      .set(seller1Headers);

    expect(res.status).toBe(200);
    const prices = res.body.data.list.map((p) => parseFloat(p.price));
    for (let i = 1; i < prices.length; i++) {
      expect(prices[i]).toBeLessThanOrEqual(prices[i - 1]);
    }
  });
});

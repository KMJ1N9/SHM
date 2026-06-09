/**
 * 搜索 + 筛选 — 单元测试（7 条）
 *
 * 被测模块：server/src/services/product.js → list()
 * 覆盖：FULLTEXT 搜索、LIKE 兜底、SQL 特殊字符、卖家昵称/分类匹配、空关键词
 * 测试计划参考：docs/测试计划.md §3.12 — SH-001 ~ SH-005
 */

const {
  setupTestDb,
  teardownTestDb,
  createTestUser,
  db,
} = require('../../setup');
const productService = require('../../../src/services/product');

/**
 * 创建测试商品
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

/** 清理业务数据 */
async function cleanData() {
  await db.query('DELETE FROM products');
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
// 关键词搜索
// ============================================================
describe('搜索服务 — 关键词搜索', () => {
  let seller;

  beforeEach(async () => {
    await cleanData();
    seller = await createTestUser({ nickname: '计算机学长' });
    // 创建多个用途各异的商品
    await createTestProduct(seller.id, {
      title: '高等数学第七版',
      description: '同济大学版，笔记整洁',
      category: '教材',
    });
    await createTestProduct(seller.id, {
      title: '机械键盘青轴',
      description: 'Cherry MX 青轴，手感清脆',
      category: '电子产品',
    });
    await createTestProduct(seller.id, {
      title: '数据结构算法导论',
      description: '经典教材，几乎全新',
      category: '教材',
    });
    await createTestProduct(seller.id, {
      title: 'iPhone 14 手机壳',
      description: '硅胶材质，手感好',
      category: '电子产品',
    });
    await createTestProduct(seller.id, {
      title: '篮球 Nike',
      description: '室外用球，耐磨',
      category: '运动户外',
    });
  });

  // ---- SH-001: FULLTEXT MATCH 基本搜索 ----
  it('SH-001: FULLTEXT 搜索应返回标题匹配的商品', async () => {
    const result = await productService.list({ keyword: '高等数学' });

    expect(result.list.length).toBeGreaterThanOrEqual(1);
    expect(result.list.some((p) => p.title.includes('高等数学'))).toBe(true);
  });

  // ---- SH-002: 无匹配结果返回空数组 ----
  it('SH-002: 无匹配结果应返回空数组，不抛异常', async () => {
    const result = await productService.list({ keyword: '不存在的商品XYZ' });

    expect(result.list).toEqual([]);
    expect(result.total).toBe(0);
  });

  // ---- SH-003: SQL 特殊字符不触发错误 ----
  it("SH-003: SQL 特殊字符（引号、反斜杠）不应触发错误", async () => {
    await expect(
      productService.list({ keyword: "O'Brien\"s \\test" })
    ).resolves.toBeDefined();
  });

  // ---- SH-004: LIKE fallback（描述匹配） ----
  it('SH-004: 关键词应能匹配描述中的词（LIKE fallback）', async () => {
    const result = await productService.list({ keyword: 'Cherry' });

    expect(result.list.length).toBeGreaterThanOrEqual(1);
    // "Cherry MX 青轴" 出现在机械键盘的 description 中
    expect(result.list.some((p) => p.title.includes('机械键盘'))).toBe(true);
  });

  // ---- SH-005: 空关键词不过滤 ----
  it('SH-005: 空关键词应返回全部商品（等价于无 keyword 参数）', async () => {
    const result = await productService.list({ keyword: '' });

    expect(result.list.length).toBe(5);
    expect(result.total).toBe(5);
  });

  // ---- 卖家昵称搜索 ----
  it('按卖家昵称搜索应返回该卖家的商品', async () => {
    const result = await productService.list({ keyword: '计算机学长' });

    expect(result.list.length).toBeGreaterThanOrEqual(1);
    result.list.forEach((p) => {
      expect(p.seller.nickname).toBe('计算机学长');
    });
  });

  // ---- 分类名搜索（分类名出现在 keyword 匹配中） ----
  it('按分类名搜索应返回该分类的商品', async () => {
    const result = await productService.list({ keyword: '运动户外' });

    expect(result.list.length).toBeGreaterThanOrEqual(1);
    expect(result.list.some((p) => p.category === '运动户外')).toBe(true);
  });
});

// ============================================================
// 组合筛选
// ============================================================
describe('搜索服务 — 组合筛选', () => {
  let seller;

  beforeEach(async () => {
    await cleanData();
    seller = await createTestUser({ nickname: '数码达人' });
    await createTestProduct(seller.id, {
      title: 'iPhone 14 Pro',
      category: '电子产品',
      condition: '95新',
      price: 5000,
    });
    await createTestProduct(seller.id, {
      title: 'iPad Air',
      category: '电子产品',
      condition: '全新',
      price: 3000,
    });
    await createTestProduct(seller.id, {
      title: '高等数学',
      category: '教材',
      condition: '8成新',
      price: 15,
    });
    await createTestProduct(seller.id, {
      title: 'MacBook Pro',
      category: '电子产品',
      condition: '9成新',
      price: 8000,
    });
  });

  it('keyword + category 组合筛选应同时满足两个条件', async () => {
    const result = await productService.list({
      keyword: 'iPhone',
      category: '电子产品',
    });

    expect(result.list.length).toBe(1);
    expect(result.list[0].title).toBe('iPhone 14 Pro');
  });

  it('category + priceMin + priceMax 组合筛选', async () => {
    const result = await productService.list({
      category: '电子产品',
      priceMin: 2000,
      priceMax: 6000,
    });

    expect(result.list.length).toBeGreaterThanOrEqual(1);
    result.list.forEach((p) => {
      expect(p.category).toBe('电子产品');
      expect(parseFloat(p.price)).toBeGreaterThanOrEqual(2000);
      expect(parseFloat(p.price)).toBeLessThanOrEqual(6000);
    });
  });

  it('keyword + condition 组合筛选', async () => {
    const result = await productService.list({
      keyword: 'MacBook',
      condition: '9成新',
    });

    expect(result.list.length).toBe(1);
    expect(result.list[0].title).toBe('MacBook Pro');
    expect(result.list[0].condition).toBe('9成新');
  });

  it('全条件组合：keyword + category + condition + priceRange', async () => {
    const result = await productService.list({
      keyword: 'Pro',
      category: '电子产品',
      condition: '95新',
      priceMin: 4000,
      priceMax: 6000,
    });

    expect(result.list.length).toBe(1);
    expect(result.list[0].title).toBe('iPhone 14 Pro');
  });
});

// ============================================================
// 排序
// ============================================================
describe('搜索服务 — 排序', () => {
  let seller;

  beforeEach(async () => {
    await cleanData();
    seller = await createTestUser();
    await createTestProduct(seller.id, { title: '便宜商品', price: 10 });
    // 间隔 1.1s 确保 created_at 不同
    await new Promise((r) => setTimeout(r, 1100));
    await createTestProduct(seller.id, { title: '中等商品', price: 50 });
    await new Promise((r) => setTimeout(r, 1100));
    await createTestProduct(seller.id, { title: '昂贵商品', price: 100 });
  });

  it('sort=priceAsc 应按价格升序排列', async () => {
    const result = await productService.list({ sort: 'priceAsc' });

    expect(result.list.length).toBe(3);
    const prices = result.list.map((p) => parseFloat(p.price));
    for (let i = 1; i < prices.length; i++) {
      expect(prices[i]).toBeGreaterThanOrEqual(prices[i - 1]);
    }
  });

  it('sort=priceDesc 应按价格降序排列', async () => {
    const result = await productService.list({ sort: 'priceDesc' });

    expect(result.list.length).toBe(3);
    const prices = result.list.map((p) => parseFloat(p.price));
    for (let i = 1; i < prices.length; i++) {
      expect(prices[i]).toBeLessThanOrEqual(prices[i - 1]);
    }
  });

  it('sort=latest 应按时间降序排列（默认）', async () => {
    const result = await productService.list({ sort: 'latest' });

    expect(result.list.length).toBe(3);
    // 验证按 id 降序（id 越大表示越新插入），等价于时间降序
    const ids = result.list.map((p) => p.id);
    for (let i = 1; i < ids.length; i++) {
      expect(ids[i]).toBeLessThan(ids[i - 1]);
    }
  });
});

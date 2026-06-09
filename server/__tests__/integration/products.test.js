/**
 * 商品 API — 集成测试（8 条）
 *
 * 测试范围：supertest + 真实路由 + MySQL 测试数据库
 * 测试计划参考：docs/测试计划.md §4.2 — PR-001 ~ PR-005
 * 扩展覆盖：编辑商品、删除商品、列表筛选
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
let user, seller, lowCreditUser;
let authHeaders, sellerHeaders;

beforeAll(async () => {
  await setupTestDb();

  // 创建测试用户
  user = await createTestUser({ nickname: '买家测试', credit_score: 100 });
  seller = await createTestUser({ nickname: '卖家测试', credit_score: 100 });
  lowCreditUser = await createTestUser({ nickname: '低信誉用户', credit_score: 50 });

  authHeaders = authHeader(user);
  sellerHeaders = authHeader(seller);

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

describe('POST /api/products — 发布商品', () => {
  // ---- PR-001: 完整字段发布 → 201 ----
  it('PR-001: 完整字段发布应返回 201 + cover_image + seller 嵌套', async () => {
    const res = await request(app)
      .post('/api/products')
      .set(sellerHeaders)
      .send({
        title: '高等数学第七版',
        description: '九成新，笔记整洁',
        category: '教材',
        condition: '9成新',
        original_price: 50,
        price: 25,
        trade_location: '图书馆门口',
        negotiable: true,
        images: [
          'https://cos.example.com/img1.jpg',
          'https://cos.example.com/img2.jpg',
        ],
      });

    expect(res.status).toBe(201);
    expect(res.body.code).toBe(0);
    expect(res.body.data).toMatchObject({
      title: '高等数学第七版',
      status: 'active',
    });
    expect(parseFloat(res.body.data.price)).toBe(25);
    expect(parseFloat(res.body.data.original_price)).toBe(50);
    expect(res.body.data.cover_image).toBeUndefined(); // detail/create 不返回 cover_image
    expect(res.body.data.seller).toBeTruthy();
    expect(res.body.data.seller.nickname).toBe('卖家测试');

    // 验证数据库状态（SELECT 返回 rows[]）
    const rows = await db.query('SELECT status, images FROM products WHERE id = ?', [res.body.data.id]);
    expect(rows[0].status).toBe('active');
    // mysql2 自动解析 JSON 列，已经是数组
    expect(rows[0].images.length).toBe(2);
  });

  // ---- PR-002: 缺必填字段 → 4001 ----
  it('PR-002: 缺必填字段应返回 4001', async () => {
    const res = await request(app)
      .post('/api/products')
      .set(sellerHeaders)
      .send({
        // 故意缺 title, category, condition, original_price, price, trade_location, images
      });

    expect(res.status).toBe(400);
    expect(res.body.code).toBe(4001);
  });

  // ---- PR-003: 信誉分 < 60 发布被拒 ----
  it('PR-003: 信誉分 < 60 发布应返回 4008', async () => {
    const lowAuthHeaders = authHeader(lowCreditUser);
    const res = await request(app)
      .post('/api/products')
      .set(lowAuthHeaders)
      .send({
        title: '低信誉测试商品',
        category: '教材',
        condition: '9成新',
        original_price: 50,
        price: 25,
        trade_location: '图书馆',
        images: ['https://cos.example.com/img.jpg'],
      });

    expect(res.status).toBe(403);
    expect(res.body.code).toBe(4008);
  });

  // ---- PR-004: 未登录发布 → 401 ----
  it('PR-004: 未登录发布应返回 1001', async () => {
    const res = await request(app)
      .post('/api/products')
      .send({
        title: '未登录商品',
        category: '教材',
        condition: '9成新',
        original_price: 50,
        price: 25,
        trade_location: '图书馆',
        images: ['https://cos.example.com/img.jpg'],
      });

    expect(res.status).toBe(401);
    expect(res.body.code).toBe(1001);
  });

  // ---- 敏感词拦截 ----
  it('标题含敏感词应返回 6002', async () => {
    const res = await request(app)
      .post('/api/products')
      .set(sellerHeaders)
      .send({
        title: '诈骗信息',
        category: '教材',
        condition: '9成新',
        original_price: 50,
        price: 25,
        trade_location: '图书馆',
        images: ['https://cos.example.com/img.jpg'],
      });

    expect(res.status).toBe(400);
    expect(res.body.code).toBe(6002);
  });
});

describe('GET /api/products — 商品列表', () => {
  beforeEach(async () => {
    // 创建 3 个测试商品
    for (let i = 1; i <= 3; i++) {
      // INSERT 返回 ResultSetHeader（不可数组解构）
      await db.query(
        `INSERT INTO products (seller_id, title, category, \`condition\`,
          original_price, price, trade_location, negotiable, images, status)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [seller.id, `商品${i}`, '教材', '9成新', 50, 25 + i, '图书馆', 1,
         JSON.stringify([`https://cos.example.com/img${i}.jpg`]), 'active']
      );
    }
  });

  it('应返回商品列表含 seller 嵌套和 cover_image', async () => {
    const res = await request(app).get('/api/products').set(sellerHeaders);

    expect(res.status).toBe(200);
    expect(res.body.code).toBe(0);
    expect(res.body.data.list.length).toBeGreaterThanOrEqual(3);
    expect(res.body.data.total).toBeGreaterThanOrEqual(3);
    expect(res.body.data.list[0]).toHaveProperty('cover_image');
    expect(res.body.data.list[0]).toHaveProperty('seller');
  });

  it('应支持分类筛选', async () => {
    const res = await request(app).get('/api/products?category=教材').set(sellerHeaders);

    expect(res.status).toBe(200);
    expect(res.body.data.list.length).toBeGreaterThanOrEqual(3);
  });

  it('不存在的分类应返回空列表', async () => {
    const res = await request(app).get('/api/products?category=不存在的分类').set(sellerHeaders);

    expect(res.status).toBe(200);
    expect(res.body.data.list).toEqual([]);
    expect(res.body.data.total).toBe(0);
  });

  it('应支持分页 pageSize=1', async () => {
    const res = await request(app).get('/api/products?pageSize=1').set(sellerHeaders);

    expect(res.status).toBe(200);
    expect(res.body.data.list.length).toBe(1);
  });
});

describe('GET /api/products/:id — 商品详情', () => {
  let productId;

  beforeEach(async () => {
    // INSERT 返回 ResultSetHeader（不可数组解构）
    const ins = await db.query(
      `INSERT INTO products (seller_id, title, category, \`condition\`,
        original_price, price, trade_location, negotiable, images, status)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [seller.id, '详情测试商品', '数码', '95新', 2000, 1000, '校门口', 1,
       JSON.stringify(['https://cos.example.com/detail1.jpg', 'https://cos.example.com/detail2.jpg']), 'active']
    );
    productId = ins.insertId;
  });

  it('应返回完整商品信息含 seller 嵌套', async () => {
    const res = await request(app).get(`/api/products/${productId}`).set(sellerHeaders);

    expect(res.status).toBe(200);
    expect(res.body.code).toBe(0);
    expect(res.body.data.title).toBe('详情测试商品');
    expect(parseFloat(res.body.data.price)).toBe(1000);
    expect(parseFloat(res.body.data.original_price)).toBe(2000);
    expect(res.body.data.seller).toBeTruthy();
    expect(res.body.data.seller.nickname).toBe('卖家测试');
  });

  it('不存在的商品应返回 404', async () => {
    const res = await request(app).get('/api/products/99999').set(sellerHeaders);

    expect(res.status).toBe(404);
    expect(res.body.code).toBe(2001);
  });

  // off_shelf 商品仅卖家和 admin 可见
  it('off_shelf 商品对非卖家不可见', async () => {
    await db.query('UPDATE products SET status = ? WHERE id = ?', ['off_shelf', productId]);

    const res = await request(app)
      .get(`/api/products/${productId}`)
      .set(authHeaders); // user 不是卖家

    expect(res.status).toBe(404);
    expect(res.body.code).toBe(2001);
  });

  it('off_shelf 商品对卖家本人可见', async () => {
    await db.query('UPDATE products SET status = ? WHERE id = ?', ['off_shelf', productId]);

    const res = await request(app)
      .get(`/api/products/${productId}`)
      .set(sellerHeaders);

    expect(res.status).toBe(200);
    expect(res.body.data.status).toBe('off_shelf');
  });
});

describe('PUT /api/products/:id — 编辑商品', () => {
  let productId;

  beforeEach(async () => {
    // INSERT 返回 ResultSetHeader（不可数组解构）
    const ins = await db.query(
      `INSERT INTO products (seller_id, title, category, \`condition\`,
        original_price, price, trade_location, negotiable, images, status)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [seller.id, '待编辑商品', '数码', '95新', 2000, 1000, '校门口', 1, '[]', 'active']
    );
    productId = ins.insertId;
  });

  it('卖家编辑成功应返回更新后数据', async () => {
    const res = await request(app)
      .put(`/api/products/${productId}`)
      .set(sellerHeaders)
      .send({ title: '编辑后的标题', price: 800 });

    expect(res.status).toBe(200);
    expect(res.body.data.title).toBe('编辑后的标题');
    expect(parseFloat(res.body.data.price)).toBe(800);
  });

  it('非卖家编辑应返回 1005（notOwner）', async () => {
    const res = await request(app)
      .put(`/api/products/${productId}`)
      .set(authHeaders)
      .send({ title: '恶意修改' });

    expect(res.status).toBe(403);
    expect(res.body.code).toBe(1005);
  });
});

describe('DELETE /api/products/:id — 删除商品', () => {
  let productId;

  beforeEach(async () => {
    // INSERT 返回 ResultSetHeader（不可数组解构）
    const ins = await db.query(
      `INSERT INTO products (seller_id, title, category, \`condition\`,
        original_price, price, trade_location, negotiable, images, status)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [seller.id, '待删除商品', '数码', '95新', 2000, 1000, '校门口', 1, '[]', 'active']
    );
    productId = ins.insertId;
  });

  it('卖家删除成功应返回 code=0', async () => {
    const res = await request(app)
      .delete(`/api/products/${productId}`)
      .set(sellerHeaders);

    expect(res.status).toBe(200);
    expect(res.body.code).toBe(0);

    // 验证软删除（SELECT 返回 rows[]）
    const delRows = await db.query('SELECT status FROM products WHERE id = ?', [productId]);
    expect(delRows[0].status).toBe('deleted');
  });
});

describe('GET /api/products/my — 我的发布', () => {
  beforeEach(async () => {
    // 创建 seller 的 3 个商品
    for (let i = 1; i <= 3; i++) {
      await db.query(
        `INSERT INTO products (seller_id, title, category, \`condition\`,
          original_price, price, trade_location, negotiable, images, status)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        [seller.id, `我的商品${i}`, '数码', '95新', 2000, 1000, '校门口', 1, '[]', 'active']
      );
    }
    // 创建 user 的 1 个商品（不应出现在 seller 的"我的发布"中）
    await db.query(
      `INSERT INTO products (seller_id, title, category, \`condition\`,
        original_price, price, trade_location, negotiable, images, status)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [user.id, '别人的商品', '数码', '95新', 2000, 1000, '校门口', 1, '[]', 'active']
    );
  });

  it('应只返回当前用户的商品', async () => {
    const res = await request(app)
      .get('/api/products/my')
      .set(sellerHeaders);

    expect(res.status).toBe(200);
    expect(res.body.code).toBe(0);
    expect(res.body.data.list.length).toBe(3);
    res.body.data.list.forEach((item) => {
      expect(item).toHaveProperty('cover_image');
    });
  });

  it('应支持 status 筛选', async () => {
    // 修改一个商品为 sold
    await db.query('UPDATE products SET status = ? WHERE seller_id = ? LIMIT 1', ['sold', seller.id]);

    const res = await request(app)
      .get('/api/products/my?status=sold')
      .set(sellerHeaders);

    expect(res.status).toBe(200);
    expect(res.body.data.list.length).toBe(1);
    expect(res.body.data.list[0].status).toBe('sold');
  });

  it('未登录应返回 1001', async () => {
    const res = await request(app).get('/api/products/my');

    expect(res.status).toBe(401);
    expect(res.body.code).toBe(1001);
  });
});

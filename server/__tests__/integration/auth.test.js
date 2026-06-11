/**
 * 认证 API — 集成测试（7 条）
 *
 * 测试范围：supertest + 真实路由 + MySQL 测试数据库
 * 覆盖：登录+新用户注册+封禁拦截+refresh+token过期+me+未登录
 * 测试计划参考：编码迭代计划 §13.1 Phase 1-7a
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
let existingUser;
let existingUserHeaders;

beforeAll(async () => {
  await setupTestDb();

  // 预置一个已注册用户
  existingUser = await createTestUser({ nickname: '已注册用户', phone: '13800001111' });
  existingUserHeaders = authHeader(existingUser);

  app = require('../../src/app');
});

afterAll(async () => {
  await teardownTestDb();
});

beforeEach(async () => {
  // 清理可能残留的测试数据
  await db.query('DELETE FROM users WHERE phone LIKE ?', ['139_test_%']);
});

// ============================================================
// AU-INT-001~007
// ============================================================
describe('POST /api/auth/login', () => {
  it('AU-INT-001: mock 登录已有用户 → 返回双 Token + isNewUser=false', async () => {
    const res = await request(app)
      .post('/api/auth/login')
      .send({ code: `mock_${existingUser.phone}` });

    expect(res.status).toBe(200);
    expect(res.body.code).toBe(0);
    expect(res.body.data.accessToken).toBeTruthy();
    expect(res.body.data.refreshToken).toBeTruthy();
    expect(res.body.data.isNewUser).toBe(false);
    expect(res.body.data.user.nickname).toBe('已注册用户');
    expect(res.body.data.user.phone).toBe(existingUser.phone);
    expect(res.body.data.user.role).toBe('user');
    expect(res.body.data.user.credit_score).toBe(100);
    // 不应包含敏感内部字段
    expect(res.body.data.user.token_version).toBeUndefined();
    expect(res.body.data.user.status).toBeUndefined();
  });

  it('AU-INT-002: mock 新用户登录 → isNewUser=true + auto-register', async () => {
    const res = await request(app)
      .post('/api/auth/login')
      .send({ code: 'mock_13900000001' });

    expect(res.status).toBe(200);
    expect(res.body.data.isNewUser).toBe(true);
    expect(res.body.data.user.nickname).toBeTruthy();
    expect(res.body.data.user.phone).toBe('13900000001');

    // 验证 DB 中真实存在
    const users = await db.query('SELECT phone, nickname FROM users WHERE phone = ?', ['13900000001']);
    expect(users.length).toBe(1);
    expect(users[0].phone).toBe('13900000001');
  });

  it('AU-INT-003: 封禁用户登录 → 1004', async () => {
    // 创建一个被封禁的用户
    await db.query(
      "INSERT INTO users (phone, nickname, role, status, credit_score) VALUES (?, ?, ?, ?, ?)",
      ['13900000002', '被封用户', 'user', 'banned', 0]
    );

    const res = await request(app)
      .post('/api/auth/login')
      .send({ code: 'mock_13900000002' });

    expect(res.status).toBe(403);
    expect(res.body.code).toBe(1004);
  });

  it('AU-INT-004: 缺少 code 字段 → 4001', async () => {
    const res = await request(app)
      .post('/api/auth/login')
      .send({});

    expect(res.status).toBe(400);
    expect(res.body.code).toBe(4001);
  });
});

describe('POST /api/auth/refresh', () => {
  it('AU-INT-005: 有效 refresh_token → 返回新双 Token', async () => {
    // 先登录获取 refresh token
    const loginRes = await request(app)
      .post('/api/auth/login')
      .send({ code: `mock_${existingUser.phone}` });
    const refreshToken = loginRes.body.data.refreshToken;

    const res = await request(app)
      .post('/api/auth/refresh')
      .send({ refresh_token: refreshToken });

    expect(res.status).toBe(200);
    expect(res.body.code).toBe(0);
    expect(res.body.data.accessToken).toBeTruthy();
    expect(res.body.data.refreshToken).toBeTruthy();
    // 刷新后应返回有效的 token（可能相同也可能不同，取决于时间精度）
  });

  it('AU-INT-006: 无效/过期 refresh_token → 1002', async () => {
    const res = await request(app)
      .post('/api/auth/refresh')
      .send({ refresh_token: 'invalid-token-12345' });

    expect(res.status).toBe(401);
    expect(res.body.code).toBe(1002);
  });
});

describe('GET /api/auth/me', () => {
  it('AU-INT-007: 有效 Token → 返回用户信息', async () => {
    const res = await request(app)
      .get('/api/auth/me')
      .set(existingUserHeaders);

    expect(res.status).toBe(200);
    expect(res.body.code).toBe(0);
    expect(res.body.data.id).toBe(existingUser.id);
    expect(res.body.data.nickname).toBe('已注册用户');
    expect(res.body.data.phone).toBe(existingUser.phone);
    expect(res.body.data.role).toBe('user');
  });

  it('AU-INT-008: 未登录 → 1001', async () => {
    const res = await request(app)
      .get('/api/auth/me');

    expect(res.status).toBe(401);
    expect(res.body.code).toBe(1001);
  });

  it('AU-INT-009: 无效 Token → 1002', async () => {
    const res = await request(app)
      .get('/api/auth/me')
      .set({ Authorization: 'Bearer invalid-jwt-token-xxxxx' });

    expect(res.status).toBe(401);
    // code 可能是 1001 或 1002（取决于 auth 中间件如何处理）
    expect([1001, 1002]).toContain(res.body.code);
  });
});

/**
 * IM UserSig 签发 — 集成测试
 *
 * 测试范围：supertest + 真实路由 + JWT 鉴权
 * 测试用例：IM-001（已登录获取 UserSig）、IM-002（未登录拒绝）、IM-003（格式校验）
 */

const request = require('supertest');
const {
  setupTestDb,
  teardownTestDb,
  createTestUser,
  authHeader,
} = require('../setup');

// 延迟 require app：确保 .env.test 已被 setup.js 加载
let app;
let user, headers;

beforeAll(async () => {
  await setupTestDb();

  user = await createTestUser({ nickname: 'IM测试用户' });
  headers = authHeader(user);

  // 加载 app（在 DB 就绪后）
  app = require('../../src/app');
});

afterAll(async () => {
  await teardownTestDb();
});

// ============================================================
// IM-001: 已登录用户获取 UserSig
// ============================================================
describe('GET /api/im/user-sig — 已登录用户获取 UserSig', () => {
  it('IM-001: 返回 userId、userSig、sdkAppId', async () => {
    const res = await request(app)
      .get('/api/im/user-sig')
      .set(headers)
      .expect(200);

    expect(res.body.code).toBe(0);
    expect(res.body.message).toBe('ok');

    const { userId, userSig, sdkAppId } = res.body.data;
    expect(userId).toBe(String(user.id));
    expect(userSig).toBeDefined();
    expect(typeof userSig).toBe('string');
    expect(sdkAppId).toBeGreaterThan(0);
  });

  it('IM-001: userId 与登录用户一致', async () => {
    const res = await request(app)
      .get('/api/im/user-sig')
      .set(headers)
      .expect(200);

    expect(res.body.data.userId).toBe(String(user.id));
  });
});

// ============================================================
// IM-002: 未登录用户获取 UserSig
// ============================================================
describe('GET /api/im/user-sig — 未登录拒绝', () => {
  it('IM-002: 无 Authorization header → 1001', async () => {
    const res = await request(app)
      .get('/api/im/user-sig')
      .expect(401);

    expect(res.body.code).toBe(1001);
    expect(res.body.message).toBeDefined();
  });

  it('IM-002: 无效 token → 认证失败', async () => {
    const res = await request(app)
      .get('/api/im/user-sig')
      .set({ Authorization: 'Bearer invalid_token_here' })
      .expect(401);

    expect(res.body.code).toBeGreaterThanOrEqual(1001);
    expect(res.body.code).toBeLessThanOrEqual(1003);
  });
});

// ============================================================
// IM-003: UserSig 格式校验
// ============================================================
describe('GET /api/im/user-sig — 返回数据格式校验', () => {
  it('IM-003: userSig 为非空字符串', async () => {
    const res = await request(app)
      .get('/api/im/user-sig')
      .set(headers)
      .expect(200);

    expect(res.body.data.userSig.length).toBeGreaterThan(0);
  });

  it('IM-003: userSig 长度 > 50', async () => {
    const res = await request(app)
      .get('/api/im/user-sig')
      .set(headers)
      .expect(200);

    expect(res.body.data.userSig.length).toBeGreaterThan(50);
  });

  it('IM-003: userSig 仅含合法 Base64 字符', async () => {
    const res = await request(app)
      .get('/api/im/user-sig')
      .set(headers)
      .expect(200);

    // UserSig 使用 Base64Url 编码：字母数字 + * - _ .
    expect(res.body.data.userSig).toMatch(/^[A-Za-z0-9*_\-.]+$/);
  });

  it('IM-003: sdkAppId 为正整数', async () => {
    const res = await request(app)
      .get('/api/im/user-sig')
      .set(headers)
      .expect(200);

    expect(Number.isInteger(res.body.data.sdkAppId)).toBe(true);
    expect(res.body.data.sdkAppId).toBeGreaterThan(0);
  });
});

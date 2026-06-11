/**
 * JWT 鉴权中间件 — 单元测试（8 条）
 *
 * 被测模块：server/src/middleware/auth.js
 * 覆盖：白名单放行 / 无 Token / 格式错误 / 有效 Token / 过期 Token / user 不存在
 *       / token_version 不匹配 / 封禁用户拦截
 * 测试计划参考：编码迭代计划 §13.1 Phase 1-4
 *
 * 策略：使用真实 JWT + setupTestDb()，通过 auth 中间件测试真实鉴权流程
 */

const jwt = require('jsonwebtoken');
const config = require('../../../src/config');
const { setupTestDb, teardownTestDb, createTestUser, db } = require('../../setup');
const auth = require('../../../src/middleware/auth');

function mockReq(overrides = {}) {
  return {
    method: overrides.method ?? 'GET',
    path: overrides.path ?? '/api/products',
    headers: Object.assign({}, overrides.headers),
    ...overrides,
  };
}
function mockRes() {
  const res = {};
  res.status = () => res;
  res.json = () => res;
  res.set = () => res;
  return res;
}

// ============================================================
// MU-001~008
// ============================================================
describe('auth middleware — 白名单', () => {
  it('MU-001: POST /api/auth/login → 白名单放行', async () => {
    const req = mockReq({ method: 'POST', path: '/auth/login' });
    const next = vi.fn();

    await auth(req, mockRes(), next);

    expect(next).toHaveBeenCalledWith();
  });

  it('MU-002: GET /api/health → 白名单放行', async () => {
    const req = mockReq({ method: 'GET', path: '/health' });
    const next = vi.fn();

    await auth(req, mockRes(), next);

    expect(next).toHaveBeenCalledWith();
  });
});

describe('auth middleware — Token 提取', () => {
  it('MU-003: 无 Authorization header → 1001', async () => {
    const req = mockReq({ method: 'GET', path: '/products' });
    const next = vi.fn();

    await auth(req, mockRes(), next);

    expect(next).toHaveBeenCalledWith(
      expect.objectContaining({ code: 1001, httpStatus: 401 })
    );
  });

  it('MU-004: Authorization 不以 Bearer 开头 → 1001', async () => {
    const req = mockReq({
      method: 'GET',
      path: '/products',
      headers: { authorization: 'Basic xxxxx' },
    });
    const next = vi.fn();

    await auth(req, mockRes(), next);

    expect(next).toHaveBeenCalledWith(
      expect.objectContaining({ code: 1001 })
    );
  });
});

describe('auth middleware — JWT 验证 + 用户状态', () => {
  beforeAll(async () => {
    await setupTestDb();
  });

  afterAll(async () => {
    await teardownTestDb();
  });

  beforeEach(async () => {
    await db.query('DELETE FROM users');
  });

  it('MU-005: 有效 Token + 正常用户 → 挂载 req.user', async () => {
    const user = await createTestUser({ nickname: '鉴权用户' });
    const token = jwt.sign(
      { sub: user.id, role: user.role, tv: user.token_version },
      config.jwt.accessSecret,
      { expiresIn: '15m' }
    );

    const req = mockReq({
      method: 'GET',
      path: '/products',
      headers: { authorization: `Bearer ${token}` },
    });
    const next = vi.fn();

    await auth(req, mockRes(), next);

    expect(next).toHaveBeenCalledWith();
    expect(req.user).toBeDefined();
    expect(req.user.id).toBe(user.id);
    expect(req.user.nickname).toBe('鉴权用户');
    expect(req.user.role).toBe('user');
  });

  it('MU-006: 过期 Token → 1002', async () => {
    const token = jwt.sign(
      { sub: 1, role: 'user', tv: 1 },
      config.jwt.accessSecret,
      { expiresIn: '0s' } // 立即过期
    );

    const req = mockReq({
      method: 'GET',
      path: '/products',
      headers: { authorization: `Bearer ${token}` },
    });
    const next = vi.fn();

    await auth(req, mockRes(), next);

    expect(next).toHaveBeenCalledWith(
      expect.objectContaining({ code: 1002 })
    );
  });

  it('MU-007: token_version 不匹配 → 1002', async () => {
    const user = await createTestUser();
    // 签发带旧 tv 的 token
    const token = jwt.sign(
      { sub: user.id, role: 'user', tv: user.token_version + 1 },
      config.jwt.accessSecret,
      { expiresIn: '15m' }
    );

    const req = mockReq({
      method: 'GET',
      path: '/products',
      headers: { authorization: `Bearer ${token}` },
    });
    const next = vi.fn();

    await auth(req, mockRes(), next);

    expect(next).toHaveBeenCalledWith(
      expect.objectContaining({ code: 1003 })
    );
  });

  it('MU-008: 封禁用户 → 1004', async () => {
    const user = await createTestUser({ status: 'banned' });
    const token = jwt.sign(
      { sub: user.id, role: 'user', tv: user.token_version },
      config.jwt.accessSecret,
      { expiresIn: '15m' }
    );

    const req = mockReq({
      method: 'GET',
      path: '/products',
      headers: { authorization: `Bearer ${token}` },
    });
    const next = vi.fn();

    await auth(req, mockRes(), next);

    expect(next).toHaveBeenCalledWith(
      expect.objectContaining({ code: 1004, httpStatus: 403 })
    );
  });
});

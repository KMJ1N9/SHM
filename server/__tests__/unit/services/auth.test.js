/**
 * 认证服务 — 单元测试（10 条）
 *
 * 被测模块：server/src/services/auth.js
 * 覆盖：mock 登录/新用户注册/封禁拦截/Token 刷新/getMe
 * 测试计划参考：编码迭代计划 §13.1 Phase 1-1
 *
 * 策略：使用 setupTestDb() 真实数据库 + mock_ code（auth.js 已允许 test 环境 mock）
 */

const { setupTestDb, teardownTestDb, createTestUser, db } = require('../../setup');
const authService = require('../../../src/services/auth');

beforeAll(async () => {
  await setupTestDb();
});

afterAll(async () => {
  await teardownTestDb();
});

beforeEach(async () => {
  await db.query('DELETE FROM users');
});

// ============================================================
// AU-001~010
// ============================================================
describe('auth service — login', () => {
  it('AU-001: mock 登录已有用户 → 返回双 Token + isNewUser=false', async () => {
    const user = await createTestUser({ nickname: '已有用户', phone: '13899990001' });

    const result = await authService.login('mock_13899990001');

    expect(result.accessToken).toBeTruthy();
    expect(result.refreshToken).toBeTruthy();
    expect(result.isNewUser).toBe(false);
    expect(result.user.id).toBe(user.id);
    expect(result.user.nickname).toBe('已有用户');
    expect(result.user.phone).toBe('13899990001');
    expect(result.user.role).toBe('user');
    expect(result.user.credit_score).toBe(100);
  });

  it('AU-002: 新用户自动注册 → isNewUser=true', async () => {
    const result = await authService.login('mock_13899990002');

    expect(result.isNewUser).toBe(true);
    expect(result.user.phone).toBe('13899990002');
    expect(result.user.nickname).toBe('微信用户');

    // 验证 DB 中有记录
    const [row] = await db.query('SELECT phone, nickname FROM users WHERE phone = ?', ['13899990002']);
    expect(row.phone).toBe('13899990002');
  });

  it('AU-003: 封禁用户登录 → 1004', async () => {
    // 直接 INSERT 封禁用户
    await db.query(
      "INSERT INTO users (phone, nickname, role, status, credit_score) VALUES (?, ?, ?, ?, ?)",
      ['13899990003', '被封用户', 'user', 'banned', 0]
    );

    await expect(
      authService.login('mock_13899990003')
    ).rejects.toMatchObject({
      code: 1004,
      httpStatus: 403,
    });
  });

  it('AU-004: JWT payload 含 tv', async () => {
    const user = await createTestUser({ phone: '13899990004' });

    const result = await authService.login('mock_13899990004');

    // access token 可被自身 decode
    const jwt = require('jsonwebtoken');
    const config = require('../../../src/config');
    const payload = jwt.verify(result.accessToken, config.jwt.accessSecret);
    expect(payload.sub).toBe(user.id);
    expect(payload.tv).toBe(user.token_version);
  });
});

describe('auth service — refresh', () => {
  it('AU-005: refresh token 有效 → 返回新双 Token', async () => {
    const user = await createTestUser({ phone: '13899990005' });
    const loginResult = await authService.login('mock_13899990005');

    const result = await authService.refresh(loginResult.refreshToken);

    expect(result.accessToken).toBeTruthy();
    expect(result.refreshToken).toBeTruthy();
  });

  it('AU-006: 无效 refresh token → 1002', async () => {
    await expect(
      authService.refresh('invalid-token')
    ).rejects.toMatchObject({
      code: 1002,
      httpStatus: 401,
    });
  });

  it('AU-007: token_version 不匹配 → 1002', async () => {
    const user = await createTestUser({ phone: '13899990006' });
    // 登录 → 签发 token (tv=1)
    const loginResult = await authService.login('mock_13899990006');

    // 修改 token_version 使旧 token 失效（模拟在其他设备修改密码/被封后解封）
    await db.query('UPDATE users SET token_version = ? WHERE id = ?', [2, user.id]);

    await expect(
      authService.refresh(loginResult.refreshToken)
    ).rejects.toMatchObject({ code: 1002 });
  });

  it('AU-008: 封禁用户 refresh → 1004', async () => {
    const user = await createTestUser({ phone: '13899990007' });
    const loginResult = await authService.login('mock_13899990007');

    // 封禁用户
    await db.query('UPDATE users SET status = ? WHERE id = ?', ['banned', user.id]);

    await expect(
      authService.refresh(loginResult.refreshToken)
    ).rejects.toMatchObject({ code: 1004 });
  });
});

describe('auth service — me', () => {
  it('AU-009: getMe 成功 → 返回用户信息', async () => {
    const user = await createTestUser({ nickname: '取我信息', phone: '13899990008' });

    const result = await authService.me(user.id);

    expect(result.id).toBe(user.id);
    expect(result.nickname).toBe('取我信息');
    expect(result.role).toBe('user');
    expect(result.credit_score).toBe(100);
    // 不包含内部字段
    expect(result.token_version).toBeUndefined();
    expect(result.status).toBeUndefined();
  });

  it('AU-010: getMe 用户不存在 → 1002', async () => {
    await expect(authService.me(99999)).rejects.toMatchObject({ code: 1002 });
  });
});

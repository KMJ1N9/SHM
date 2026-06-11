/**
 * 信誉分服务 — 单元测试（6 条）
 *
 * 被测模块：server/src/services/credit.js
 * 覆盖：my / userPublic / changeScore（加分+扣分+下限保护）
 * 测试计划参考：编码迭代计划 §13.1 Phase 1-2
 *
 * 策略：使用 setupTestDb() 真实数据库
 */

const { setupTestDb, teardownTestDb, createTestUser, db } = require('../../setup');
const creditService = require('../../../src/services/credit');

beforeAll(async () => {
  await setupTestDb();
});

afterAll(async () => {
  await teardownTestDb();
});

beforeEach(async () => {
  await db.query('DELETE FROM notifications');
  await db.query('DELETE FROM users');
});

// ============================================================
// CR-001~006
// ============================================================
describe('credit service — my', () => {
  it('CR-001: getMyCredit → 返回信誉分 + 变动记录分页', async () => {
    const user = await createTestUser({ nickname: '信誉分测试', credit_score: 95 });

    const result = await creditService.my(user.id, { page: 1, pageSize: 20 });

    expect(result.score).toBe(95);
    expect(result.change_log).toBeDefined();
  });

  it('CR-002: my 用户不存在 → 2001', async () => {
    await expect(creditService.my(99999)).rejects.toMatchObject({
      code: 2001,
      message: expect.stringContaining('用户不存在'),
    });
  });
});

describe('credit service — userPublic', () => {
  it('CR-003: getUserPublicCredit → 返回公开信息', async () => {
    const user = await createTestUser({ credit_score: 85 });

    const result = await creditService.userPublic(user.id);

    expect(result.user_id).toBe(user.id);
    expect(result.score).toBe(85);
    expect(result.phone).toBeUndefined();
  });

  it('CR-004: userPublic 用户不存在 → 2001', async () => {
    await expect(creditService.userPublic(99999)).rejects.toMatchObject({ code: 2001 });
  });
});

describe('credit service — changeScore', () => {
  it('CR-005: changeScore 加分 → 信誉分增加 + change_log 有记录', async () => {
    const user = await createTestUser({ credit_score: 100 });

    const result = await creditService.changeScore(user.id, 2, '完成交易');

    expect(result.previousScore).toBe(100);
    expect(result.currentScore).toBe(102);

    // 验证 DB 中信誉分已更新
    const [row] = await db.query('SELECT credit_score FROM users WHERE id = ?', [user.id]);
    expect(row.credit_score).toBe(102);

    // 验证通知已创建
    const logs = await db.query(
      "SELECT * FROM notifications WHERE type = 'credit_change' ORDER BY id DESC LIMIT 1"
    );
    expect(logs.length).toBeGreaterThanOrEqual(1);
  });

  it('CR-006: changeScore 扣分 → 总分不低于 0', async () => {
    const user = await createTestUser({ credit_score: 5 });

    const result = await creditService.changeScore(user.id, -30, '举报成立');

    expect(result.previousScore).toBe(5);
    expect(result.currentScore).toBe(0);

    // 验证 DB 中信誉分 ≥ 0
    const [row] = await db.query('SELECT credit_score FROM users WHERE id = ?', [user.id]);
    expect(row.credit_score).toBe(0);
  });
});

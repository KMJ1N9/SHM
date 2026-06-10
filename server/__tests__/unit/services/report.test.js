/**
 * 举报服务 — 单元测试（5 条）
 *
 * 被测模块：server/src/services/report.js
 * 覆盖：举报详情查看/权限校验（举报人/CS/Admin/路人）
 * 测试计划参考：docs/测试计划.md §3.9
 */

const {
  setupTestDb,
  teardownTestDb,
  createTestUser,
  db,
} = require('../../setup');
const reportService = require('../../../src/services/report');

/**
 * 创建测试举报（直接 insert）
 */
async function createTestReport(reporterId, reportedUserId, overrides = {}) {
  const ins = await db.query(
    `INSERT INTO reports (reporter_id, reported_user_id, product_id, order_id, type, description, evidence_images, status)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      reporterId,
      reportedUserId,
      overrides.product_id || null,
      overrides.order_id || null,
      overrides.type || '描述不符',
      overrides.description || '测试举报描述内容，至少需要十个字以上',
      overrides.evidence_images ? JSON.stringify(overrides.evidence_images) : JSON.stringify([]),
      overrides.status || 'pending',
    ]
  );
  return ins.insertId;
}

/** 清理数据（按外键依赖倒序） */
async function cleanData() {
  await db.query('DELETE FROM reports');
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
// 举报详情 — 权限校验
// ============================================================
describe('举报服务 — 举报详情权限校验', () => {
  let reporter, reported, csUser, adminUser, outsider, reportId;

  beforeEach(async () => {
    await cleanData();
    reporter = await createTestUser({ nickname: '举报人', role: 'user' });
    reported = await createTestUser({ nickname: '被举报人', role: 'user' });
    csUser = await createTestUser({ nickname: '客服', role: 'cs' });
    adminUser = await createTestUser({ nickname: '管理员', role: 'admin' });
    outsider = await createTestUser({ nickname: '路人', role: 'user' });
    reportId = await createTestReport(reporter.id, reported.id);
  });

  it('RP-001: 举报人查看自己的举报 → 成功，返回含双方昵称头像的详情', async () => {
    const report = await reportService.detail(reportId, reporter.id, 'user');

    expect(report).toBeTruthy();
    expect(report.id).toBe(reportId);
    expect(report.reporter_id).toBe(reporter.id);
    expect(report.reported_user_id).toBe(reported.id);
    expect(report.reporter_nickname).toBe('举报人');
    expect(report.reported_nickname).toBe('被举报人');
    // 头像字段应存在（可能为 null，取决于 seed 数据）
    expect(report).toHaveProperty('reporter_avatar');
    expect(report).toHaveProperty('reported_avatar');
  });

  it('RP-002: cs 查看他人举报 → 成功（cs 角色可越权查看）', async () => {
    const report = await reportService.detail(reportId, csUser.id, 'cs');

    expect(report).toBeTruthy();
    expect(report.id).toBe(reportId);
  });

  it('RP-003: admin 查看他人举报 → 成功（admin 角色可越权查看）', async () => {
    const report = await reportService.detail(reportId, adminUser.id, 'admin');

    expect(report).toBeTruthy();
    expect(report.id).toBe(reportId);
  });

  it('RP-004: 非举报人非 cs/admin 查看 → 抛出 notOwner (1005)', async () => {
    await expect(
      reportService.detail(reportId, outsider.id, 'user')
    ).rejects.toMatchObject({
      code: 1005,
      message: expect.stringContaining('权限'),
    });
  });

  it('RP-005: 查看不存在的举报 ID → 抛出 notFound (2001)', async () => {
    await expect(
      reportService.detail(99999, reporter.id, 'user')
    ).rejects.toMatchObject({
      code: 2001,
      message: expect.stringContaining('不存在'),
    });
  });
});

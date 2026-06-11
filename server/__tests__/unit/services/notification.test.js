/**
 * 通知服务 — 单元测试（6 条）
 *
 * 被测模块：server/src/services/notification.js
 * 覆盖：list / unreadCount / read / readAll
 * 测试计划参考：编码迭代计划 §13.1 Phase 1-3
 *
 * 策略：使用 setupTestDb() 真实数据库
 */

const { setupTestDb, teardownTestDb, createTestUser, db } = require('../../setup');
const notificationService = require('../../../src/services/notification');

let testUser;

beforeAll(async () => {
  await setupTestDb();
});

afterAll(async () => {
  await teardownTestDb();
});

beforeEach(async () => {
  await db.query('DELETE FROM notifications');
  await db.query('DELETE FROM users');
  testUser = await createTestUser({ nickname: '通知测试' });
});

/** 插入一条测试通知 */
async function insertNotification(overrides = {}) {
  const result = await db.query(
    `INSERT INTO notifications (user_id, type, title, content, is_read, metadata)
     VALUES (?, ?, ?, ?, ?, ?)`,
    [
      overrides.user_id ?? testUser.id,
      overrides.type ?? 'order',
      overrides.title ?? '测试通知',
      overrides.content ?? '通知内容',
      overrides.is_read ?? 0,
      overrides.metadata ? JSON.stringify(overrides.metadata) : null,
    ]
  );
  return result.insertId;
}

// ============================================================
// NF-001~006
// ============================================================
describe('notification service — list', () => {
  it('NF-001: list → 返回当前用户通知', async () => {
    await insertNotification({ title: '通知A', type: 'order' });
    await insertNotification({ title: '通知B', type: 'system' });

    const result = await notificationService.list(testUser.id, { page: 1, pageSize: 20 });

    expect(result.total).toBe(2);
    expect(result.list).toHaveLength(2);
  });

  it('NF-002: list 类型筛选 → 仅返回指定类型', async () => {
    await insertNotification({ type: 'order', title: '订单通知' });
    await insertNotification({ type: 'system', title: '系统通知' });

    const result = await notificationService.list(testUser.id, {
      type: 'order', page: 1, pageSize: 20,
    });

    expect(result.total).toBe(1);
    expect(result.list[0].type).toBe('order');
  });
});

describe('notification service — unreadCount', () => {
  it('NF-003: unreadCount → 返回未读数量', async () => {
    await insertNotification({ is_read: 0 });
    await insertNotification({ is_read: 1 }); // 已读
    await insertNotification({ is_read: 0 });

    const result = await notificationService.unreadCount(testUser.id);

    expect(result.count).toBe(2);
  });

  it('NF-004: unreadCount → 返回 0 当无未读', async () => {
    await insertNotification({ is_read: 1 });
    await insertNotification({ is_read: 1 });

    const result = await notificationService.unreadCount(testUser.id);

    expect(result.count).toBe(0);
  });
});

describe('notification service — read / readAll', () => {
  it('NF-005: markAsRead → 更新 is_read = 1', async () => {
    const id = await insertNotification({ is_read: 0 });

    await notificationService.read(id, testUser.id);

    const [row] = await db.query('SELECT is_read FROM notifications WHERE id = ?', [id]);
    expect(row.is_read).toBe(1);
  });

  it('NF-006: markAsRead 通知不存在 → 2001', async () => {
    await expect(
      notificationService.read(99999, testUser.id)
    ).rejects.toMatchObject({
      code: 2001,
      message: expect.stringContaining('通知不存在'),
    });
  });
});

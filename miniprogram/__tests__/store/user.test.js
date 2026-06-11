/**
 * Pinia user store 测试 — getter 逻辑
 *
 * 测试范围：
 *   - isLoggedIn / creditScore / canPublish / canTrade / isAdmin / isCS
 *   - 边界值：信誉分阈值 / null state / 默认值
 *
 * 不测试 actions（涉及异步 API 调用和 uni.reLaunch 副作用）。
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';

// ── Mock uni storage（store 初始化时需要） ──
vi.stubGlobal('uni', {
  getStorageSync: vi.fn(() => ''),
  setStorageSync: vi.fn(),
  removeStorageSync: vi.fn(),
  reLaunch: vi.fn(),
});

// ── Mock API 模块 ──
vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  refreshToken: vi.fn(),
  getMe: vi.fn(),
}));
vi.mock('@/api/index', () => ({
  saveTokens: vi.fn(),
  clearAuth: vi.fn(),
  resolveImageUrl: vi.fn((url) => url || ''),
}));
vi.mock('@/utils/im', () => ({
  logoutIM: vi.fn(() => Promise.resolve()),
}));
vi.mock('@/store/app', () => ({
  useAppStore: vi.fn(() => ({
    setNetworkStatus: vi.fn(),
    setUnreadMsgCount: vi.fn(),
    setShouldPoll: vi.fn(),
    setUnreadNotifyCount: vi.fn(),
  })),
}));

import { useUserStore } from '@/store/user';

// ── 辅助：创建独立的 store 实例并设置 state ──
function createStore(state = {}) {
  // 每个测试用例用独立 Pinia 实例避免状态污染
  const pinia = createPinia();
  setActivePinia(pinia);
  const store = useUserStore();
  // 覆盖 state
  if (state.user !== undefined) store.user = state.user;
  if (state.accessToken !== undefined) store.accessToken = state.accessToken;
  if (state.refreshToken !== undefined) store.refreshToken = state.refreshToken;
  return store;
}

// ── 测试组：user store getters ──

describe('user store getters', () => {
  // ── isLoggedIn ──

  describe('isLoggedIn', () => {
    it('user 存在 且 accessToken 存在 → true', () => {
      const store = createStore({
        user: { id: 1, nickname: '测试用户' },
        accessToken: 'token123',
      });
      expect(store.isLoggedIn).toBe(true);
    });

    it('user 为 null → false', () => {
      const store = createStore({
        user: null,
        accessToken: 'token123',
      });
      expect(store.isLoggedIn).toBe(false);
    });

    it('accessToken 为空 → false', () => {
      const store = createStore({
        user: { id: 1 },
        accessToken: '',
      });
      expect(store.isLoggedIn).toBe(false);
    });

    it('两者都为空 → false', () => {
      const store = createStore({ user: null, accessToken: '' });
      expect(store.isLoggedIn).toBe(false);
    });
  });

  // ── creditScore ──

  describe('creditScore', () => {
    it('有 user 时返回 credit_score 值', () => {
      const store = createStore({
        user: { id: 1, credit_score: 85 },
        accessToken: 't',
      });
      expect(store.creditScore).toBe(85);
    });

    it('user 为 null → 默认值 100', () => {
      const store = createStore({ user: null, accessToken: '' });
      expect(store.creditScore).toBe(100);
    });

    it('credit_score 为 0 → 返回 0', () => {
      const store = createStore({
        user: { id: 2, credit_score: 0 },
        accessToken: 't',
      });
      expect(store.creditScore).toBe(0);
    });
  });

  // ── canPublish（信誉分 ≥ 60） ──

  describe('canPublish', () => {
    it('信誉分 60 → true', () => {
      const store = createStore({
        user: { id: 1, credit_score: 60 },
        accessToken: 't',
      });
      expect(store.canPublish).toBe(true);
    });

    it('信誉分 59 → false', () => {
      const store = createStore({
        user: { id: 1, credit_score: 59 },
        accessToken: 't',
      });
      expect(store.canPublish).toBe(false);
    });

    it('user 为 null → 默认 100 可发布', () => {
      const store = createStore({ user: null, accessToken: '' });
      expect(store.canPublish).toBe(true);
    });
  });

  // ── canTrade（信誉分 ≥ 30） ──

  describe('canTrade', () => {
    it('信誉分 30 → true', () => {
      const store = createStore({
        user: { id: 1, credit_score: 30 },
        accessToken: 't',
      });
      expect(store.canTrade).toBe(true);
    });

    it('信誉分 29 → false', () => {
      const store = createStore({
        user: { id: 1, credit_score: 29 },
        accessToken: 't',
      });
      expect(store.canTrade).toBe(false);
    });

    it('user 为 null → 默认 100 可交易', () => {
      const store = createStore({ user: null, accessToken: '' });
      expect(store.canTrade).toBe(true);
    });
  });

  // ── isAdmin ──

  describe('isAdmin', () => {
    it('role = admin → true', () => {
      const store = createStore({
        user: { id: 1, role: 'admin' },
        accessToken: 't',
      });
      expect(store.isAdmin).toBe(true);
    });

    it('role = user → false', () => {
      const store = createStore({
        user: { id: 1, role: 'user' },
        accessToken: 't',
      });
      expect(store.isAdmin).toBe(false);
    });

    it('user 为 null → false', () => {
      const store = createStore({ user: null, accessToken: '' });
      expect(store.isAdmin).toBe(false);
    });
  });

  // ── isCS ──

  describe('isCS', () => {
    it('role = cs → true', () => {
      const store = createStore({
        user: { id: 1, role: 'cs' },
        accessToken: 't',
      });
      expect(store.isCS).toBe(true);
    });

    it('role = admin → true（管理员也是客服）', () => {
      const store = createStore({
        user: { id: 1, role: 'admin' },
        accessToken: 't',
      });
      expect(store.isCS).toBe(true);
    });

    it('role = user → false', () => {
      const store = createStore({
        user: { id: 1, role: 'user' },
        accessToken: 't',
      });
      expect(store.isCS).toBe(false);
    });

    it('user 为 null → false', () => {
      const store = createStore({ user: null, accessToken: '' });
      expect(store.isCS).toBe(false);
    });
  });
});

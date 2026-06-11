/**
 * 用户认证状态管理 (Pinia)
 *
 * 管理用户登录态、Token 持久化、会话恢复。
 *
 * Token 存储策略：
 *   - accessToken / refreshToken → uni.setStorageSync（持久化，App 重启后恢复）
 *   - user 信息 → Pinia state（内存，重启后通过 getMe 重新拉取）
 *
 * 路径依赖：
 *   - api/auth.js — 后端认证接口
 *   - api/index.js — saveTokens / clearAuth 工具函数
 */

import { defineStore } from 'pinia';
import { login as loginAPI, refreshToken as refreshAPI, getMe as getMeAPI } from '@/api/auth';
import { saveTokens, clearAuth } from '@/api/index';
import { logoutIM } from '@/utils/im';
import { useAppStore } from '@/store/app';

export const useUserStore = defineStore('user', {
  state: () => ({
    /** @type {Object|null} 当前用户信息 */
    user: null,
    /** @type {string} 短期访问令牌 */
    accessToken: '',
    /** @type {string} 长期刷新令牌 */
    refreshToken: '',
  }),

  getters: {
    /** 是否已登录——同时检查 user 和 accessToken */
    isLoggedIn: (state) => !!(state.user && state.accessToken),

    /** 当前信誉分（默认 100） */
    creditScore: (state) => state.user?.credit_score ?? 100,

    /** 是否可以发布商品（信誉分 ≥ 60） */
    canPublish: (state) => (state.user?.credit_score ?? 100) >= 60,

    /** 是否可以发起交易（信誉分 ≥ 30） */
    canTrade: (state) => (state.user?.credit_score ?? 100) >= 30,

    /** 是否为管理员（可访问用户/商品/日志/敏感词/数据看板等管理功能） */
    isAdmin: (state) => state.user?.role === 'admin',

    /** 是否为客服或管理员（可访问工单管理） */
    isCS: (state) => state.user?.role === 'cs' || state.user?.role === 'admin',
  },

  actions: {
    /**
     * 登录
     *
     * 调用微信手机号授权接口 → 存储双 Token → 拉取用户信息。
     * @param {string} code - wx.getPhoneNumber 返回的 code
     * @returns {Promise<Object>} 用户信息
     */
    async loginAction(code) {
      const result = await loginAPI(code);

      this.accessToken = result.accessToken;
      this.refreshToken = result.refreshToken;
      this.user = result.user;

      // 持久化 Token（user 信息也存一份用于快速恢复 UI）
      saveTokens(result.accessToken, result.refreshToken);
      try {
        uni.setStorageSync('userInfo', result.user);
      } catch {
        // Storage 写入失败不阻塞登录流程
      }

      return result.user;
    },

    /**
     * 退出登录
     *
     * 清除所有状态 + Storage + 停止后台轮询/IM → 跳转登录页。
     */
    logoutAction() {
      // 1. 停止通知轮询（防止后台定时器继续发 401 请求）
      const appStore = useAppStore();
      appStore.setShouldPoll(false);
      appStore.setUnreadNotifyCount(0);
      appStore.setUnreadMsgCount(0);

      // 2. 登出 IM（异步，不阻塞跳转）
      logoutIM().catch(() => {});

      // 3. 清除状态
      this.user = null;
      this.accessToken = '';
      this.refreshToken = '';

      clearAuth();

      // 4. 跳转登录页
      uni.reLaunch({ url: '/pages/auth/login' });
    },

    /**
     * 获取当前用户信息（用于刷新用户资料）
     */
    async getMeAction() {
      const user = await getMeAPI();
      this.user = user;
      try {
        uni.setStorageSync('userInfo', user);
      } catch {
        // 忽略
      }
      return user;
    },

    /**
     * 应用启动时恢复会话
     *
     * 从 Storage 读取 Token → 尝试 getMe 验证有效性。
     * 若 Token 已过期，api/index.js 拦截器会自动尝试刷新。
     * 若刷新也失败，静默清除状态（不强制跳转，用户可能在登录页）。
     */
    async initAuth() {
      try {
        const token = uni.getStorageSync('accessToken');
        const rToken = uni.getStorageSync('refreshToken');

        if (!token) {
          return; // 未登录，无需恢复
        }

        // 先恢复 Token 到 state（getMe 拦截器需要从 state 读取）
        this.accessToken = token;
        this.refreshToken = rToken || '';

        // 尝试验证 Token 有效性（拦截器会自动处理 1002 刷新）
        const user = await getMeAPI();
        this.user = user;

        // 拦截器刷新 Token 后只写 Storage，不同步 Store —— 此处补齐
        const syncedAccess = uni.getStorageSync('accessToken');
        const syncedRefresh = uni.getStorageSync('refreshToken');
        if (syncedAccess) this.accessToken = syncedAccess;
        if (syncedRefresh) this.refreshToken = syncedRefresh;

        try {
          uni.setStorageSync('userInfo', user);
        } catch {
          // 忽略
        }
      } catch {
        // Token 无效且刷新失败 → 静默清除
        this.user = null;
        this.accessToken = '';
        this.refreshToken = '';
        clearAuth();
      }
    },

    /**
     * 刷新 Token（供外部手动调用，如修改资料后刷新用户信息）
     */
    async refreshTokenAction() {
      if (!this.refreshToken) {
        throw new Error('无刷新令牌');
      }
      const result = await refreshAPI(this.refreshToken);
      this.accessToken = result.accessToken;
      if (result.refreshToken) {
        this.refreshToken = result.refreshToken;
      }
      saveTokens(result.accessToken, result.refreshToken);
    },
  },
});

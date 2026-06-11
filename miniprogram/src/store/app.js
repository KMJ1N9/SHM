/**
 * 全局应用状态管理 (Pinia)
 *
 * 管理跨页面的全局状态：
 *   - loading  — 全局加载指示器
 *   - error    — 全局错误信息（用于顶部错误横幅）
 *   - networkStatus — 网络在线状态
 *   - unreadMsgCount  — IM 未读消息总数
 *   - unreadNotifyCount — 通知未读数
 */

import { defineStore } from 'pinia';

export const useAppStore = defineStore('app', {
  state: () => ({
    /** @type {boolean} 全局加载状态 */
    loading: false,
    /** @type {string|null} 全局错误信息（null = 无错误） */
    error: null,
    /** @type {'online'|'offline'} 网络状态 */
    networkStatus: 'online',

    /** @type {number} IM 未读消息总数（由 utils/im.js 更新） */
    unreadMsgCount: 0,
    /** @type {number} 通知未读数（由 App.vue 轮询更新） */
    unreadNotifyCount: 0,

    /** @type {boolean} 是否允许通知轮询（退出登录时置 false 停止轮询） */
    shouldPoll: true,

    /**
     * 待编辑商品 ID（tabBar 页面无法通过 query 传参，用 store 中转）。
     * publish.vue 的 onShow 会检查此字段，若不为 null 则进入编辑模式并消费掉。
     * @type {number|null}
     */
    pendingEditProductId: null,
  }),

  getters: {
    /** TabBar 消息角标数字（IM 未读 + 通知未读） */
    totalUnread: (state) => state.unreadMsgCount + state.unreadNotifyCount,
  },

  actions: {
    /** 开启全局加载 */
    setLoading(value) {
      this.loading = value;
    },

    /** 设置全局错误信息 */
    setError(message) {
      this.error = message;
    },

    /** 清除全局错误 */
    clearError() {
      this.error = null;
    },

    /** 更新网络状态 */
    setNetworkStatus(status) {
      this.networkStatus = status;
    },

    /** 设置 IM 未读消息数 */
    setUnreadMsgCount(count) {
      this.unreadMsgCount = count;
    },

    /** 设置通知未读数 */
    setUnreadNotifyCount(count) {
      this.unreadNotifyCount = count;
    },

    /** 设置是否允许轮询（退出登录时置 false） */
    setShouldPoll(value) {
      this.shouldPoll = value;
    },

    /** 设置待编辑商品 ID（tabBar 路由传参中转） */
    setPendingEditProductId(id) {
      this.pendingEditProductId = id;
    },

    /** 消费并清除待编辑商品 ID */
    consumePendingEditProductId() {
      const id = this.pendingEditProductId;
      this.pendingEditProductId = null;
      return id;
    },
  },
});

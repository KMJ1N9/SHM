/**
 * 腾讯云 IM SDK 初始化模块
 *
 * 职责：
 *   - 创建 IM 实例（单例）
 *   - 登录/登出 IM
 *   - 注册全局事件监听（消息接收/会话更新/SDK 状态）
 *   - 暴露常用 IM 方法（获取会话列表、发送消息、获取消息列表）
 *
 * 架构说明：
 *   聊天消息通过 IM SDK WebSocket 直连腾讯云，不经过项目 Express 后端。
 *   Express 仅负责签发 UserSig（GET /api/im/user-sig）和推送系统消息。
 *
 * 使用：
 *   import { initIM, getTim } from '@/utils/im';
 *   await initIM();          // App.vue onLaunch 中调用
 *   const tim = getTim();    // 页面中获取 IM 实例
 */

import TIM from 'tim-wx-sdk';
import { getUserSig } from '@/api/im';
import { useAppStore } from '@/store/app';

// ---- 常量 ----

/** IM SDK 日志级别：0=DEBUG（排查 IM 连接问题时开启），1=ERROR */
const LOG_LEVEL = 0; // 强制 DEBUG 级别，暴露 SDK 内部网络请求详情

/** 登录重试最大次数 */
const MAX_LOGIN_RETRIES = 3;

/** 消息记录每次拉取条数 */
const MESSAGE_PAGE_SIZE = 15;

// ---- 内部状态 ----

/** @type {Object|null} IM 实例（单例） */
let tim = null;

/** IM 是否已就绪（SDK_READY 事件已触发） */
let isReady = false;

/** 登录重试计数 */
let loginRetries = 0;

/** IM 就绪回调队列（就绪前缓存的操作） */
const readyQueue = [];

/** 最近一次初始化失败的错误信息（供页面展示具体原因） */
let lastError = null;

/** 是否正在初始化中（防止并发 init） */
let isInitializing = false;

/** Peer 资料缓存的 storage key */
const PEER_PROFILE_KEY = 'im_peer_profiles';

/**
 * 读取 peer 资料缓存（直接从 storage）
 *
 * 不使用模块级变量 — 微信小程序中不同页面可能加载不同模块实例，
 * 直接读写 storage 保证所有页面看到同一份数据。
 * @returns {Record<string, {nick: string, avatar: string}>}
 */
function readPeerProfileCache() {
  try {
    const raw = uni.getStorageSync(PEER_PROFILE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    return typeof parsed === 'object' && parsed !== null ? parsed : {};
  } catch {
    return {};
  }
}

/**
 * 写入 peer 资料缓存（直接写 storage）
 * @param {Record<string, {nick: string, avatar: string}>} data
 */
function writePeerProfileCache(data) {
  try {
    uni.setStorageSync(PEER_PROFILE_KEY, JSON.stringify(data));
  } catch {
    // storage 写满或不可用时静默失败
  }
}

// ---- 创建实例 ----

/**
 * 创建 TIM 实例（仅创建，不登录）
 * @returns {Object} TIM 实例
 */
function createTIM() {
  if (tim) return tim;

  // SDKAppID 从服务端 UserSig 响应中获取，此处用占位值
  // 实际登录前会通过 getUserSig() 获取正确的 sdkAppId
  tim = TIM.create({ SDKAppID: 1600145841 });

  // 设置日志级别
  tim.setLogLevel(LOG_LEVEL);

  // 注册全局事件
  _registerEvents(tim);

  return tim;
}

// ---- 事件注册 ----

/**
 * 注册 IM SDK 全局事件监听
 * @param {Object} instance - TIM 实例
 */
function _registerEvents(instance) {
  // SDK 就绪 — 可以开始收发消息
  instance.on(TIM.EVENT.SDK_READY, () => {
    isReady = true;
    loginRetries = 0;
    console.log('[IM] SDK READY');

    // 执行就绪队列中的回调
    while (readyQueue.length > 0) {
      const cb = readyQueue.shift();
      try { cb(); } catch (e) { /* 不中断队列 */ }
    }
  });

  // SDK 未就绪 — 网络断开或心跳超时
  instance.on(TIM.EVENT.SDK_NOT_READY, () => {
    isReady = false;
    console.warn('[IM] SDK NOT READY — 等待自动重连');
  });

  // 收到新消息（单聊 + 群聊，含系统消息）
  instance.on(TIM.EVENT.MESSAGE_RECEIVED, (messageList) => {
    if (!messageList || messageList.length === 0) return;

    // 更新未读计数
    _updateUnreadCount(instance);

    console.log(`[IM] 收到 ${messageList.length} 条新消息`);
  });

  // 会话列表变更（新会话、消息已读、会话删除等）
  // 注意：tim-wx-sdk v2.27.6 事件名为 CONVERSATION_LIST_UPDATED，不是 CONVERSATION_UPDATE
  instance.on(TIM.EVENT.CONVERSATION_LIST_UPDATED, (conversationList) => {
    if (!conversationList || conversationList.length === 0) return;

    // 更新未读计数
    _updateUnreadCount(instance);

    console.log(`[IM] ${conversationList.length} 个会话已更新`);
  });

  // 被踢下线（多端登录 / 管理员强制下线）
  instance.on(TIM.EVENT.KICKED_OUT, (event) => {
    console.warn('[IM] 被踢下线:', event.data.type);
    isReady = false;
    tim = null;

    // 清除登录态
    uni.showToast({ title: '账号在其他设备登录', icon: 'none', duration: 2500 });
  });

  // 网络状态变化
  instance.on(TIM.EVENT.NET_STATE_CHANGE, (event) => {
    const appStore = useAppStore();
    const status = event.data.state === TIM.TYPES.NET_STATE_CONNECTED ? 'online' : 'offline';
    appStore.setNetworkStatus(status);
  });
}

/**
 * 更新 store 中的未读消息总数
 * @param {Object} instance - TIM 实例
 */
function _updateUnreadCount(instance) {
  try {
    // getTotalUnreadMessageCount 返回 { totalCount: number }
    // 注意：此方法可能在某些版本中不可用，做容错处理
    if (typeof instance.getTotalUnreadMessageCount === 'function') {
      instance.getTotalUnreadMessageCount().then((res) => {
        const appStore = useAppStore();
        appStore.setUnreadMsgCount(res.data?.totalCount || 0);
      }).catch(() => {
        // 忽略单次更新失败
      });
    }
  } catch {
    // 忽略
  }
}

// ---- 公开 API ----

/**
 * 初始化 IM：创建实例 → 获取凭证 → 登录
 *
 * 在 App.vue onLaunch 中调用，用户已登录后执行。
 * 如果用户未登录（无 token），跳过 IM 初始化。
 *
 * @returns {Promise<void>}
 */
export async function initIM() {
  // 防止重复初始化
  if (isReady) return;

  // 防止并发初始化（另一个调用正在初始化中）
  if (isInitializing) {
    console.log('[IM] 初始化正在进行中，等待完成...');
    const start = Date.now();
    while (isInitializing && Date.now() - start < 15000) {
      await new Promise((r) => setTimeout(r, 300));
      if (isReady) return;
    }
    if (isReady) return;
    if (isInitializing) {
      console.warn('[IM] 上一次初始化超时，强制重置');
      isInitializing = false;
    }
  }

  isInitializing = true;

  try {
    const instance = createTIM();

    // 获取 UserSig（只获取一次，重试时复用，避免无效网络请求）
    const { userId, userSig } = await getUserSig();
    console.log('[IM] UserSig 获取成功, userId:', userId);

    // 登录循环（最多 MAX_LOGIN_RETRIES 次尝试）
    // 使用循环而非递归，避免 isInitializing 死锁：
    //   递归调用在 catch 块中 → finally 尚未执行 → isInitializing 仍为 true
    //   → 递归进入后命中并发保护 → 自己等自己 15s 超时
    for (let attempt = 1; attempt <= MAX_LOGIN_RETRIES; attempt++) {
      try {
        const loginRes = await instance.login({ userID: userId, userSig });
        console.log('[IM] login() 返回:', JSON.stringify({
          hasData: !!loginRes?.data,
          actionStatus: loginRes?.data?.actionStatus,
          errorCode: loginRes?.data?.errorCode,
          keys: loginRes?.data ? Object.keys(loginRes.data) : [],
        }));

        // TIM SDK login() 响应有两种格式：
        //   - 首次登录：wslogin 协议数据，SDK 同步触发 triggerReady() → isReady 已置为 true
        //   - 重复登录：{ data: { actionStatus: 'OK', repeatLogin: true } }
        // SDK_READY 事件是判断 IM 是否可用的唯一可靠指标。
        if (isReady) {
          loginRetries = 0;
          console.log(`[IM] 登录成功（SDK_READY 已触发）: userId=${userId}`);
          return;
        }

        // 等待 SDK_READY 事件（最多 5s）
        console.log('[IM] 等待 SDK_READY 事件（最多 5s）...');
        await new Promise((resolve) => {
          const start = Date.now();
          const check = () => {
            if (isReady) {
              resolve();
            } else if (Date.now() - start > 5000) {
              resolve();
            } else {
              setTimeout(check, 200);
            }
          };
          check();
        });

        if (isReady) {
          loginRetries = 0;
          console.log(`[IM] 登录成功（SDK_READY 延迟到达）: userId=${userId}`);
          return;
        }

        // 服务端确认登录但 SDK_READY 未在 5s 内触发（极端情况）
        // 降级：信任服务端确认，强制置为就绪
        if (loginRes?.data?.actionStatus === 'OK') {
          loginRetries = 0;
          isReady = true;
          console.log(`[IM] 登录成功（服务端确认，强制就绪）: userId=${userId}`);
          return;
        }

        // 显式错误码（如 70001 userSig expired）
        if (loginRes?.data?.errorCode) {
          throw new Error(`[${loginRes.data.errorCode}] ${loginRes.data.errorInfo || '登录失败'}`);
        }

        // 未知状态：不是就绪也不是错误 → 视为登录失败
        console.error('[IM] 登录未就绪, loginRes:', JSON.stringify(loginRes));
        throw new Error('IM 登录未就绪');
      } catch (err) {
        // 登录已经成功（SDK_READY 在异步等待期间触发）则忽略错误
        if (isReady) {
          loginRetries = 0;
          return;
        }

        const errorMsg = err.message || String(err);
        lastError = errorMsg;
        loginRetries = attempt;
        console.error(`[IM] 登录失败 (${attempt}/${MAX_LOGIN_RETRIES}):`, errorMsg);
        console.error('[IM] 当前状态 — isReady:', isReady);

        // 最后一次尝试已失败
        if (attempt >= MAX_LOGIN_RETRIES) {
          console.error('[IM] 所有重试均失败，最终错误:', errorMsg);
          throw err;
        }

        // 指数退避后进入下一次循环
        const delay = Math.min(1000 * Math.pow(2, attempt), 8000);
        console.log(`[IM] ${delay}ms 后重试...`);
        await new Promise((r) => setTimeout(r, delay));
        // 循环继续 → 下一次 login() 尝试
      }
    }
  } finally {
    isInitializing = false;
  }
}

/**
 * 获取 TIM 实例
 * 页面组件调用前应确保 isIMReady() 返回 true
 * @returns {Object|null}
 */
export function getTim() {
  return tim;
}

/**
 * IM 是否已就绪
 * @returns {boolean}
 */
export function isIMReady() {
  return isReady && tim !== null;
}

/**
 * 等待 IM 就绪（返回 Promise，页面 onLoad 时可 await）
 * 如果已就绪则立即 resolve。
 * @param {number} [timeout=10000] - 超时时间（ms）
 * @returns {Promise<void>}
 */
export function waitForReady(timeout = 10000) {
  if (isReady) return Promise.resolve();

  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      reject(new Error('IM SDK 就绪超时'));
    }, timeout);

    readyQueue.push(() => {
      clearTimeout(timer);
      resolve();
    });
  });
}

/**
 * 获取最近一次 IM 初始化失败的错误信息
 * @returns {string|null}
 */
export function getLastError() {
  return lastError;
}

/**
 * 缓存聊天对方的资料（昵称 + 头像）
 *
 * 用于解决 IM SDK userProfile 为空时消息列表显示 "用户X" 的问题。
 * product/detail → goChat() 时写入，chat/list → mapConversation() 时读取。
 *
 * @param {string|number} userId - 对方 userId
 * @param {string} nick - 昵称
 * @param {string} [avatar=''] - 头像 URL
 */
export function cachePeerProfile(userId, nick, avatar = '') {
  if (!userId || !nick) return;
  const key = String(userId);
  const all = readPeerProfileCache();
  // 不覆盖已有数据（可能来自更可靠的来源）
  if (all[key] && all[key].nick) return;
  all[key] = { nick, avatar };
  writePeerProfileCache(all);
}

/**
 * 获取缓存的 peer 资料
 * @param {string|number} userId - 对方 userId
 * @returns {{ nick: string, avatar: string }|null}
 */
export function getPeerProfile(userId) {
  if (!userId) return null;
  const all = readPeerProfileCache();
  return all[String(userId)] || null;
}

/**
 * 强制重新初始化 IM（重置所有状态后重试）
 *
 * 用于 initIM 在 onLaunch 中失败后，用户在聊天页面主动重试。
 * 会销毁现有实例、重置就绪标志、清空就绪队列，然后全新初始化。
 *
 * @returns {Promise<void>}
 */
export async function reInitIM() {
  lastError = null;
  loginRetries = 0;
  isReady = false;
  isInitializing = false;

  // 销毁现有实例
  if (tim) {
    try {
      tim.logout();
    } catch { /* 忽略销毁时的 logout 错误 */ }
    try {
      tim.destroy();
    } catch { /* 忽略销毁时的 destroy 错误 */ }
    tim = null;
  }

  // 清空就绪队列（拒绝等待者——它们会重新调用 reInitIM）
  while (readyQueue.length > 0) {
    readyQueue.shift();
  }

  return initIM();
}

/**
 * 获取会话列表
 * @returns {Promise<Array>}
 */
export async function getConversationList() {
  if (!tim || !isReady) return [];
  const res = await tim.getConversationList();
  return res.data?.conversationList || [];
}

/**
 * 获取消息列表（历史消息）
 * @param {string} conversationID - 会话 ID（如 C2C123）
 * @param {string} [nextReqMessageID] - 下一页起始消息 ID（首次不传）
 * @returns {Promise<{messageList: Array, nextReqMessageID: string, isCompleted: boolean}>}
 */
export async function getMessageList(conversationID, nextReqMessageID) {
  if (!tim || !isReady) return { messageList: [], nextReqMessageID: '', isCompleted: true };
  const res = await tim.getMessageList({
    conversationID,
    nextReqMessageID,
    count: MESSAGE_PAGE_SIZE,
  });
  return {
    messageList: res.data?.messageList || [],
    nextReqMessageID: res.data?.nextReqMessageID || '',
    isCompleted: res.data?.isCompleted ?? true,
  };
}

/**
 * 发送文本消息
 * @param {string} conversationID - 会话 ID
 * @param {string} text - 文本内容
 * @returns {Promise<Object>} 消息对象
 */
export async function sendTextMessage(conversationID, text) {
  if (!tim || !isReady) throw new Error('IM SDK 未就绪');

  const message = tim.createTextMessage({
    to: conversationID.replace('C2C', ''),
    conversationType: TIM.TYPES.CONV_C2C,
    payload: { text },
  });

  const res = await tim.sendMessage(message);
  return res.data?.message || message;
}

/**
 * 将某会话标记为已读
 * @param {string} conversationID - 会话 ID
 */
export async function setMessageRead(conversationID) {
  if (!tim || !isReady) return;
  try {
    await tim.setMessageRead({ conversationID });
    _updateUnreadCount(tim);
  } catch {
    // 忽略
  }
}

/**
 * 删除会话
 * @param {string} conversationID - 会话 ID
 */
export async function deleteConversation(conversationID) {
  if (!tim || !isReady) throw new Error('IM SDK 未就绪');
  await tim.deleteConversation(conversationID);
}

/**
 * 登出 IM（用户退出登录时调用）
 */
export async function logoutIM() {
  if (tim) {
    try {
      await tim.logout();
    } catch {
      // 忽略登出错误
    }
    isReady = false;
    tim = null;
  }
}

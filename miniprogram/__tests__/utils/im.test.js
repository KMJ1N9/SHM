/**
 * IM 工具函数测试 — peer profile 缓存
 *
 * 测试范围：
 *   - cachePeerProfile — 写入缓存
 *   - getPeerProfile   — 读取缓存
 *   - 缓存覆盖保护（不覆盖已有 nick）
 *   - 空参/边界情况
 *
 * 注意：readPeerProfileCache / writePeerProfileCache 是模块内部函数，
 * 不直接导出，通过 cachePeerProfile / getPeerProfile 间接覆盖。
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';

// ── Mock uni storage API（在模块导入前） ──
const storage = {};
vi.stubGlobal('uni', {
  getStorageSync: vi.fn((key) => {
    return storage[key] ?? '';
  }),
  setStorageSync: vi.fn((key, data) => {
    storage[key] = data;
  }),
  removeStorageSync: vi.fn((key) => {
    delete storage[key];
  }),
});

// ── Mock tim-wx-sdk（模块顶层 import 需要） ──
vi.mock('tim-wx-sdk', () => {
  const EventEmitter = {
    on: vi.fn(),
    off: vi.fn(),
  };
  return {
    default: {
      create: vi.fn(() => ({
        on: vi.fn(),
        setLogLevel: vi.fn(),
        login: vi.fn(),
        logout: vi.fn(),
        destroy: vi.fn(),
        getConversationList: vi.fn(),
        getMessageList: vi.fn(),
        createTextMessage: vi.fn(),
        sendMessage: vi.fn(),
        setMessageRead: vi.fn(),
        deleteConversation: vi.fn(),
        getTotalUnreadMessageCount: vi.fn(),
      })),
      EVENT: {
        SDK_READY: 'sdk_ready',
        SDK_NOT_READY: 'sdk_not_ready',
        MESSAGE_RECEIVED: 'message_received',
        CONVERSATION_LIST_UPDATED: 'conversation_list_updated',
        KICKED_OUT: 'kicked_out',
        NET_STATE_CHANGE: 'net_state_change',
      },
      TYPES: {
        CONV_C2C: 'C2C',
        NET_STATE_CONNECTED: 'connected',
      },
    },
  };
});

// ── Mock @/api/im（避免真实网络请求） ──
vi.mock('@/api/im', () => ({
  getUserSig: vi.fn(),
}));

// ── Mock @/api/index（resolveImageUrl 等） ──
vi.mock('@/api/index', () => ({
  resolveImageUrl: vi.fn((url) => url || ''),
}));

// ── Mock Pinia stores ──
vi.mock('@/store/app', () => ({
  useAppStore: vi.fn(() => ({
    setNetworkStatus: vi.fn(),
    setUnreadMsgCount: vi.fn(),
    setShouldPoll: vi.fn(),
    setUnreadNotifyCount: vi.fn(),
  })),
}));

import {
  cachePeerProfile,
  getPeerProfile,
} from '@/utils/im';

// ── 测试组：peer profile 缓存 ──

describe('IM peer profile 缓存', () => {
  beforeEach(() => {
    // 清空 mock storage
    Object.keys(storage).forEach((k) => delete storage[k]);
  });

  // ── cachePeerProfile ──

  it('cachePeerProfile 写入后 getPeerProfile 可读取', () => {
    cachePeerProfile('123', '张三', 'https://img/avatar.png');

    const profile = getPeerProfile('123');
    expect(profile).not.toBeNull();
    expect(profile.nick).toBe('张三');
    expect(profile.avatar).toBe('https://img/avatar.png');
  });

  it('缓存不应覆盖已有的 nick', () => {
    // 先写入完整数据
    cachePeerProfile('456', '李四', 'https://img/li.png');

    // 再尝试用空 nick 覆盖（模拟 SDK 返回空资料）
    cachePeerProfile('456', '');

    // 已有 nick 不应被覆盖
    const profile = getPeerProfile('456');
    expect(profile.nick).toBe('李四');
  });

  it('cachePeerProfile userId 为空时不写入', () => {
    cachePeerProfile(null, '昵称', 'http://a.jpg');

    // storage 应为空（无有效 key）
    const profile = getPeerProfile(null);
    expect(profile).toBeNull();
  });

  it('cachePeerProfile nick 为空时不写入', () => {
    cachePeerProfile('789', '', 'http://a.jpg');

    const profile = getPeerProfile('789');
    // nick 为空 → 跳过写入（storage 中无此 key）
    expect(profile).toBeNull();
  });

  it('多个不同 userId 的缓存互不干扰', () => {
    cachePeerProfile('1', '用户1', 'http://a1.jpg');
    cachePeerProfile('2', '用户2', 'http://a2.jpg');

    const p1 = getPeerProfile('1');
    const p2 = getPeerProfile('2');

    expect(p1.nick).toBe('用户1');
    expect(p2.nick).toBe('用户2');
  });

  // ── getPeerProfile ──

  it('getPeerProfile 返回 null 当无缓存', () => {
    const profile = getPeerProfile('nonexistent');
    expect(profile).toBeNull();
  });

  it('getPeerProfile userId 为空时返回 null', () => {
    // 先写入一个有效的缓存
    cachePeerProfile('1', '用户1');
    // null/undefined userId 应返回 null
    expect(getPeerProfile(null)).toBeNull();
    expect(getPeerProfile(undefined)).toBeNull();
    expect(getPeerProfile(0)).toBeNull();
  });

  it('getPeerProfile 支持数字类型 userId', () => {
    cachePeerProfile(42, '数字用户');
    const profile = getPeerProfile(42);
    expect(profile.nick).toBe('数字用户');

    // 字符串 '42' 也能匹配
    const profileStr = getPeerProfile('42');
    expect(profileStr.nick).toBe('数字用户');
  });

  it('storage 损坏时降级返回 null', () => {
    // 模拟 storage 中有损坏的 JSON（合法 JSON 但非对象）
    const PEER_PROFILE_KEY = 'im_peer_profiles';
    storage[PEER_PROFILE_KEY] = '123';

    const profile = getPeerProfile('999');
    expect(profile).toBeNull();

    // cachePeerProfile 也应能覆盖损坏数据
    cachePeerProfile('999', '修复后');
    expect(getPeerProfile('999').nick).toBe('修复后');
  });
});

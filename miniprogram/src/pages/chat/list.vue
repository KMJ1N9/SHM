<template>
  <view class="chat-list-page">
    <!-- ============================================================ -->
    <!-- 导航栏 -->
    <!-- ============================================================ -->
    <view class="navbar">
      <text class="navbar-title">
        消息
      </text>
    </view>

    <!-- ============================================================ -->
    <!-- 会话列表 -->
    <!-- ============================================================ -->
    <scroll-view
      v-if="conversations.length > 0"
      class="conversation-scroll"
      scroll-y
      refresher-enabled
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
    >
      <view
        v-for="conv in conversations"
        :key="conv.conversationID"
        class="conversation-item"
        @click="onTapConversation(conv)"
        @longpress="onLongPress(conv)"
      >
        <!-- 头像 -->
        <view class="conv-avatar-wrap">
          <SafeImage
            v-if="conv.avatar"
            class="conv-avatar"
            :src="resolveImageUrl(conv.avatar)"
            mode="aspectFill"
            :lazy-load="true"
          />
          <view v-else class="conv-avatar conv-avatar--placeholder">
            <text class="conv-avatar-emoji">
              👤
            </text>
          </view>
        </view>

        <!-- 信息区 -->
        <view class="conv-body">
          <view class="conv-top-row">
            <text class="conv-nickname" :lines="1">
              {{ conv.nickname }}
            </text>
            <text class="conv-time">
              {{ formatTime(conv.lastTime) }}
            </text>
          </view>
          <view class="conv-bottom-row">
            <text class="conv-summary" :lines="1">
              {{ conv.summary }}
            </text>
            <view v-if="conv.unreadCount > 0" class="conv-badge">
              <text class="conv-badge-text">
                {{ conv.unreadCount > 99 ? '99+' : conv.unreadCount }}
              </text>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>

    <!-- ============================================================ -->
    <!-- 空状态 -->
    <!-- ============================================================ -->
    <view v-else-if="!loading" class="empty-state">
      <text class="empty-emoji">
        💬
      </text>
      <text class="empty-title">
        暂无消息
      </text>
      <text class="empty-desc">
        去逛逛商品，和卖家聊一聊吧
      </text>
      <view class="empty-btn" @click="goHome">
        <text class="empty-btn-text">
          去逛逛
        </text>
      </view>
    </view>

    <!-- 加载中 -->
    <view v-if="loading" class="loading-state">
      <text class="loading-text">
        加载中...
      </text>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import TIM from 'tim-wx-sdk';
import { resolveImageUrl } from '@/api/index';
import SafeImage from '@/components/SafeImage.vue';
import {
  getTim,
  isIMReady,
  waitForReady,
  reInitIM,
  getConversationList,
  deleteConversation,
  setMessageRead,
  getPeerProfile,
  cachePeerProfile,
} from '@/utils/im';
import { getPublicProfile } from '@/api/user';

// ============================================================
// 状态
// ============================================================

/** @type {import('vue').Ref<Array>} */
const conversations = ref([]);
const loading = ref(true);
const refreshing = ref(false);

// ============================================================
// 工具函数
// ============================================================

/**
 * 将会话数据转换为视图模型
 * @param {Object} conv - IM SDK conversation 对象
 * @returns {Object}
 */
function mapConversation(conv) {
  const profile = conv.userProfile || {};
  const lastMessage = conv.lastMessage || {};

  // 摘要：文本消息取前 20 字，系统消息显示类型标签
  let summary = '';
  const msgForShow = lastMessage.messageForShow || '';
  if (lastMessage.type === 'TIMCustomElem') {
    summary = '[系统通知]';
  } else if (msgForShow) {
    summary = msgForShow.length > 20 ? msgForShow.slice(0, 20) + '...' : msgForShow;
  }

  // 从 conversationID 中提取对方 userId（C2C{userId}）
  const peerId = (conv.conversationID || '').replace('C2C', '');

  // 优先使用本地缓存的 peer 资料（来自商品详情页等，数据源为 MySQL），
  // 其次使用 IM SDK userProfile。
  // 缓存优先的原因：IM SDK 的 userProfile.nick 可能是服务端自动生成的
  // "用户X" 占位名（未同步真实昵称时），而缓存来自后端 MySQL，是权威数据。
  const cachedProfile = getPeerProfile(peerId);

  return {
    conversationID: conv.conversationID,
    nickname: cachedProfile?.nick || profile.nick || `用户${peerId}`,
    avatar: cachedProfile?.avatar || profile.avatar || '',
    summary: summary || '暂无消息',
    lastTime: lastMessage.lastTime || conv.lastTime || 0,
    unreadCount: conv.unreadCount || 0,
    peerId,
  };
}

/**
 * 格式化时间
 * @param {number} timestamp - Unix 秒
 * @returns {string}
 */
function formatTime(timestamp) {
  if (!timestamp) return '';

  const now = Date.now() / 1000;
  const diff = now - timestamp;
  const date = new Date(timestamp * 1000);

  if (diff < 60) return '刚刚';
  if (diff < 3600) return `${Math.floor(diff / 60)}分钟前`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}小时前`;

  const nowDate = new Date();
  const yesterday = new Date(nowDate);
  yesterday.setDate(yesterday.getDate() - 1);

  if (date.toDateString() === yesterday.toDateString()) return '昨天';

  const month = date.getMonth() + 1;
  const day = date.getDate();
  return `${month}/${day}`;
}

// ============================================================
// 数据加载
// ============================================================

/**
 * 加载会话列表
 */
async function loadConversations() {
  try {
    // 等待 IM SDK 就绪
    if (!isIMReady()) {
      try {
        await waitForReady(8000);
      } catch {
        // 超时 → 尝试重新初始化 IM（App.vue onLaunch 中的 initIM 可能静默失败）
        console.warn('[ChatList] IM 就绪超时，尝试重新初始化...');
        try {
          await reInitIM();
        } catch (reInitErr) {
          console.warn('[ChatList] IM 重新初始化失败:', reInitErr.message || reInitErr);
          return;
        }
      }
    }

    const rawList = await getConversationList();
    conversations.value = (rawList || []).map(mapConversation);

    // 修复 IM 自动生成的 "用户X" 占位昵称
    // IM 服务端对未设置 Nick 的账号自动生成 "用户X" 名称，
    // 此时 storage 缓存也为空（冷启动），需要从后端 MySQL 获取真实昵称
    await resolveFallbackNames();
  } catch (err) {
    console.warn('[ChatList] 加载会话列表失败:', err.message || err);
  } finally {
    loading.value = false;
    refreshing.value = false;
  }
}

/**
 * 从后端获取 "用户X" 占位昵称对应的真实昵称
 *
 * IM SDK 的 userProfile.nick 可能是服务端自动生成的 "用户3" 这类占位名。
 * 此函数检测这类名称，从后端 MySQL 获取真实昵称，缓存到 storage，
 * 并立即更新 conversations 数组中的显示名称。
 */
async function resolveFallbackNames() {
  // 筛选需要修复的会话：昵称匹配 IM 自动生成模式且无本地缓存
  const needsFix = conversations.value.filter((conv) => {
    if (!conv.peerId) return false;
    const isPlaceholder = /^用户\d+$/.test(conv.nickname);
    if (!isPlaceholder) return false;
    // 已有缓存的不需要再请求
    return !getPeerProfile(conv.peerId);
  });

  if (needsFix.length === 0) return;

  // 并行请求所有需要修复的 peer 资料
  const results = await Promise.allSettled(
    needsFix.map((conv) =>
      getPublicProfile(Number(conv.peerId)).then((profile) => ({
        peerId: conv.peerId,
        nick: profile.nickname,
        avatar: profile.avatar || '',
      }))
    )
  );

  // 缓存结果并更新显示
  for (const result of results) {
    if (result.status !== 'fulfilled') continue;
    const { peerId, nick, avatar } = result.value;
    if (!nick) continue;

    // 写入 storage 缓存
    cachePeerProfile(peerId, nick, avatar);

    // 立即更新 conversations 数组中对应项的昵称
    const conv = conversations.value.find((c) => c.peerId === peerId);
    if (conv) {
      conv.nickname = nick;
      if (avatar) conv.avatar = avatar;
    }
  }
}

/**
 * 下拉刷新
 */
async function onRefresh() {
  refreshing.value = true;
  await loadConversations();
}

// ============================================================
// 交互
// ============================================================

/**
 * 点击会话 → 进入聊天详情
 */
function onTapConversation(conv) {
  // 先标记已读（乐观清零 + 失败恢复）
  if (conv.unreadCount > 0) {
    const prevCount = conv.unreadCount;
    conv.unreadCount = 0;
    setMessageRead(conv.conversationID).catch((err) => {
      console.warn('[ChatList] 标记已读失败:', err.message || err);
      conv.unreadCount = prevCount;
    });
  }

  uni.navigateTo({
    url: `/pages/chat/detail?conversationId=${conv.conversationID}&nickname=${encodeURIComponent(conv.nickname)}&avatar=${encodeURIComponent(conv.avatar)}`,
  });
}

/**
 * 长按删除会话
 */
function onLongPress(conv) {
  uni.showActionSheet({
    itemList: ['删除会话'],
    itemColor: '#FF4D4F',
    success: async (res) => {
      if (res.tapIndex === 0) {
        try {
          await deleteConversation(conv.conversationID);
          conversations.value = conversations.value.filter(
            (c) => c.conversationID !== conv.conversationID
          );
          uni.showToast({ title: '已删除', icon: 'none', duration: 1000 });
        } catch (err) {
          uni.showToast({ title: err.message || '删除失败', icon: 'none', duration: 1500 });
        }
      }
    },
  });
}

/**
 * 跳转首页
 */
function goHome() {
  uni.switchTab({ url: '/pages/index/index' });
}

// ============================================================
// 生命周期
// ============================================================

onMounted(() => {
  loadConversations();

  // 注册会话更新监听
  const tim = getTim();
  if (tim) {
    // 注：tim-wx-sdk v2.27.6 事件名为 CONVERSATION_LIST_UPDATED
    tim.on(TIM.EVENT.CONVERSATION_LIST_UPDATED, () => {
      loadConversations();
    });
  }
});

// 每次显示页面时刷新（从聊天详情页返回时更新未读数和最后消息）
onShow(() => {
  if (!loading.value) {
    loadConversations();
  }
});
</script>

<style lang="scss">
@import '@/styles/tokens.scss';

.chat-list-page {
  min-height: 100vh;
  background: $color-bg;
  display: flex;
  flex-direction: column;
}

// ── 导航栏 ──────────────────────────────────────────────
.navbar {
  padding: 24rpx $space-page;
  background: $color-surface;
  border-bottom: 1rpx solid $color-divider;
}

.navbar-title {
  font-size: $text-xl;
  font-weight: $weight-bold;
  color: $color-title;
}

// ── 会话列表 ──────────────────────────────────────────────
.conversation-scroll {
  flex: 1;
}

.conversation-item {
  display: flex;
  align-items: center;
  padding: 24rpx $space-page;
  background: $color-surface;
  border-bottom: 1rpx solid $color-divider;

  &:active {
    background: #F5F5F5;
  }
}

// 头像 — 尺寸定义在 wrapper 上（而非 SafeImage 组件本身），防止 SafeImage
// 的 scoped CSS（.safe-image { width:100%; height:100% }，特异性 0,2,0）
// 覆盖父级非 scoped 的 .conv-avatar { width:96rpx }（特异性 0,1,0），
// 导致图片加载失败时元素塌陷为零尺寸。
.conv-avatar-wrap {
  width: 96rpx;
  height: 96rpx;
  margin-right: 20rpx;
  flex-shrink: 0;
}

.conv-avatar {
  width: 100%;
  height: 100%;
  border-radius: $radius-full;
  background: $color-divider;

  &--placeholder {
    display: flex;
    align-items: center;
    justify-content: center;
    background: $color-primary-light;
  }
}

.conv-avatar-emoji {
  font-size: 40rpx;
}

// 信息区
.conv-body {
  flex: 1;
  min-width: 0;
}

.conv-top-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8rpx;
}

.conv-nickname {
  font-size: $text-base;
  font-weight: $weight-medium;
  color: $color-title;
  max-width: 400rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-time {
  font-size: $text-xs;
  color: $color-muted;
  flex-shrink: 0;
  margin-left: 16rpx;
}

.conv-bottom-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.conv-summary {
  font-size: $text-sm;
  color: $color-muted;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-badge {
  min-width: 36rpx;
  height: 36rpx;
  padding: 0 10rpx;
  background: $color-error;
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: 16rpx;
  flex-shrink: 0;
}

.conv-badge-text {
  font-size: $text-xs;
  color: #FFFFFF;
  font-weight: $weight-bold;
  line-height: 1;
}

// ── 空状态 ──────────────────────────────────────────────
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 120rpx $space-page;
}

.empty-emoji {
  font-size: 96rpx;
  margin-bottom: 24rpx;
}

.empty-title {
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: $color-title;
  margin-bottom: 12rpx;
}

.empty-desc {
  font-size: $text-sm;
  color: $color-muted;
  margin-bottom: 48rpx;
}

.empty-btn {
  padding: 16rpx 56rpx;
  background: $color-primary;
  border-radius: $radius-full;
}

.empty-btn-text {
  font-size: $text-base;
  color: #FFFFFF;
  font-weight: $weight-medium;
}

// ── 加载中 ──────────────────────────────────────────────
.loading-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.loading-text {
  font-size: $text-sm;
  color: $color-muted;
}
</style>

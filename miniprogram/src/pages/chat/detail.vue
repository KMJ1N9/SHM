<template>
  <view class="chat-detail-page">
    <!-- ============================================================ -->
    <!-- 消息列表 -->
    <!-- ============================================================ -->
    <scroll-view
      class="msg-scroll"
      scroll-y
      :scroll-with-animation="true"
      :scroll-into-view="scrollToView"
      :upper-threshold="100"
      @scrolltoupper="loadMoreHistory"
    >
      <!-- 加载历史 -->
      <view v-if="loadingHistory" class="history-tip">
        <text class="history-tip-text">
          加载中...
        </text>
      </view>
      <view v-else-if="historyCompleted && messages.length > 0" class="history-tip">
        <text class="history-tip-text">
          没有更多消息了
        </text>
      </view>

      <!-- 消息项 -->
      <view
        v-for="msg in messages"
        :id="'msg-' + msg.ID"
        :key="msg.ID"
        class="msg-item"
      >
        <!-- 时间标签 -->
        <view v-if="msg.showTime" class="msg-time-wrap">
          <text class="msg-time-text">
            {{ formatMsgTime(msg.time) }}
          </text>
        </view>

        <!-- 系统消息（自定义消息） -->
        <view v-if="msg.type === 'TIMCustomElem'" class="msg-system-wrap">
          <view class="msg-system-card">
            <text class="msg-system-icon">
              📢
            </text>
            <text class="msg-system-text">
              {{ getSystemMsgText(msg) }}
            </text>
          </view>
        </view>

        <!-- 文本消息气泡 -->
        <view
          v-else
          :class="['msg-bubble-row', msg.flow === 'out' ? 'msg-row--out' : 'msg-row--in']"
        >
          <!-- 对方头像（收到的消息） -->
          <view v-if="msg.flow === 'in'" class="msg-avatar-wrap">
            <SafeImage
              v-if="peerAvatar && !avatarFailed"
              class="msg-avatar"
              :src="resolveImageUrl(peerAvatar)"
              mode="aspectFill"
              @error="onAvatarError"
            />
            <view v-else class="msg-avatar msg-avatar--placeholder">
              <text class="msg-avatar-emoji">
                👤
              </text>
            </view>
          </view>

          <!-- 气泡 -->
          <view :class="['msg-bubble', msg.flow === 'out' ? 'bubble--out' : 'bubble--in']">
            <text class="bubble-text">
              {{ msg.payload.text }}
            </text>
          </view>

          <!-- 发送状态（自己发的消息） -->
          <view v-if="msg.flow === 'out'" class="msg-status-wrap">
            <text v-if="msg.status === 'fail'" class="msg-status msg-status--fail">
              !
            </text>
            <text v-else-if="msg.status === 'unSend'" class="msg-status msg-status--sending">
              ···
            </text>
          </view>

          <!-- 自己头像（发出的消息） -->
          <view v-if="msg.flow === 'out'" class="msg-avatar-wrap msg-avatar-wrap--self">
            <SafeImage
              v-if="myAvatar && !myAvatarFailed"
              class="msg-avatar"
              :src="resolveImageUrl(myAvatar)"
              mode="aspectFill"
              @error="onMyAvatarError"
            />
            <view v-else class="msg-avatar msg-avatar--placeholder">
              <text class="msg-avatar-emoji">
                👤
              </text>
            </view>
          </view>
        </view>
      </view>

      <!-- 空状态 -->
      <view v-if="messages.length === 0 && !loadingInitial" class="msg-empty">
        <text class="msg-empty-emoji">
          💬
        </text>
        <text class="msg-empty-text">
          开始聊天吧~
        </text>
      </view>

      <!-- 初始加载中 -->
      <view v-if="loadingInitial" class="msg-empty">
        <text class="msg-empty-text">
          加载中...
        </text>
      </view>
    </scroll-view>

    <!-- ============================================================ -->
    <!-- 底部输入栏 -->
    <!-- ============================================================ -->
    <view class="input-bar">
      <input
        v-model="inputText"
        class="input-field"
        placeholder="输入消息..."
        confirm-type="send"
        :hold-keyboard="true"
        :cursor-spacing="20"
        :disabled="sending"
        @confirm="sendMessage"
      >

      <view
        :class="['send-btn', inputText.trim() && !sending ? 'send-btn--active' : '']"
        @click="sendMessage"
      >
        <text class="send-btn-text">
          发送
        </text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue';
import { onLoad, onShow, onHide, onUnload } from '@dcloudio/uni-app';
import TIM from 'tim-wx-sdk';
import { resolveImageUrl } from '@/api/index';
import SafeImage from '@/components/SafeImage.vue';
import { useUserStore } from '@/store/user';
import {
  getTim,
  isIMReady,
  waitForReady,
  reInitIM,
  getMessageList,
  sendTextMessage,
  setMessageRead,
} from '@/utils/im';

// ============================================================
// 页面参数
// ============================================================

/** @type {string} 会话 ID（如 C2C123） */
const conversationId = ref('');

/** @type {string} 对方昵称 */
const peerNickname = ref('');

/** @type {string} 对方头像 URL */
const peerAvatar = ref('');

/** 头像加载失败标记 */
const avatarFailed = ref(false);

/** 我的头像加载失败标记 */
const myAvatarFailed = ref(false);

// ============================================================
// 当前用户
// ============================================================

const userStore = useUserStore();

/** 当前登录用户的头像 URL */
const myAvatar = computed(() => userStore.user?.avatar || '');

// ============================================================
// 消息状态
// ============================================================

/** @type {import('vue').Ref<Array>} */
const messages = ref([]);

/** 初始加载中 */
const loadingInitial = ref(true);

/** 加载历史中 */
const loadingHistory = ref(false);

/** 历史消息是否已全部加载 */
const historyCompleted = ref(false);

/** 下一页起始消息 ID（首次为 ''） */
let nextReqMessageID = '';

// ============================================================
// 输入状态
// ============================================================

const inputText = ref('');
const sending = ref(false);

// ============================================================
// 滚动
// ============================================================

/** scroll-into-view 目标元素 ID */
const scrollToView = ref('');

/** 页面是否可见（用于暂停/恢复 MESSAGE_RECEIVED 处理） */
const isPageVisible = ref(true);

// ============================================================
// MESSAGE_RECEIVED 回调引用（用于 onUnload 清理）
// ============================================================

let onMessageReceived = null;

// ============================================================
// 页面生命周期
// ============================================================

onLoad((options) => {
  conversationId.value = options.conversationId || '';
  peerNickname.value = decodeURIComponent(options.nickname || '');
  peerAvatar.value = decodeURIComponent(options.avatar || '');

  if (peerNickname.value) {
    uni.setNavigationBarTitle({ title: peerNickname.value });
  }

  // 标记已读
  if (conversationId.value) {
    setMessageRead(conversationId.value);
  }

  // 注册消息接收监听
  registerMessageListener();

  // 加载初始消息
  loadInitialMessages();
});

onHide(() => {
  // 页面隐藏时暂停消息处理 — 避免在用户不知情时标记已读
  isPageVisible.value = false;
});

onShow(() => {
  isPageVisible.value = true;

  // 从其他页面返回时刷新消息（拉取隐藏期间到达的新消息）
  if (!loadingInitial.value && conversationId.value) {
    loadLatestMessages();
    setMessageRead(conversationId.value);
  }
});

onUnload(() => {
  // 移除消息监听
  if (onMessageReceived) {
    const tim = getTim();
    if (tim) {
      tim.off(TIM.EVENT.MESSAGE_RECEIVED, onMessageReceived);
    }
    onMessageReceived = null;
  }
});

// ============================================================
// 消息加载
// ============================================================

/**
 * 加载初始消息（最新 15 条）
 */
async function loadInitialMessages() {
  try {
    if (!isIMReady()) {
      try {
        await waitForReady(8000);
      } catch {
        // 超时 → 尝试重新初始化 IM
        console.warn('[ChatDetail] IM 就绪超时，尝试重新初始化...');
        try {
          await reInitIM();
        } catch (reInitErr) {
          console.warn('[ChatDetail] IM 重新初始化失败:', reInitErr.message || reInitErr);
          uni.showToast({ title: '聊天功能未就绪，请稍后重试', icon: 'none', duration: 2000 });
          return;
        }
      }
    }

    const result = await getMessageList(conversationId.value);
    const list = result.messageList || [];
    nextReqMessageID = result.nextReqMessageID || '';
    historyCompleted.value = result.isCompleted !== false;

    messages.value = processMessages(list);
    scrollToBottom();
  } catch (err) {
    console.warn('[ChatDetail] 加载消息失败:', err.message || err);
    uni.showToast({ title: '加载消息失败', icon: 'none', duration: 1500 });
  } finally {
    loadingInitial.value = false;
  }
}

/**
 * 加载更早的历史消息（scroll-view 触顶时调用）
 */
async function loadMoreHistory() {
  if (loadingHistory.value || historyCompleted.value) return;

  loadingHistory.value = true;

  try {
    const result = await getMessageList(conversationId.value, nextReqMessageID);
    const list = result.messageList || [];
    nextReqMessageID = result.nextReqMessageID || '';
    historyCompleted.value = result.isCompleted !== false;

    if (list.length > 0) {
      // 将历史消息前置插入
      const processed = processMessages(list);
      messages.value = [...processed, ...messages.value];

      // 重新计算 showTime（历史消息插入后，第一条的 showTime 需要对比后续消息）
      recomputeShowTimes();
    }
  } catch (err) {
    console.warn('[ChatDetail] 加载历史失败:', err.message || err);
  } finally {
    loadingHistory.value = false;
  }
}

// ============================================================
// 消息发送
// ============================================================

/**
 * 发送文本消息
 */
async function sendMessage() {
  const text = inputText.value.trim();
  if (!text || sending.value) return;

  sending.value = true;

  try {
    const msg = await sendTextMessage(conversationId.value, text);
    // 发送成功后才清空输入框 — 失败时保留用户输入可重新发送
    inputText.value = '';
    const processed = processMessages([msg]);
    messages.value.push(...processed);
    scrollToBottom();
  } catch (err) {
    console.warn('[ChatDetail] 发送失败:', err.message || err);
    uni.showToast({ title: err.message || '发送失败', icon: 'none', duration: 1500 });
  } finally {
    sending.value = false;
  }
}

// ============================================================
// 消息接收
// ============================================================

/**
 * 注册 MESSAGE_RECEIVED 事件监听
 */
function registerMessageListener() {
  const tim = getTim();
  if (!tim) return;

  onMessageReceived = (event) => {
    // 页面隐藏时跳过处理 — 避免在用户不知情时标记已读
    if (!isPageVisible.value) return;

    const newMessages = (event.data || []).filter(
      (m) => m.conversationID === conversationId.value
    );

    if (newMessages.length === 0) return;

    const processed = processMessages(newMessages);
    messages.value.push(...processed);

    // 标记已读
    setMessageRead(conversationId.value);
    scrollToBottom();
  };

  tim.on(TIM.EVENT.MESSAGE_RECEIVED, onMessageReceived);
}

// ============================================================
// 工具函数
// ============================================================

/**
 * 加载隐藏期间到达的最新消息（页面 onShow 时调用）
 * 只追加新消息，不丢失已加载的历史
 */
async function loadLatestMessages() {
  try {
    if (!isIMReady()) return;

    const result = await getMessageList(conversationId.value);
    const list = result.messageList || [];

    // 过滤出不在当前列表中的新消息
    const existingIds = new Set(messages.value.map((m) => m.ID));
    const newMessages = list.filter((m) => !existingIds.has(m.ID));

    if (newMessages.length > 0) {
      const processed = processMessages(newMessages);
      messages.value.push(...processed);
      recomputeShowTimes();
      scrollToBottom();
    }
  } catch (err) {
    console.warn('[ChatDetail] 刷新最新消息失败:', err.message || err);
  }
}

/**
 * 处理消息列表：添加 showTime 标记
 * @param {Array} list - 原始消息数组
 * @returns {Array}
 */
function processMessages(list) {
  const prev = messages.value;
  const lastPrevTime = prev.length > 0 ? prev[prev.length - 1].time : 0;

  return list.map((msg, i) => {
    let showTime = false;
    if (prev.length === 0 && i === 0) {
      // 这是第一条消息
      showTime = true;
    } else if (i === 0 && lastPrevTime > 0) {
      // 这条消息接在已有消息后面
      showTime = (msg.time - lastPrevTime) >= 300;
    } else if (i > 0) {
      showTime = (msg.time - list[i - 1].time) >= 300;
    }

    return {
      ...msg,
      showTime,
    };
  });
}

/**
 * 重新计算所有消息的 showTime（用于历史消息前置插入后）
 */
function recomputeShowTimes() {
  const msgs = messages.value;
  for (let i = 0; i < msgs.length; i++) {
    const prevTime = i > 0 ? msgs[i - 1].time : 0;
    msgs[i].showTime = i === 0 || (msgs[i].time - prevTime) >= 300;
  }
}

/**
 * 滚动到底部
 */
function scrollToBottom() {
  const msgs = messages.value;
  if (msgs.length === 0) return;

  // 先清空再设置，强制触发 scroll-into-view
  scrollToView.value = '';
  nextTick(() => {
    scrollToView.value = 'msg-' + msgs[msgs.length - 1].ID;
  });
}

/**
 * 格式化消息时间（用于时间标签）
 * @param {number} timestamp - Unix 秒
 * @returns {string}
 */
function formatMsgTime(timestamp) {
  if (!timestamp) return '';

  const date = new Date(timestamp * 1000);
  const now = new Date();
  const hours = date.getHours().toString().padStart(2, '0');
  const minutes = date.getMinutes().toString().padStart(2, '0');
  const timeStr = `${hours}:${minutes}`;

  // 今天
  if (date.toDateString() === now.toDateString()) {
    return timeStr;
  }

  // 昨天
  const yesterday = new Date(now);
  yesterday.setDate(yesterday.getDate() - 1);
  if (date.toDateString() === yesterday.toDateString()) {
    return `昨天 ${timeStr}`;
  }

  // 今年
  if (date.getFullYear() === now.getFullYear()) {
    return `${date.getMonth() + 1}月${date.getDate()}日 ${timeStr}`;
  }

  // 更早
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日 ${timeStr}`;
}

/**
 * 获取系统消息文本
 * @param {Object} msg - IM 自定义消息对象
 * @returns {string}
 */
function getSystemMsgText(msg) {
  if (msg.payloadForShow) return msg.payloadForShow;
  if (msg.payload && msg.payload.text) return msg.payload.text;
  try {
    if (msg.payload && msg.payload.data) {
      const data = typeof msg.payload.data === 'string'
        ? JSON.parse(msg.payload.data)
        : msg.payload.data;
      return data.text || data.title || '系统消息';
    }
  } catch (err) {
    console.warn('[ChatDetail] 解析系统消息 payload 失败:', err.message || err);
  }
  return '系统消息';
}

/**
 * 头像加载失败回退
 */
function onAvatarError() {
  avatarFailed.value = true;
}

/**
 * 我的头像加载失败回退
 */
function onMyAvatarError() {
  myAvatarFailed.value = true;
}
</script>

<style lang="scss">
@import '@/styles/tokens.scss';

.chat-detail-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: $color-bg;
}

// ── 消息滚动区 ──────────────────────────────────────────────
.msg-scroll {
  flex: 1;
  padding: 16rpx 0;
}

// 历史加载提示
.history-tip {
  display: flex;
  justify-content: center;
  padding: 20rpx 0;
}

.history-tip-text {
  font-size: $text-xs;
  color: $color-muted;
}

// ── 时间标签 ────────────────────────────────────────────────
.msg-time-wrap {
  display: flex;
  justify-content: center;
  padding: 24rpx 0 12rpx;
}

.msg-time-text {
  font-size: $text-xs;
  color: $color-muted;
  background: $color-divider;
  padding: 6rpx 20rpx;
  border-radius: 6rpx;
}

// ── 系统消息 ────────────────────────────────────────────────
.msg-system-wrap {
  display: flex;
  justify-content: center;
  padding: 8rpx 0;
}

.msg-system-card {
  display: flex;
  align-items: center;
  padding: 12rpx 24rpx;
  background: $color-primary-light;
  border-radius: $radius-card;
  max-width: 560rpx;
}

.msg-system-icon {
  font-size: 24rpx;
  margin-right: 10rpx;
  flex-shrink: 0;
}

.msg-system-text {
  font-size: $text-sm;
  color: $color-primary;
  line-height: 1.4;
}

// ── 消息行 ──────────────────────────────────────────────────
.msg-item {
  padding: 4rpx 0;
}

.msg-bubble-row {
  display: flex;
  align-items: flex-start;
  padding: 0 $space-page;
}

.msg-row--in {
  justify-content: flex-start;
}

.msg-row--out {
  justify-content: flex-end;
}

// ── 头像 — 尺寸定义在 wrapper 上，防止 SafeImage scoped CSS
//     (.safe-image { width:100%; height:100% }，特异性 0,2,0)
//     覆盖父级非 scoped .msg-avatar { width:72rpx }（特异性 0,1,0）
//     导致图片加载失败时元素塌陷为零尺寸。
// ──────────────────────────────────────────────────────────────
.msg-avatar-wrap {
  width: 72rpx;
  height: 72rpx;
  flex-shrink: 0;
  margin-right: 16rpx;

  &--spacer {
    margin-right: 0;
    margin-left: 16rpx;
    visibility: hidden;
  }

  &--self {
    margin-right: 0;
    margin-left: 16rpx;
  }
}

.msg-avatar {
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

.msg-avatar-emoji {
  font-size: 32rpx;
  line-height: 1;
}

// ── 气泡 ────────────────────────────────────────────────────
.msg-bubble {
  max-width: 440rpx;
  padding: 18rpx 24rpx;
  border-radius: 16rpx;
}

.bubble--in {
  background: $color-surface;
  border-top-left-radius: 4rpx;
}

.bubble--out {
  background: $color-primary;
  border-top-right-radius: 4rpx;
}

.bubble-text {
  font-size: $text-base;
  color: $color-title;
  line-height: 1.5;
  word-break: break-all;
}

.bubble--out .bubble-text {
  color: #FFFFFF;
}

// ── 发送状态 ────────────────────────────────────────────────
.msg-status-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40rpx;
  height: 72rpx;
  flex-shrink: 0;
  margin-right: 8rpx;
}

.msg-status {
  width: 32rpx;
  height: 32rpx;
  border-radius: $radius-full;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18rpx;
  font-weight: $weight-bold;
  color: #FFFFFF;

  &--fail {
    background: $color-error;
  }

  &--sending {
    background: $color-muted;
    font-size: 14rpx;
  }
}

// ── 空状态 ──────────────────────────────────────────────────
.msg-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-top: 360rpx;
}

.msg-empty-emoji {
  font-size: 96rpx;
  margin-bottom: 24rpx;
}

.msg-empty-text {
  font-size: $text-base;
  color: $color-muted;
}

// ── 输入栏 ──────────────────────────────────────────────────
.input-bar {
  display: flex;
  align-items: center;
  padding: 16rpx $space-page;
  padding-bottom: calc(16rpx + $safe-area-bottom);
  background: $color-surface;
  border-top: 1rpx solid $color-divider;
}

.input-field {
  flex: 1;
  height: 72rpx;
  padding: 0 24rpx;
  background: $color-bg;
  border-radius: 36rpx;
  font-size: $text-base;
  color: $color-title;
}

.send-btn {
  margin-left: 16rpx;
  padding: 14rpx 28rpx;
  border-radius: 36rpx;
  background: $color-divider;
  flex-shrink: 0;

  &--active {
    background: $color-primary;
  }
}

.send-btn-text {
  font-size: $text-base;
  color: $color-muted;
  font-weight: $weight-medium;

  .send-btn--active & {
    color: #FFFFFF;
  }
}
</style>

<template>
  <view class="notify-page">
    <!-- ============================================================ -->
    <!-- 顶部操作栏：类型筛选 + 全部已读 -->
    <!-- ============================================================ -->
    <view class="notify-header">
      <scroll-view class="type-tabs" scroll-x :show-scrollbar="false">
        <view
          v-for="tab in typeTabs"
          :key="tab.value"
          :class="['type-tab', activeType === tab.value ? 'type-tab--active' : '']"
          @click="onSwitchType(tab.value)"
        >
          <text class="type-tab-text">
            {{ tab.label }}
          </text>
        </view>
      </scroll-view>
      <view v-if="hasUnread" class="read-all-btn" @click="onMarkAllRead">
        <text class="read-all-text">
          全部已读
        </text>
      </view>
    </view>

    <!-- ============================================================ -->
    <!-- 通知列表 -->
    <!-- ============================================================ -->
    <scroll-view
      v-if="notifications.length > 0"
      class="notify-scroll"
      scroll-y
      refresher-enabled
      :refresher-triggered="refreshing"
      :lower-threshold="100"
      @refresherrefresh="onRefresh"
      @scrolltolower="loadMore"
    >
      <view
        v-for="item in notifications"
        :key="item.id"
        :class="['notify-item', item.is_read ? '' : 'notify-item--unread']"
        @click="onTapNotification(item)"
      >
        <!-- 类型图标 -->
        <view class="notify-icon-wrap">
          <text class="notify-icon">
            {{ getTypeIcon(item.type) }}
          </text>
        </view>

        <!-- 内容区 -->
        <view class="notify-body">
          <view class="notify-top-row">
            <text class="notify-title">
              {{ item.title }}
            </text>
            <text class="notify-time">
              {{ formatTime(item.created_at) }}
            </text>
          </view>
          <text class="notify-content" :lines="2">
            {{ item.content }}
          </text>
        </view>

        <!-- 未读标记 -->
        <view v-if="!item.is_read" class="unread-dot" />
      </view>

      <!-- 加载更多 -->
      <view v-if="loadingMore" class="loadmore-tip">
        <text class="loadmore-text">
          加载中...
        </text>
      </view>
      <view v-else-if="!hasMore && notifications.length > 0" class="loadmore-tip">
        <text class="loadmore-text">
          没有更多了
        </text>
      </view>
    </scroll-view>

    <!-- ============================================================ -->
    <!-- 空状态 -->
    <!-- ============================================================ -->
    <view v-else-if="!loading" class="notify-empty">
      <text class="notify-empty-emoji">
        🔔
      </text>
      <text class="notify-empty-title">
        {{ activeType === 'all' ? '暂无通知' : '暂无此类通知' }}
      </text>
    </view>

    <!-- 加载中 -->
    <view v-if="loading" class="notify-empty">
      <text class="notify-empty-title">
        加载中...
      </text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import {
  listNotifications,
  markRead,
  markAllRead,
} from '@/api/notification';

// ============================================================
// 类型筛选
// ============================================================

const typeTabs = [
  { label: '全部', value: 'all' },
  { label: '订单', value: 'order_update' },
  { label: '评价', value: 'review_remind' },
  { label: '举报', value: 'report_result' },
  { label: '信誉', value: 'credit_change' },
];

/** 当前选中的通知类型 */
const activeType = ref('all');

// ============================================================
// 列表状态
// ============================================================

/** @type {import('vue').Ref<Array>} */
const notifications = ref([]);
const loading = ref(true);
const refreshing = ref(false);
const loadingMore = ref(false);
const hasMore = ref(true);

/** 当前页码 */
let currentPage = 1;
/** 是否有未读通知 */
const hasUnread = ref(false);

// ============================================================
// 数据加载
// ============================================================

/**
 * 加载通知列表（从头加载）
 */
async function loadNotifications() {
  try {
    const params = { page: 1, pageSize: 20 };
    if (activeType.value !== 'all') {
      params.type = activeType.value;
    }

    const result = await listNotifications(params);
    notifications.value = result.list || [];
    hasMore.value = notifications.value.length < (result.total || 0);
    currentPage = 1;

    // 检测是否有未读
    checkUnread();
  } catch (err) {
    console.warn('[Notify] 加载通知失败:', err.message || err);
    uni.showToast({ title: err.message || '加载失败', icon: 'none', duration: 1500 });
  } finally {
    loading.value = false;
    refreshing.value = false;
  }
}

/**
 * 加载更多（触底分页）
 */
async function loadMore() {
  if (loadingMore.value || !hasMore.value) return;

  loadingMore.value = true;

  try {
    const params = { page: currentPage + 1, pageSize: 20 };
    if (activeType.value !== 'all') {
      params.type = activeType.value;
    }

    const result = await listNotifications(params);
    const newItems = result.list || [];
    notifications.value.push(...newItems);
    hasMore.value = notifications.value.length < (result.total || 0);
    currentPage += 1;
  } catch (err) {
    console.warn('[Notify] 加载更多失败:', err.message || err);
  } finally {
    loadingMore.value = false;
  }
}

/**
 * 下拉刷新
 */
async function onRefresh() {
  refreshing.value = true;
  await loadNotifications();
}

// ============================================================
// 筛选切换
// ============================================================

/**
 * 切换通知类型
 */
function onSwitchType(type) {
  if (activeType.value === type) return;
  activeType.value = type;
  loading.value = true;
  notifications.value = [];
  loadNotifications();
}

// ============================================================
// 交互
// ============================================================

/**
 * 点击通知 → 标记已读 + 跳转
 */
async function onTapNotification(item) {
  // 标记已读
  if (!item.is_read) {
    try {
      await markRead(item.id);
      item.is_read = 1;
      checkUnread();
    } catch (err) {
      console.warn('[Notify] 标记已读失败:', err.message || err);
    }
  }

  // 尝试跳转对应页面（从 metadata 中提取路由信息）
  const meta = parseMetadata(item.metadata);
  if (meta && meta.route) {
    uni.navigateTo({ url: meta.route });
  }
}

/**
 * 全部标记已读
 */
async function onMarkAllRead() {
  try {
    await markAllRead();
    notifications.value.forEach((n) => {
      n.is_read = 1;
    });
    hasUnread.value = false;
    uni.showToast({ title: '已全部标记为已读', icon: 'none', duration: 1500 });
  } catch (err) {
    uni.showToast({ title: err.message || '操作失败', icon: 'none', duration: 1500 });
  }
}

// ============================================================
// 工具函数
// ============================================================

/**
 * 检查列表中是否有未读通知
 */
function checkUnread() {
  hasUnread.value = notifications.value.some((n) => !n.is_read);
}

/**
 * 获取通知类型图标
 * @param {string} type
 * @returns {string}
 */
function getTypeIcon(type) {
  const icons = {
    order_update: '📦',
    review_remind: '⭐',
    report_result: '📋',
    credit_change: '🛡️',
  };
  return icons[type] || '📢';
}

/**
 * 格式化时间
 * @param {string} dateStr - ISO 时间字符串
 * @returns {string}
 */
function formatTime(dateStr) {
  if (!dateStr) return '';
  try {
    const date = new Date(dateStr);
    const now = Date.now();
    const diff = now - date.getTime();
    const seconds = Math.floor(diff / 1000);

    if (seconds < 60) return '刚刚';
    if (seconds < 3600) return `${Math.floor(seconds / 60)}分钟前`;
    if (seconds < 86400) return `${Math.floor(seconds / 3600)}小时前`;

    const nowDate = new Date();
    const yesterday = new Date(nowDate);
    yesterday.setDate(yesterday.getDate() - 1);

    if (date.toDateString() === yesterday.toDateString()) return '昨天';

    const month = date.getMonth() + 1;
    const day = date.getDate();
    return `${month}/${day}`;
  } catch {
    return '';
  }
}

/**
 * 解析 metadata（可能是 JSON 字符串或对象）
 * @param {*} meta
 * @returns {Object|null}
 */
function parseMetadata(meta) {
  if (!meta) return null;
  if (typeof meta === 'object') return meta;
  try {
    return JSON.parse(meta);
  } catch {
    return null;
  }
}

// ============================================================
// 生命周期
// ============================================================

// 每次显示页面时刷新（从其他页面返回时更新已读状态）
onShow(() => {
  if (!loading.value) {
    loadNotifications();
  }
});
</script>

<style lang="scss">
@import '@/styles/tokens.scss';

.notify-page {
  min-height: 100vh;
  background: $color-bg;
  display: flex;
  flex-direction: column;
}

// ── 顶部操作栏 ────────────────────────────────────────────────
.notify-header {
  display: flex;
  align-items: center;
  background: $color-surface;
  border-bottom: 1rpx solid $color-divider;
}

.type-tabs {
  flex: 1;
  white-space: nowrap;
}

.type-tab {
  display: inline-flex;
  align-items: center;
  padding: 20rpx 28rpx;
  border-bottom: 4rpx solid transparent;

  &--active {
    border-bottom-color: $color-primary;
  }
}

.type-tab-text {
  font-size: $text-base;
  color: $color-body;

  .type-tab--active & {
    color: $color-primary;
    font-weight: $weight-bold;
  }
}

.read-all-btn {
  padding: 0 28rpx;
  flex-shrink: 0;
  border-left: 1rpx solid $color-divider;
  height: 56rpx;
  display: flex;
  align-items: center;
}

.read-all-text {
  font-size: $text-sm;
  color: $color-primary;
}

// ── 列表区 ──────────────────────────────────────────────────
.notify-scroll {
  flex: 1;
}

.notify-item {
  display: flex;
  align-items: flex-start;
  padding: 24rpx $space-page;
  background: $color-surface;
  border-bottom: 1rpx solid $color-divider;
  position: relative;

  &--unread {
    background: $color-primary-light;
  }

  &:active {
    background: #F5F5F5;
  }
}

// 类型图标
.notify-icon-wrap {
  width: 72rpx;
  height: 72rpx;
  border-radius: $radius-full;
  background: $color-bg;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20rpx;
  flex-shrink: 0;
}

.notify-icon {
  font-size: 36rpx;
}

// 内容区
.notify-body {
  flex: 1;
  min-width: 0;
}

.notify-top-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6rpx;
}

.notify-title {
  font-size: $text-base;
  font-weight: $weight-medium;
  color: $color-title;
  max-width: 400rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notify-time {
  font-size: $text-xs;
  color: $color-muted;
  flex-shrink: 0;
  margin-left: 16rpx;
}

.notify-content {
  font-size: $text-sm;
  color: $color-body;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

// 未读圆点
.unread-dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: $radius-full;
  background: $color-primary;
  margin-left: 12rpx;
  margin-top: 8rpx;
  flex-shrink: 0;
}

// 加载更多
.loadmore-tip {
  display: flex;
  justify-content: center;
  padding: 32rpx 0;
}

.loadmore-text {
  font-size: $text-xs;
  color: $color-muted;
}

// ── 空状态/加载中 ──────────────────────────────────────────
.notify-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 120rpx $space-page;
}

.notify-empty-emoji {
  font-size: 96rpx;
  margin-bottom: 24rpx;
}

.notify-empty-title {
  font-size: $text-base;
  color: $color-muted;
}
</style>

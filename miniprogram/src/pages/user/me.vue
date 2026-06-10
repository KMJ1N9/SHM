<template>
  <view class="me-page">
    <!-- 用户信息 -->
    <view class="user-section">
      <view class="user-card">
        <image
          class="user-avatar"
          :src="user?.avatar || defaultAvatar"
          mode="aspectFill"
        />
        <view class="user-info">
          <text class="user-name">{{ user?.nickname || '微信用户' }}</text>
          <text v-if="user?.class_name" class="user-class">{{ user.class_name }}</text>
        </view>
        <view class="user-credit">
          <text class="credit-score">{{ user?.credit_score || 100 }}</text>
          <text class="credit-label">信誉分</text>
        </view>
      </view>
    </view>

    <!-- 功能菜单 -->
    <view class="menu-section">
      <view class="menu-item" @click="goOrders">
        <text class="menu-icon">📋</text>
        <text class="menu-label">我的订单</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goMyProducts">
        <text class="menu-icon">📦</text>
        <text class="menu-label">我的发布</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goReviews">
        <text class="menu-icon">⭐</text>
        <text class="menu-label">我的评价</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goCredit">
        <text class="menu-icon">🛡️</text>
        <text class="menu-label">信誉分明细</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goReports">
        <text class="menu-icon">📝</text>
        <text class="menu-label">我的举报</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>

    <!-- 管理功能（仅管理员/cs 可见） -->
    <view v-if="userStore.isAdmin" class="menu-section">
      <view class="menu-section-header">
        <text class="menu-section-title">管理功能</text>
      </view>
      <view class="menu-item" @click="goTickets">
        <text class="menu-icon">🎫</text>
        <text class="menu-label">工单管理</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goDashboard">
        <text class="menu-icon">📊</text>
        <text class="menu-label">数据看板</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>

    <!-- 退出登录 -->
    <view class="logout-section">
      <view class="logout-btn" @click="onLogout">
        <text class="logout-btn-text">退出登录</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue';
import { useUserStore } from '@/store/user';

const userStore = useUserStore();
const user = computed(() => userStore.user);

const defaultAvatar =
  'data:image/svg+xml,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><circle cx="50" cy="50" r="50" fill="#F0F0F0"/></svg>'
  );

function goOrders() {
  uni.navigateTo({ url: '/pages/order/list' });
}

function goMyProducts() {
  uni.navigateTo({ url: '/pages/product/my' });
}

function goReviews() {
  uni.navigateTo({ url: '/pages/user/reviews' });
}

function goCredit() {
  uni.navigateTo({ url: '/pages/user/credit' });
}

function goReports() {
  uni.navigateTo({ url: '/pages/report/list' });
}

function goTickets() {
  uni.navigateTo({ url: '/pages/admin/tickets' });
}

function goDashboard() {
  uni.navigateTo({ url: '/pages/admin/dashboard' });
}

function onLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确定要退出登录吗？',
    confirmText: '退出',
    cancelText: '取消',
    success: (res) => {
      if (res.confirm) {
        userStore.logoutAction();
      }
    },
  });
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.me-page {
  min-height: 100vh;
  background: $color-bg;
}

// ── 用户信息 ──────────────────────────────────────────────
.user-section {
  background: $color-primary-gradient;
  padding: 60rpx $space-page 40rpx;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.user-avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  border: 4rpx solid rgba(255, 255, 255, 0.3);
  background: $color-divider;
  flex-shrink: 0;
}

.user-info {
  flex: 1;
}

.user-name {
  font-size: $text-xl;
  font-weight: $weight-bold;
  color: #FFFFFF;
  display: block;
}

.user-class {
  font-size: $text-sm;
  color: rgba(255, 255, 255, 0.8);
  margin-top: 4rpx;
  display: block;
}

.user-credit {
  display: flex;
  flex-direction: column;
  align-items: center;
  background: rgba(255, 255, 255, 0.15);
  border-radius: $radius-card;
  padding: 12rpx 24rpx;
  flex-shrink: 0;
}

.credit-score {
  font-size: $text-2xl;
  font-weight: $weight-bold;
  color: #FFFFFF;
  font-family: $font-mono;
}

.credit-label {
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.8);
}

// ── 功能菜单 ──────────────────────────────────────────────
.menu-section {
  background: $color-surface;
  margin-top: 16rpx;
}

.menu-section-header {
  padding: 24rpx $space-page 12rpx;
}

.menu-section-title {
  font-size: $text-xs;
  color: $color-muted;
  font-weight: $weight-medium;
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 28rpx $space-page;
  border-bottom: 1rpx solid $color-divider;

  &:last-child { border-bottom: none; }

  &:active { background: #F5F5F5; }
}

.menu-icon {
  font-size: 36rpx;
  margin-right: 20rpx;
}

.menu-label {
  flex: 1;
  font-size: $text-base;
  color: $color-title;
}

.menu-arrow {
  font-size: 36rpx;
  color: $color-muted;
}

// ── 退出登录 ──────────────────────────────────────────────
.logout-section {
  margin-top: 80rpx;
  padding: 0 $space-page;
}

.logout-btn {
  height: $btn-height-md;
  border-radius: $radius-card;
  border: 1rpx solid $color-divider;
  display: flex;
  align-items: center;
  justify-content: center;

  &:active { background: $color-bg; }
}

.logout-btn-text {
  font-size: $text-base;
  color: $color-muted;
}
</style>

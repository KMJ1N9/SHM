<template>
  <view class="settings-page">
    <!-- 关于分组 -->
    <view class="menu-section">
      <view class="menu-section-header">
        <text class="menu-section-title">关于</text>
      </view>
      <view class="menu-item" @click="goAbout">
        <text class="menu-icon">ℹ️</text>
        <text class="menu-label">关于我们</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>

    <!-- 协议分组 -->
    <view class="menu-section">
      <view class="menu-section-header">
        <text class="menu-section-title">协议与政策</text>
      </view>
      <view class="menu-item" @click="showAgreement('user')">
        <text class="menu-icon">📄</text>
        <text class="menu-label">用户协议</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="showAgreement('privacy')">
        <text class="menu-icon">🔒</text>
        <text class="menu-label">隐私政策</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>

    <!-- 账号分组 -->
    <view class="menu-section">
      <view class="menu-section-header">
        <text class="menu-section-title">账号</text>
      </view>
      <view class="menu-item menu-item--danger" @click="onLogout">
        <text class="menu-icon">🚪</text>
        <text class="menu-label menu-label--danger">退出登录</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>

    <!-- 版本信息 -->
    <view class="version-info">
      <text class="version-text">版本 1.0.0</text>
    </view>
  </view>
</template>

<script setup>
/**
 * 设置页 — 应用设置与账号管理
 *
 * 入口：me 页 → 系统设置菜单项
 */
import { useUserStore } from '@/store/user';

const userStore = useUserStore();

function goAbout() {
  uni.navigateTo({ url: '/pages/about/index' });
}

function showAgreement(type) {
  uni.navigateTo({ url: `/pages/agreement/index?type=${type}` });
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

.settings-page {
  min-height: 100vh;
  background: $color-bg;
  padding-bottom: $safe-area-bottom;
}

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

.menu-item--danger {
  &:active { background: rgba(255, 77, 79, 0.05); }
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

.menu-label--danger {
  color: $color-error;
}

.menu-arrow {
  font-size: 36rpx;
  color: $color-muted;
}

.version-info {
  margin-top: 48rpx;
  text-align: center;
}

.version-text {
  font-size: $text-xs;
  color: $color-muted;
}
</style>

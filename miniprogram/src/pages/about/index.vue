<template>
  <view class="about-page">
    <view class="about-header">
      <text class="about-logo">🛒</text>
      <text class="about-app-name">校园二手交易</text>
      <text class="about-version">v1.0.0</text>
    </view>

    <view class="about-section">
      <text class="about-section-title">关于我们</text>
      <text class="about-text">
        校园二手交易小程序是一个面向广州应用科技学院肇庆校区的 C2C
        二手交易平台。由计算机学院学生独立开发，旨在为校内师生提供安全、便捷的闲置物品交易服务。
      </text>
    </view>

    <view class="about-section">
      <text class="about-section-title">技术栈</text>
      <view class="tech-stack">
        <text class="tech-item">uni-app (Vue 3) + SCSS</text>
        <text class="tech-item">Node.js + Express</text>
        <text class="tech-item">MySQL</text>
        <text class="tech-item">腾讯云 IM + COS</text>
      </view>
    </view>

    <view class="about-section">
      <text class="about-section-title">联系方式</text>
      <text class="about-text">如有问题或建议，请联系平台管理员。</text>
    </view>
  </view>
</template>

<script setup>
/**
 * 关于我们 — 应用信息静态展示页
 *
 * 入口：设置页 → 关于我们
 */
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.about-page {
  min-height: 100vh;
  background: $color-bg;
  padding-bottom: $safe-area-bottom;
}

.about-header {
  background: $color-primary-gradient;
  padding: 80rpx $space-page 60rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.about-logo {
  font-size: 96rpx;
  margin-bottom: $space-content;
}

.about-app-name {
  font-size: $text-xl;
  font-weight: $weight-bold;
  color: #FFFFFF;
  margin-bottom: 8rpx;
}

.about-version {
  font-size: $text-sm;
  color: rgba(255, 255, 255, 0.7);
  font-family: $font-mono;
}

.about-section {
  background: $color-surface;
  margin-top: 16rpx;
  padding: 24rpx $space-page;
}

.about-section-title {
  font-size: $text-base;
  font-weight: $weight-medium;
  color: $color-title;
  display: block;
  margin-bottom: 16rpx;
}

.about-text {
  font-size: $text-sm;
  color: $color-body;
  line-height: 1.8;
}

.tech-stack {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.tech-item {
  font-size: $text-xs;
  color: $color-primary;
  background: $color-primary-light;
  padding: 6rpx 16rpx;
  border-radius: $radius-card;
}
</style>

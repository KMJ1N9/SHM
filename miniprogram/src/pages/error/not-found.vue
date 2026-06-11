<template>
  <view class="error-page">
    <view class="error-content">
      <text class="error-icon">🔍</text>
      <text class="error-title">页面不存在</text>
      <text class="error-desc">你要找的页面可能已被移除或链接地址有误</text>
      <button class="error-btn" @click="goHome">返回首页</button>
    </view>
  </view>
</template>

<script setup>
/**
 * 404 页面 — 全局页面未找到时的降级展示
 *
 * 集成方式（后续轮次）：
 *   在 App.vue 的 onPageNotFound 事件中调用 uni.navigateTo 跳转到此页面。
 *   小程序不支持传统的 404 catch-all 路由，需通过框架事件手动跳转。
 */
function goHome() {
  uni.switchTab({ url: '/pages/index/index' });
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.error-page {
  min-height: 100vh;
  background: $color-bg;
  display: flex;
  align-items: center;
  justify-content: center;
}

.error-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: $space-page;
}

.error-icon {
  font-size: 128rpx;
  margin-bottom: $space-card;
}

.error-title {
  font-size: $text-xl;
  font-weight: $weight-bold;
  color: $color-title;
  margin-bottom: $space-content;
}

.error-desc {
  font-size: $text-sm;
  color: $color-muted;
  text-align: center;
  line-height: 1.6;
  margin-bottom: 64rpx;
}

.error-btn {
  width: 280rpx;
  height: $btn-height-md;
  line-height: $btn-height-md;
  background: $color-primary;
  color: #FFFFFF;
  font-size: $text-base;
  border-radius: $radius-full;
  border: none;
}
</style>

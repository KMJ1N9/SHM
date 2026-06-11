<template>
  <view class="deprecated-page">
    <text class="deprecated-icon">📝</text>
    <text class="deprecated-title">评价功能已迁移</text>
    <text class="deprecated-desc">请在对应订单详情页中进行评价</text>
    <button class="deprecated-btn" @click="goOrders">查看我的订单</button>
  </view>
</template>

<script setup>
/**
 * review/create.vue — 已废弃
 *
 * 评价功能已在 order/detail.vue 弹窗中完整实现（三维 StarRating + 互评进度条）。
 * 此页面保留用于兼容旧链接，引导用户跳转到订单列表。
 *
 * @deprecated 自第 9 轮起废弃，后续版本将移除此路由。
 */
function goOrders() {
  uni.navigateTo({ url: '/pages/order/list' });
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.deprecated-page {
  min-height: 100vh;
  background: $color-bg;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $space-page;
}

.deprecated-icon {
  font-size: 96rpx;
  margin-bottom: $space-card;
}

.deprecated-title {
  font-size: $text-xl;
  font-weight: $weight-bold;
  color: $color-title;
  margin-bottom: $space-content;
}

.deprecated-desc {
  font-size: $text-sm;
  color: $color-muted;
  margin-bottom: 48rpx;
}

.deprecated-btn {
  width: 320rpx;
  height: $btn-height-md;
  line-height: $btn-height-md;
  background: $color-primary;
  color: #FFFFFF;
  font-size: $text-base;
  border-radius: $radius-card;
  border: none;
}
</style>

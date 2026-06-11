<template>
  <view class="seller-section">
    <text class="section-title">卖家信息</text>
    <view class="seller-card" @click="goProfile">
      <image
        class="seller-avatar"
        :src="seller.avatar || defaultAvatar"
        mode="aspectFill"
      />
      <view class="seller-info">
        <text class="seller-name">{{ seller.nickname }}</text>
        <text v-if="seller.class_name" class="seller-detail">{{ seller.class_name }}</text>
        <text v-if="seller.dorm_building" class="seller-detail">{{ seller.dorm_building }}</text>
      </view>
      <view class="seller-credit">
        <text class="credit-score">{{ seller.credit_score || 100 }}</text>
        <text class="credit-label">信誉分</text>
      </view>
      <text class="seller-arrow">›</text>
    </view>
    <!-- 举报入口 — 仅非本人商品显示 -->
    <view v-if="!isOwner" class="report-entry" @click="goReport">
      <text>⚑ 举报商品</text>
    </view>
  </view>
</template>

<script setup>
import { useUserStore } from '@/store/user';

const props = defineProps({
  seller: { type: Object, default: null },
  isOwner: { type: Boolean, default: false },
  productId: { type: Number, default: 0 },
  defaultAvatar: { type: String, default: '' },
});

const userStore = useUserStore();

function goProfile() {
  if (props.seller?.id) {
    uni.navigateTo({ url: `/pages/user/profile?id=${props.seller.id}` });
  }
}

function goReport() {
  if (!userStore.isLoggedIn) {
    uni.showToast({ title: '请先登录', icon: 'none', duration: 1500 });
    return;
  }
  if (!props.seller?.id) return;
  uni.navigateTo({
    url: `/pages/report/submit?product_id=${props.productId}&reported_user_id=${props.seller.id}`,
  });
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.seller-section {
  background: $color-surface;
  margin-top: 16rpx;
  padding: $space-card $space-page;
}

.section-title {
  font-size: $text-base;
  font-weight: $weight-bold;
  color: $color-title;
  display: block;
  margin-bottom: 16rpx;
}

.seller-card {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.seller-avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: $color-divider;
  flex-shrink: 0;
}

.seller-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.seller-name {
  font-size: $text-base;
  font-weight: $weight-medium;
  color: $color-title;
}

.seller-detail {
  font-size: $text-xs;
  color: $color-muted;
  margin-top: 4rpx;
}

.seller-credit {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
}

.credit-score {
  font-size: $text-xl;
  font-weight: $weight-bold;
  color: $color-success;
  font-family: $font-mono;
}

.credit-label {
  font-size: 20rpx;
  color: $color-muted;
}

.seller-arrow {
  font-size: 36rpx;
  color: $color-muted;
  flex-shrink: 0;
}

.report-entry {
  text-align: right;
  padding-top: 12rpx;
  margin-top: 8rpx;
  border-top: 1rpx solid $color-divider;
}

.report-entry text {
  font-size: $text-xs;
  color: $color-muted;
}
</style>

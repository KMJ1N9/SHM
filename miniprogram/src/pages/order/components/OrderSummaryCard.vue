<template>
  <view>
    <!-- 商品信息 -->
    <view class="section-card">
      <text class="section-title">商品信息</text>
      <view class="product-row" @click="goProduct">
        <SafeImage
          class="product-image"
          :src="resolveImageUrl(productImage)"
          mode="aspectFill"
        />
        <view class="product-info">
          <text class="product-title">{{ productTitle }}</text>
          <text class="product-condition">{{ productCondition }}</text>
          <view class="product-price-row">
            <text class="product-price">¥{{ formatPrice(productPrice) }}</text>
          </view>
          <text class="product-location">{{ productLocation }}</text>
        </view>
        <text class="arrow-right">›</text>
      </view>
    </view>

    <!-- 交易对象 -->
    <view class="section-card">
      <text class="section-title">交易对象</text>
      <view class="partner-row">
        <image
          v-if="partnerAvatar"
          class="partner-avatar"
          :src="partnerAvatar"
          mode="aspectFill"
        />
        <view v-else class="partner-avatar partner-avatar--default">
          <text class="partner-avatar-emoji">👤</text>
        </view>
        <text class="partner-name">{{ partnerName }}</text>
      </view>
    </view>

    <!-- 订单信息 -->
    <view class="section-card">
      <text class="section-title">订单信息</text>
      <view class="info-item">
        <text class="info-label">订单编号</text>
        <text class="info-value" selectable>#{{ order.id }}</text>
      </view>
      <view class="info-item">
        <text class="info-label">创建时间</text>
        <text class="info-value">{{ formatDateTime(order.created_at) }}</text>
      </view>
      <view class="info-item">
        <text class="info-label">当前状态</text>
        <view class="status-badge" :class="'status-' + order.status">
          <text class="status-text">{{ statusLabel(order.status) }}</text>
        </view>
      </view>
      <view v-if="order.cancelled_by" class="info-item">
        <text class="info-label">取消方</text>
        <text class="info-value">{{ order.cancelled_by === 'buyer' ? '买家' : '卖家' }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue';
import { resolveImageUrl } from '@/api/index';
import { formatPrice, formatDateTime } from '@/utils/format';
import SafeImage from '@/components/SafeImage.vue';

const props = defineProps({
  order: { type: Object, required: true },
  isBuyer: { type: Boolean, required: true },
});

function statusLabel(status) {
  const map = {
    pending: '待面交',
    met: '已面交',
    completed: '已完成',
    cancelled: '已取消',
  };
  return map[status] || status;
}

// ── 商品快照解析 ──
function parseSnapshot() {
  if (!props.order?.product_snapshot) return {};
  try {
    return typeof props.order.product_snapshot === 'string'
      ? JSON.parse(props.order.product_snapshot)
      : props.order.product_snapshot;
  } catch (err) {
    console.warn('[OrderSummaryCard] 解析商品快照失败:', err);
    return {};
  }
}

const productImage = computed(() => {
  const s = parseSnapshot();
  const images = s.images;
  return Array.isArray(images) && images.length > 0 ? images[0] : '';
});
const productTitle = computed(() => parseSnapshot().title || '商品');
const productCondition = computed(() => parseSnapshot().condition || '');
const productPrice = computed(() => parseSnapshot().price || 0);
const productLocation = computed(() => parseSnapshot().trade_location || '');

const partnerName = computed(() => {
  if (!props.order) return '';
  if (props.isBuyer) return props.order.seller_nickname || '卖家';
  return props.order.buyer_nickname || '买家';
});
const partnerAvatar = computed(() => {
  if (!props.order) return '';
  if (props.isBuyer) return props.order.seller_avatar || '';
  return props.order.buyer_avatar || '';
});

function goProduct() {
  if (props.order?.product_id) {
    uni.navigateTo({ url: `/pages/product/detail?id=${props.order.product_id}` });
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.section-card {
  background: $color-surface;
  margin: $space-card $space-page;
  padding: $space-card;
  border-radius: $radius-card;
  box-shadow: $shadow-card;
}

.section-title {
  font-size: $text-base;
  font-weight: $weight-medium;
  color: $color-title;
  margin-bottom: $space-content;
  display: block;
}

// ── 商品信息 ──
.product-row {
  display: flex;
  align-items: center;
  gap: $space-card;
}

.product-image {
  width: 120rpx;
  height: 120rpx;
  border-radius: $radius-card;
  flex-shrink: 0;
  background: $color-divider;
}

.product-info {
  flex: 1;
  overflow: hidden;
}

.product-title {
  font-size: $text-base;
  font-weight: $weight-medium;
  color: $color-title;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}

.product-condition {
  font-size: $text-sm;
  color: $color-muted;
  margin-top: 4rpx;
}

.product-price-row {
  margin-top: 4rpx;
}

.product-price {
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: $color-error;
  font-family: $font-mono;
}

.product-location {
  font-size: $text-sm;
  color: $color-muted;
  margin-top: 2rpx;
}

.arrow-right {
  font-size: 40rpx;
  color: $color-divider;
  flex-shrink: 0;
}

// ── 交易对象 ──
.partner-row {
  display: flex;
  align-items: center;
  gap: $space-content;
}

.partner-avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: $color-divider;
  flex-shrink: 0;

  &--default {
    display: flex;
    align-items: center;
    justify-content: center;
  }
}

.partner-avatar-emoji {
  font-size: 36rpx;
}

.partner-name {
  font-size: $text-base;
  color: $color-title;
  font-weight: $weight-medium;
}

// ── 订单信息 ──
.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: $space-content 0;

  & + & {
    border-top: 1rpx solid $color-divider;
  }
}

.info-label {
  font-size: $text-sm;
  color: $color-muted;
}

.info-value {
  font-size: $text-sm;
  color: $color-body;
}

// 状态标签
.status-badge {
  padding: 2rpx 16rpx;
  border-radius: $radius-full;

  &.status-pending {
    background: #E6F7FF;
    .status-text { color: #1890FF; }
  }
  &.status-met {
    background: #FFF7E6;
    .status-text { color: #FA8C16; }
  }
  &.status-completed {
    background: #F6FFED;
    .status-text { color: #52C41A; }
  }
  &.status-cancelled {
    background: #F5F5F5;
    .status-text { color: $color-muted; }
  }
}

.status-text {
  font-size: $text-xs;
  font-weight: $weight-medium;
}
</style>

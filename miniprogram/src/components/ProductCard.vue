<template>
  <view class="product-card" @click="goDetail">
    <!-- 封面图 -->
    <view class="card-image-wrap">
      <SafeImage
        class="card-image"
        :src="resolveImageUrl(product.cover_image)"
        mode="aspectFill"
        :lazy-load="true"
        :placeholder="placeholderImage"
      />
      <!-- 成色标签 -->
      <view v-if="product.condition" class="card-condition-tag">
        {{ product.condition }}
      </view>
    </view>

    <!-- 信息区 -->
    <view class="card-body">
      <!-- 标题（最多 2 行） -->
      <text class="card-title">
        {{ product.title }}
      </text>

      <!-- 价格行 -->
      <view class="card-price-row">
        <text class="card-price">
          ¥{{ formatPrice(product.price) }}
        </text>
        <text v-if="product.original_price && product.original_price > product.price" class="card-original-price">
          ¥{{ formatPrice(product.original_price) }}
        </text>
      </view>

      <!-- 底部信息 -->
      <view class="card-footer">
        <text class="card-location">
          {{ product.trade_location }}
        </text>
        <text v-if="product.negotiable" class="card-negotiable">
          可议价
        </text>
      </view>
    </view>
  </view>
</template>

<script setup>
/**
 * ProductCard — 商品卡片组件（瀑布流双列布局）
 *
 * Props:
 *   product: Object — 商品对象（含 cover_image, title, price, original_price, condition, trade_location, negotiable）
 *
 * Events:
 *   click — 点击跳转商品详情
 */

import { resolveImageUrl } from '@/api/index';
import SafeImage from '@/components/SafeImage.vue';

const props = defineProps({
  product: {
    type: Object,
    required: true,
  },
});

const emit = defineEmits(['click']);

/**
 * 缺省占位图：灰色 SVG 矩形，与 card-image-wrap 的 $color-divider 背景一致
 * 当 product.cover_image 为 null/undefined 时显示，避免渲染破损图片图标
 */
const placeholderImage =
  'data:image/svg+xml,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200"><rect fill="#F0F0F0" width="200" height="200"/></svg>'
  );

/**
 * 格式化价格（保留最多 2 位小数，去掉多余的 0）
 */
function formatPrice(price) {
  if (price == null) return '0';
  return parseFloat(price).toFixed(2).replace(/\.00$/, '');
}

/**
 * 点击跳转商品详情
 */
function goDetail() {
  if (props.product.id) {
    uni.navigateTo({ url: `/pages/product/detail?id=${props.product.id}` });
  }
  emit('click', props.product);
}
</script>

<style lang="scss">
@import '@/styles/tokens.scss';

.product-card {
  width: 100%;
  background: $color-surface;
  border-radius: $radius-card;
  overflow: hidden;
  box-shadow: $shadow-card;
  transition: transform 0.15s ease;

  &:active {
    transform: scale(0.97);
  }
}

// ── 封面图 ──────────────────────────────────────────────
.card-image-wrap {
  position: relative;
  width: 100%;
  padding-top: 100%;       // 1:1 正方形
  background: $color-divider;
  overflow: hidden;
}

.card-image {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.card-condition-tag {
  position: absolute;
  bottom: 8rpx;
  left: 8rpx;
  padding: 4rpx 12rpx;
  background: rgba(0, 0, 0, 0.55);
  border-radius: $radius-card;
  font-size: $text-xs;
  color: #FFFFFF;
  line-height: 1.4;
}

// ── 信息区 ──────────────────────────────────────────────
.card-body {
  padding: 16rpx;
}

.card-title {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  font-size: $text-sm;
  font-weight: $weight-medium;
  color: $color-title;
  line-height: 1.4;
  margin-bottom: 12rpx;
  min-height: 68rpx;
}

// ── 价格行 ──────────────────────────────────────────────
.card-price-row {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
  margin-bottom: 12rpx;
}

.card-price {
  font-size: $text-base;
  font-weight: $weight-bold;
  color: $color-error;
  font-family: $font-mono;
}

.card-original-price {
  font-size: $text-xs;
  color: $color-muted;
  text-decoration: line-through;
}

// ── 底部信息 ────────────────────────────────────────────
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-location {
  font-size: $text-xs;
  color: $color-muted;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-negotiable {
  font-size: 20rpx;
  color: $color-primary;
  padding: 2rpx 8rpx;
  border: 1px solid $color-primary-light;
  border-radius: 4rpx;
  flex-shrink: 0;
  margin-left: 8rpx;
}
</style>

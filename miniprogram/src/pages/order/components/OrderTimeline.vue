<template>
  <view class="section-card">
    <text class="section-title">
      订单进度
    </text>
    <view class="timeline">
      <!-- 节点 1：已下单 -->
      <view class="tl-node" :class="{ 'tl-node--active': timelineActive(0) }">
        <view class="tl-dot" />
        <view class="tl-content">
          <text class="tl-label">已下单</text>
          <text class="tl-time">{{ formatDateTime(order.created_at) }}</text>
        </view>
      </view>
      <!-- 节点 2：已面交 -->
      <view class="tl-node" :class="{ 'tl-node--active': timelineActive(1) }">
        <view class="tl-dot" />
        <view class="tl-content">
          <text class="tl-label">已面交</text>
          <text v-if="order.met_at" class="tl-time">{{ formatDateTime(order.met_at) }}</text>
          <text v-else class="tl-time tl-time--pending">待完成</text>
        </view>
      </view>
      <!-- 节点 3：已确认收货 -->
      <view class="tl-node" :class="{ 'tl-node--active': timelineActive(2) }">
        <view class="tl-dot" />
        <view class="tl-content">
          <text class="tl-label">已确认收货</text>
          <text v-if="order.confirmed_at" class="tl-time">{{ formatDateTime(order.confirmed_at) }}</text>
          <text v-else class="tl-time tl-time--pending">待完成</text>
        </view>
      </view>
      <!-- 节点 4：已评价 -->
      <view class="tl-node" :class="{ 'tl-node--active': timelineActive(3) }">
        <view class="tl-dot" />
        <view class="tl-content">
          <text class="tl-label">{{ reviewCompletion.label }}</text>
          <text v-if="reviewCompletion.done === 1" class="tl-time tl-time--pending">等待对方评价</text>
          <text v-else-if="reviewCompletion.done === 0" class="tl-time tl-time--pending">待完成</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue';
import { formatDateTime } from '@/utils/format';

const props = defineProps({
  order: { type: Object, required: true },
  reviews: { type: Array, default: () => [] },
});

const STATUS = {
  PENDING: 'pending',
  MET: 'met',
  COMPLETED: 'completed',
};

/** 双方互评完成度 */
const reviewCompletion = computed(() => {
  const total = props.reviews.length;
  if (total === 0) return { done: 0, total: 2, active: false, label: '待评价' };
  if (total === 1) return { done: 1, total: 2, active: false, label: '已评价 (1/2)' };
  return { done: 2, total: 2, active: true, label: '已完成互评' };
});

/** 时间线节点激活判断 */
function timelineActive(nodeIndex) {
  const order = props.order;
  if (!order) return false;
  const status = order.status;
  if (nodeIndex === 0) return true;
  if (nodeIndex === 1) return status === STATUS.MET || status === STATUS.COMPLETED;
  if (nodeIndex === 2) return status === STATUS.COMPLETED;
  if (nodeIndex === 3) return reviewCompletion.value.active;
  return false;
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

.timeline {
  padding-left: 8rpx;
}

.tl-node {
  display: flex;
  align-items: flex-start;
  padding-bottom: $space-card;

  &:last-child {
    padding-bottom: 0;
  }
}

.tl-dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
  background: $color-divider;
  margin-top: 4rpx;
  margin-right: $space-content;
  flex-shrink: 0;
  position: relative;

  &::after {
    content: '';
    position: absolute;
    top: 24rpx;
    left: 9rpx;
    width: 2rpx;
    height: calc(100% + 8rpx);
    background: $color-divider;
  }

  .tl-node:last-child &::after {
    display: none;
  }
}

.tl-node--active {
  .tl-dot {
    background: $color-primary;
    box-shadow: 0 0 0 4rpx $color-primary-light;

    &::after {
      background: $color-primary;
    }
  }
}

.tl-content {
  flex: 1;
}

.tl-label {
  font-size: $text-base;
  color: $color-muted;
  display: block;

  .tl-node--active & {
    color: $color-title;
    font-weight: $weight-medium;
  }
}

.tl-time {
  font-size: $text-sm;
  color: $color-muted;
  margin-top: 2rpx;
  display: block;

  &--pending {
    color: $color-divider;
  }
}
</style>

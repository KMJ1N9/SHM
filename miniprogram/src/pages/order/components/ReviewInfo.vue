<template>
  <view v-if="reviews.length > 0" class="section-card">
    <text class="section-title">评价信息</text>

    <!-- 互评进度条 -->
    <view class="review-progress">
      <view class="review-progress-bar">
        <view
          class="review-progress-fill"
          :style="{ width: (reviewCompletion.done / 2 * 100) + '%' }"
        />
      </view>
      <text class="review-progress-text">{{ reviewCompletion.done }}/2</text>
    </view>
    <text v-if="reviewCompletion.done === 1" class="review-progress-hint">
      {{ myReview ? '对方尚未评价，评价后双方可见' : '你尚未评价，请先完成评价' }}
    </text>
    <text v-else-if="reviewCompletion.done === 0" class="review-progress-hint">
      双方完成评价后可见评价详情
    </text>

    <!-- 已有评价列表 -->
    <view v-for="rv in reviews" :key="rv.id" class="review-item">
      <view class="review-item-header">
        <SafeImage
          v-if="rv.reviewer_avatar"
          class="review-item-avatar"
          :src="resolveImageUrl(rv.reviewer_avatar)"
          mode="aspectFill"
        />
        <view v-else class="review-item-avatar review-item-avatar--default">
          <text class="review-item-avatar-emoji">👤</text>
        </view>
        <text class="review-item-name">{{ rv.reviewer_nickname || '匿名用户' }}</text>
        <text class="review-item-time">{{ formatDateTime(rv.created_at) }}</text>
      </view>
      <!-- 三维评分 -->
      <view class="review-item-scores">
        <view class="review-dim-item">
          <text class="review-dim-label">沟通</text>
          <StarRating :model-value="rv.communication_score" :readonly="true" size="sm" />
        </view>
        <view class="review-dim-item">
          <text class="review-dim-label">守时</text>
          <StarRating :model-value="rv.punctuality_score" :readonly="true" size="sm" />
        </view>
        <view class="review-dim-item">
          <text class="review-dim-label">描述</text>
          <StarRating :model-value="rv.accuracy_score" :readonly="true" size="sm" />
        </view>
      </view>
      <!-- 文字评价 -->
      <text v-if="rv.comment" class="review-item-comment">"{{ rv.comment }}"</text>
    </view>
  </view>
</template>

<script setup>
import { formatDateTime } from '@/utils/format';
import { resolveImageUrl } from '@/api/index';
import StarRating from '@/components/StarRating.vue';
import SafeImage from '@/components/SafeImage.vue';

defineProps({
  reviews: { type: Array, default: () => [] },
  reviewCompletion: { type: Object, default: () => ({ done: 0, total: 2, active: false, label: '待评价' }) },
  myReview: { type: Object, default: null },
});
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

.review-progress {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: $space-content;
}

.review-progress-bar {
  flex: 1;
  height: 12rpx;
  background: $color-divider;
  border-radius: 6rpx;
  overflow: hidden;
}

.review-progress-fill {
  height: 100%;
  background: $color-primary-gradient;
  border-radius: 6rpx;
  transition: width 0.4s ease;
  min-width: 0;
}

.review-progress-text {
  font-size: $text-sm;
  color: $color-muted;
  font-family: $font-mono;
  flex-shrink: 0;
}

.review-progress-hint {
  display: block;
  font-size: $text-xs;
  color: $color-muted;
  margin-bottom: $space-content;
  padding-bottom: $space-content;
  border-bottom: 1rpx solid $color-divider;
}

.review-item {
  padding: 24rpx 0;
  border-bottom: 1rpx solid $color-divider;

  &:last-child {
    border-bottom: none;
    padding-bottom: 0;
  }
}

.review-item-header {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.review-item-avatar {
  width: 48rpx;
  height: 48rpx;
  border-radius: $radius-full;
  flex-shrink: 0;

  &--default {
    background: $color-bg;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &-emoji {
    font-size: 28rpx;
  }
}

.review-item-name {
  font-size: $text-sm;
  font-weight: $weight-medium;
  color: $color-body;
  flex: 1;
}

.review-item-time {
  font-size: $text-xs;
  color: $color-muted;
  flex-shrink: 0;
}

.review-item-scores {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-top: 16rpx;
}

.review-dim-item {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.review-dim-label {
  font-size: $text-xs;
  color: $color-muted;
}

.review-item-comment {
  display: block;
  margin-top: 16rpx;
  font-size: $text-sm;
  color: $color-body;
  font-style: italic;
  line-height: 1.6;
}
</style>

<template>
  <view class="profile-page">
    <!-- 加载/错误状态 -->
    <view v-if="loading" class="state-center">
      <text class="state-text">加载中...</text>
    </view>

    <template v-else-if="error">
      <view class="state-center">
        <text class="error-emoji">😵</text>
        <text class="error-text">{{ error }}</text>
        <button class="retry-btn" @click="loadProfile">重试</button>
      </view>
    </template>

    <template v-else-if="profile">
      <!-- 用户头部 -->
      <view class="profile-header">
        <image
          v-if="profile.avatar"
          class="profile-avatar"
          :src="profile.avatar"
          mode="aspectFill"
        />
        <view v-else class="profile-avatar profile-avatar--default">
          <text class="profile-avatar-emoji">👤</text>
        </view>
        <text class="profile-name">{{ profile.nickname || '匿名用户' }}</text>
        <text v-if="profile.class_name || profile.dorm_building" class="profile-meta">
          {{ [profile.class_name, profile.dorm_building].filter(Boolean).join(' · ') }}
        </text>
        <view class="profile-credit-badge">
          <text class="profile-credit-score">{{ creditScore }}</text>
          <text class="profile-credit-label">信誉分</text>
        </view>
      </view>

      <!-- 评价汇总 -->
      <view v-if="reviews.summary && reviews.summary.total > 0" class="section-card">
        <text class="section-title">评价汇总</text>
        <view class="summary-row">
          <text class="summary-total">共 {{ reviews.summary.total }} 条评价</text>
        </view>
        <view class="summary-scores">
          <view class="summary-dim">
            <text class="summary-dim-label">沟通</text>
            <text class="summary-dim-value">{{ formatAvg(reviews.summary.avg_communication) }}</text>
          </view>
          <view class="summary-dim">
            <text class="summary-dim-label">守时</text>
            <text class="summary-dim-value">{{ formatAvg(reviews.summary.avg_punctuality) }}</text>
          </view>
          <view class="summary-dim">
            <text class="summary-dim-label">描述</text>
            <text class="summary-dim-value">{{ formatAvg(reviews.summary.avg_accuracy) }}</text>
          </view>
        </view>
      </view>

      <!-- 历史评价 -->
      <view class="section-card">
        <text class="section-title">历史评价</text>
        <view v-if="reviews.list && reviews.list.length > 0">
          <view
            v-for="rv in reviews.list"
            :key="rv.id"
            class="review-item"
          >
            <view class="review-item-header">
              <image
                v-if="rv.reviewer_avatar"
                class="review-item-avatar"
                :src="rv.reviewer_avatar"
                mode="aspectFill"
              />
              <view v-else class="review-item-avatar review-item-avatar--default">
                <text>👤</text>
              </view>
              <text class="review-item-name">{{ rv.reviewer_nickname || '匿名用户' }}</text>
              <text class="review-item-time">{{ formatTime(rv.created_at) }}</text>
            </view>
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
            <text v-if="rv.comment" class="review-item-comment">"{{ rv.comment }}"</text>
          </view>
        </view>
        <view v-else class="review-empty">
          <text class="review-empty-text">暂无评价</text>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
/**
 * 用户个人主页 — 公开信息 + 信誉分 + 评价汇总 + 历史评价
 *
 * 入口：
 *   - 商品详情页点击卖家头像
 *   - 订单详情页点击交易对方
 *   - 聊天页点击对方头像
 *
 * URL 参数：?id=userId
 */
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { getPublicProfile } from '@/api/user';
import { getUserCredit } from '@/api/credit';
import { getUserReviews } from '@/api/review';
import StarRating from '@/components/StarRating.vue';

const profile = ref(null);
const creditScore = ref(0);
const reviews = ref({ summary: null, list: [] });
const loading = ref(true);
const error = ref('');

onLoad((options) => {
  const userId = options.id;
  if (!userId) {
    error.value = '缺少用户 ID';
    loading.value = false;
    return;
  }
  loadProfile(userId);
});

async function loadProfile(uid) {
  const userId = uid || profile.value?.id;
  if (!userId) return;

  loading.value = true;
  error.value = '';

  try {
    const [profileData, creditData, reviewData] = await Promise.all([
      getPublicProfile(userId),
      getUserCredit(userId).catch(() => ({ score: 0 })),
      getUserReviews(userId, { page: 1, pageSize: 20 }).catch(() => ({ summary: null, list: [] })),
    ]);
    profile.value = profileData;
    creditScore.value = creditData.score ?? profileData.credit_score ?? 0;
    reviews.value = reviewData;
  } catch (err) {
    error.value = err.message || '加载失败';
  } finally {
    loading.value = false;
  }
}

function formatAvg(val) {
  if (val == null) return '—';
  const n = parseFloat(val);
  if (Number.isNaN(n)) return '—';
  return n.toFixed(1);
}

function formatTime(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  const m = d.getMonth() + 1;
  const day = d.getDate();
  return `${m}-${day}`;
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.profile-page {
  min-height: 100vh;
  background: $color-bg;
  padding-bottom: calc(80rpx + $safe-area-bottom);
}

// ── 状态 ──────────────────────────────────────────────
.state-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 80vh;
  padding: $space-page;
}

.state-text {
  font-size: $text-base;
  color: $color-muted;
}

.error-emoji {
  font-size: 96rpx;
  margin-bottom: $space-card;
}

.error-text {
  font-size: $text-base;
  color: $color-muted;
  margin-bottom: $space-card;
}

.retry-btn {
  padding: 12rpx 48rpx;
  font-size: $text-base;
  color: $color-primary;
  background: $color-surface;
  border: 1rpx solid $color-primary;
  border-radius: $radius-full;
}

// ── 头部 ──────────────────────────────────────────────
.profile-header {
  background: $color-primary-gradient;
  padding: 80rpx $space-page 48rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.profile-avatar {
  width: 160rpx;
  height: 160rpx;
  border-radius: 50%;
  border: 4rpx solid rgba(255, 255, 255, 0.3);
  background: $color-divider;
}

.profile-avatar--default {
  display: flex;
  align-items: center;
  justify-content: center;
}

.profile-avatar-emoji {
  font-size: 64rpx;
}

.profile-name {
  font-size: $text-xl;
  font-weight: $weight-bold;
  color: #FFFFFF;
  margin-top: 24rpx;
}

.profile-meta {
  font-size: $text-sm;
  color: rgba(255, 255, 255, 0.8);
  margin-top: 8rpx;
}

.profile-credit-badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  background: rgba(255, 255, 255, 0.15);
  border-radius: $radius-card;
  padding: 16rpx 32rpx;
  margin-top: 24rpx;
}

.profile-credit-score {
  font-size: $text-3xl;
  font-weight: $weight-bold;
  color: #FFFFFF;
  font-family: $font-mono;
}

.profile-credit-label {
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.8);
  margin-top: 4rpx;
}

// ── 区块卡片 ──────────────────────────────────────────
.section-card {
  background: $color-surface;
  margin: 16rpx 0 0;
  padding: 24rpx $space-page;
}

.section-title {
  font-size: $text-base;
  font-weight: $weight-medium;
  color: $color-title;
  margin-bottom: 20rpx;
  display: block;
}

// ── 评价汇总 ──────────────────────────────────────────
.summary-row {
  margin-bottom: 16rpx;
}

.summary-total {
  font-size: $text-sm;
  color: $color-muted;
}

.summary-scores {
  display: flex;
  gap: 32rpx;
}

.summary-dim {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.summary-dim-label {
  font-size: $text-xs;
  color: $color-muted;
}

.summary-dim-value {
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: $color-primary;
  font-family: $font-mono;
}

// ── 评价列表 ──────────────────────────────────────────
.review-item {
  padding: 20rpx 0;
  border-bottom: 1rpx solid $color-divider;

  &:last-child { border-bottom: none; }
}

.review-item-header {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 12rpx;
}

.review-item-avatar {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: $color-divider;
  flex-shrink: 0;
}

.review-item-avatar--default {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
}

.review-item-name {
  flex: 1;
  font-size: $text-sm;
  color: $color-title;
  font-weight: $weight-medium;
}

.review-item-time {
  font-size: $text-xs;
  color: $color-muted;
}

.review-item-scores {
  display: flex;
  gap: 24rpx;
  margin-bottom: 8rpx;
}

.review-dim-item {
  display: flex;
  align-items: center;
  gap: 6rpx;
}

.review-dim-label {
  font-size: $text-xs;
  color: $color-muted;
}

.review-item-comment {
  font-size: $text-sm;
  color: $color-body;
  font-style: italic;
  line-height: 1.5;
}

.review-empty {
  padding: 40rpx 0;
  text-align: center;
}

.review-empty-text {
  font-size: $text-sm;
  color: $color-muted;
}
</style>

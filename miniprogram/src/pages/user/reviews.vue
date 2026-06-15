<template>
  <view class="reviews-page">
    <!-- 加载中 -->
    <view v-if="loading" class="state-center">
      <text class="state-text">
        加载中...
      </text>
    </view>

    <!-- 加载失败 -->
    <view v-else-if="errorMsg" class="state-center">
      <text class="error-icon">
        😕
      </text>
      <text class="error-text">
        {{ errorMsg }}
      </text>
      <button class="retry-btn" @click="loadReviews">
        重新加载
      </button>
    </view>

    <template v-else>
      <!-- ============================================================ -->
      <!-- 评价汇总 -->
      <!-- ============================================================ -->
      <view v-if="summary" class="summary-card">
        <text class="summary-count">
          共 {{ summary.total || 0 }} 条评价
        </text>
        <view class="summary-scores">
          <view class="summary-dim">
            <text class="dim-label">
              沟通态度
            </text>
            <StarRating
              :model-value="Math.round(summary.avg_communication || 0)"
              :readonly="true"
              size="sm"
            />
          </view>
          <view class="summary-dim">
            <text class="dim-label">
              守时程度
            </text>
            <StarRating
              :model-value="Math.round(summary.avg_punctuality || 0)"
              :readonly="true"
              size="sm"
            />
          </view>
          <view class="summary-dim">
            <text class="dim-label">
              描述一致度
            </text>
            <StarRating
              :model-value="Math.round(summary.avg_accuracy || 0)"
              :readonly="true"
              size="sm"
            />
          </view>
        </view>
      </view>

      <!-- ============================================================ -->
      <!-- 评价列表 -->
      <!-- ============================================================ -->
      <scroll-view
        v-if="reviews.length > 0"
        class="review-scroll"
        scroll-y
        refresher-enabled
        :refresher-triggered="refreshing"
        :lower-threshold="100"
        @refresherrefresh="onRefresh"
        @scrolltolower="loadMore"
      >
        <view
          v-for="item in reviews"
          :key="item.id"
          class="review-card"
        >
          <view class="review-header">
            <SafeImage
              v-if="item.reviewer_avatar"
              class="review-avatar"
              :src="resolveImageUrl(item.reviewer_avatar)"
              mode="aspectFill"
              :lazy-load="true"
            />
            <view v-else class="review-avatar review-avatar--default">
              <text class="review-avatar-emoji">
                👤
              </text>
            </view>
            <text class="review-name">
              {{ item.reviewer_nickname || '匿名用户' }}
            </text>
            <text class="review-time">
              {{ formatTime(item.created_at) }}
            </text>
          </view>

          <!-- 三维评分 -->
          <view class="review-scores">
            <view class="rscore-item">
              <text class="rscore-label">
                沟通态度
              </text>
              <StarRating
                :model-value="item.communication_score"
                :readonly="true"
                size="sm"
              />
            </view>
            <view class="rscore-item">
              <text class="rscore-label">
                守时程度
              </text>
              <StarRating
                :model-value="item.punctuality_score"
                :readonly="true"
                size="sm"
              />
            </view>
            <view class="rscore-item">
              <text class="rscore-label">
                描述一致度
              </text>
              <StarRating
                :model-value="item.accuracy_score"
                :readonly="true"
                size="sm"
              />
            </view>
          </view>

          <!-- 文字评价 -->
          <text v-if="item.comment" class="review-comment">
            "{{ item.comment }}"
          </text>
        </view>

        <!-- 加载更多 -->
        <view v-if="loadingMore" class="loadmore-tip">
          <text class="loadmore-text">
            加载中...
          </text>
        </view>
        <view v-else-if="!hasMore && reviews.length > 0" class="loadmore-tip">
          <text class="loadmore-text">
            没有更多了
          </text>
        </view>
      </scroll-view>

      <!-- 空状态 -->
      <view v-else class="empty-state">
        <text class="empty-emoji">
          ⭐
        </text>
        <text class="empty-title">
          暂无评价
        </text>
        <text class="empty-desc">
          完成交易后，双方可以互相评价
        </text>
      </view>
    </template>
  </view>
</template>

<script setup>
/**
 * 评价记录页 — 三维度汇总统计 + 评价列表
 *
 * 使用游标分页（cursor/limit）替代偏移分页（page/pageSize），
 * O(1) 定位避免 OFFSET 大页码退化。
 * 游标 = 上一页最后一条 id，首页传 null。
 * summary 聚合统计始终为全量，不受 cursor 影响。
 */

import { ref, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getUserReviews } from '@/api/review';
import { useUserStore } from '@/store/user';
import { resolveImageUrl } from '@/api/index';
import StarRating from '@/components/StarRating.vue';
import SafeImage from '@/components/SafeImage.vue';

// ============================================================
// 数据状态
// ============================================================
const summary = ref(null);
const reviews = ref([]);
const loading = ref(true);
const errorMsg = ref('');
const refreshing = ref(false);
const loadingMore = ref(false);
const cursor = ref(null);
const limit = 20;
const hasMore = ref(true);

const userStore = useUserStore();

// ============================================================
// 数据加载
// ============================================================
async function fetchReviews(reset = false) {
  if (reset) {
    cursor.value = null;
    hasMore.value = true;
  }

  if (!reset && !hasMore.value) return;

  try {
    const params = { limit };
    if (!reset && cursor.value) {
      params.cursor = cursor.value;
    }

    const result = await getUserReviews(userStore.user.id, params);

    summary.value = result.summary || null;

    if (reset) {
      reviews.value = result.list || [];
    } else {
      reviews.value.push(...(result.list || []));
    }

    cursor.value = result.cursor || null;
    hasMore.value = result.hasMore ?? false;
  } catch (err) {
    uni.showToast({ title: err.message || '加载失败', icon: 'none' });
  }
}

// ============================================================
// 交互
// ============================================================
async function onRefresh() {
  refreshing.value = true;
  await fetchReviews(true);
  refreshing.value = false;
}

async function loadMore() {
  if (loadingMore.value || !hasMore.value) return;
  loadingMore.value = true;
  await fetchReviews(false);
  loadingMore.value = false;
}

// ============================================================
// 生命周期
// ============================================================
onMounted(() => {
  loading.value = true;
  fetchReviews(true).finally(() => {
    loading.value = false;
  });
});

onShow(() => {
  if (!loading.value) {
    fetchReviews(true);
  }
});

// ============================================================
// 辅助函数
// ============================================================
function formatTime(isoString) {
  if (!isoString) return '';
  const d = new Date(isoString);
  const now = new Date();
  const diff = now - d;

  if (diff < 60000) return '刚刚';
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`;
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`;

  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${month}-${day}`;
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.reviews-page {
  min-height: 100vh;
  background: $color-bg;
  padding-bottom: $space-page;
}

// ── 通用状态 ──
.state-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 80vh;
  padding: $space-page;
}

.error-icon {
  font-size: 96rpx;
  margin-bottom: $space-card;
}

.error-text {
  font-size: $text-base;
  color: $color-muted;
  margin-bottom: $space-card;
}

.state-text {
  font-size: $text-base;
  color: $color-muted;
}

.retry-btn {
  padding: 12rpx 48rpx;
  font-size: $text-base;
  color: $color-primary;
  background: $color-surface;
  border: 1rpx solid $color-primary;
  border-radius: $radius-full;
}

// ── 汇总卡片 ──
.summary-card {
  background: $color-primary-gradient;
  margin: $space-card $space-page;
  padding: 32rpx;
  border-radius: $radius-card;
}

.summary-count {
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: #FFFFFF;
  display: block;
  text-align: center;
  margin-bottom: $space-card;
}

.summary-scores {
  display: flex;
  justify-content: space-around;
}

.summary-dim {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.dim-label {
  font-size: $text-xs;
  color: rgba(255, 255, 255, 0.8);
}

// ── 评价卡片 ──
.review-card {
  background: $color-surface;
  margin: 0 $space-page $space-card;
  padding: $space-card;
  border-radius: $radius-card;
  box-shadow: $shadow-card;
}

.review-header {
  display: flex;
  align-items: center;
  gap: $space-content;
  margin-bottom: $space-content;
}

.review-avatar {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: $color-divider;
  flex-shrink: 0;

  &--default {
    display: flex;
    align-items: center;
    justify-content: center;
  }
}

.review-avatar-emoji {
  font-size: 32rpx;
}

.review-name {
  flex: 1;
  font-size: $text-sm;
  color: $color-title;
  font-weight: $weight-medium;
}

.review-time {
  font-size: $text-xs;
  color: $color-muted;
}

// 三维评分
.review-scores {
  display: flex;
  gap: $space-card;
  margin-bottom: $space-content;
}

.rscore-item {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.rscore-label {
  font-size: $text-xs;
  color: $color-muted;
}

// 文字评价
.review-comment {
  font-size: $text-sm;
  color: $color-body;
  line-height: $line-height;
  display: block;
  padding-left: 4rpx;
  border-left: 4rpx solid $color-primary-light;
}

// ── 空状态 ──
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 120rpx $space-page;
}

.empty-emoji {
  font-size: 96rpx;
  margin-bottom: $space-card;
}

.empty-title {
  font-size: $text-lg;
  color: $color-title;
  font-weight: $weight-medium;
  margin-bottom: $space-content;
}

.empty-desc {
  font-size: $text-sm;
  color: $color-muted;
}

// ── 加载更多 ──
.loadmore-tip {
  text-align: center;
  padding: $space-card 0;
}

.loadmore-text {
  font-size: $text-sm;
  color: $color-muted;
}
</style>

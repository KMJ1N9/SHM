<template>
  <view class="report-list-page">
    <!-- ============================================================ -->
    <!-- 加载/错误/空态 -->
    <!-- ============================================================ -->
    <view v-if="loading && list.length === 0" class="status-center">
      <text class="status-text">加载中...</text>
    </view>

    <view v-else-if="errorMsg && list.length === 0" class="status-center">
      <text class="status-emoji">😵</text>
      <text class="status-text">{{ errorMsg }}</text>
      <button class="btn-retry" @click="loadList(true)">重试</button>
    </view>

    <view v-else-if="list.length === 0" class="status-center">
      <text class="status-emoji">📋</text>
      <text class="status-text">暂无举报记录</text>
    </view>

    <!-- ============================================================ -->
    <!-- 举报列表 -->
    <!-- ============================================================ -->
    <scroll-view
      v-else
      class="list-scroll"
      scroll-y
      refresher-enabled
      :refresher-triggered="refreshing"
      :lower-threshold="100"
      @refresherrefresh="onRefresh"
      @scrolltolower="loadMore"
    >
      <view
        v-for="item in list"
        :key="item.id"
        class="report-card"
        @click="goDetail(item.id)"
      >
        <!-- 头部：类型 + 状态 -->
        <view class="card-header">
          <text class="report-type-tag">{{ item.type }}</text>
          <text :class="['status-tag', statusClass(item.status)]">
            {{ statusLabel(item.status) }}
          </text>
        </view>

        <!-- 描述预览（截取前 80 字） -->
        <text class="card-desc">{{ item.description }}</text>

        <!-- 关联信息 -->
        <view v-if="item.product_id || item.order_id" class="card-meta">
          <text v-if="item.product_id" class="meta-item">关联商品 #{{ item.product_id }}</text>
          <text v-if="item.order_id" class="meta-item">关联订单 #{{ item.order_id }}</text>
        </view>

        <!-- 底部：被举报人 + 时间 -->
        <view class="card-footer">
          <text class="footer-reported">
            被举报人：{{ item.reported_nickname || '用户' + item.reported_user_id }}
          </text>
          <text class="footer-time">{{ formatTime(item.created_at) }}</text>
        </view>
      </view>

      <!-- 加载更多 -->
      <view v-if="hasMore" class="load-more">
        <text class="load-more-text">加载更多...</text>
      </view>
      <view v-else class="load-more">
        <text class="load-more-text">— 没有更多了 —</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
/**
 * 我的举报列表页 — 展示当前用户提交的所有举报
 *
 * 入口：我的页面 → 我的举报
 * 跳转：点击卡片 → 举报详情页（/pages/report/detail?id=X）
 */
import { ref, computed } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { getReportList } from '@/api/report';

// ============================================================
// 常量
// ============================================================

const PAGE_SIZE = 15;

const STATUS_CONFIG = {
  pending: { label: '待受理', cls: 'status--pending' },
  processing: { label: '处理中', cls: 'status--processing' },
  resolved: { label: '已处理', cls: 'status--resolved' },
};

// ============================================================
// 数据状态
// ============================================================

const list = ref([]);
const loading = ref(false);
const refreshing = ref(false);
const errorMsg = ref('');
let page = 1;
let total = 0;

// ============================================================
// 计算属性
// ============================================================

const hasMore = computed(() => list.value.length < total);

// ============================================================
// 状态映射
// ============================================================

function statusLabel(status) {
  return STATUS_CONFIG[status]?.label || status;
}

function statusClass(status) {
  return STATUS_CONFIG[status]?.cls || '';
}

// ============================================================
// 格式化时间
// ============================================================

function formatTime(isoString) {
  if (!isoString) return '';
  const d = new Date(isoString);
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  const hour = String(d.getHours()).padStart(2, '0');
  const min = String(d.getMinutes()).padStart(2, '0');
  return `${year}-${month}-${day} ${hour}:${min}`;
}

// ============================================================
// 数据加载
// ============================================================

async function loadList(reset = false) {
  if (loading.value) return;
  if (reset) {
    page = 1;
    total = 0;
  }
  loading.value = true;
  errorMsg.value = '';

  try {
    const result = await getReportList({ page, pageSize: PAGE_SIZE });
    if (reset) {
      list.value = result.list || [];
    } else {
      list.value = [...list.value, ...(result.list || [])];
    }
    total = result.total || 0;
  } catch (err) {
    if (reset) {
      errorMsg.value = err.message || '加载失败';
    } else {
      uni.showToast({ title: err.message || '加载失败', icon: 'none' });
    }
  } finally {
    loading.value = false;
    refreshing.value = false;
  }
}

// ============================================================
// 交互
// ============================================================

function onRefresh() {
  refreshing.value = true;
  loadList(true);
}

function loadMore() {
  if (!hasMore.value || loading.value) return;
  page++;
  loadList(false);
}

function goDetail(id) {
  uni.navigateTo({ url: `/pages/report/detail?id=${id}` });
}

// ============================================================
// 生命周期
// ============================================================

onLoad(() => {
  loadList(true);
});
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.report-list-page {
  min-height: 100vh;
  background: $color-bg;
}

// ── 加载/错误/空态居中 ──
.status-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 200rpx $space-page;
}

.status-emoji {
  font-size: 80rpx;
  margin-bottom: $space-card;
}

.status-text {
  font-size: $text-base;
  color: $color-muted;
}

.btn-retry {
  margin-top: $space-card;
  padding: 12rpx 48rpx;
  font-size: $text-sm;
  color: $color-primary;
  background: $color-primary-light;
  border-radius: $radius-full;
  border: none;
}

// ── 列表滚动区 ──
.list-scroll {
  height: 100vh;
}

// ── 举报卡片 ──
.report-card {
  background: $color-surface;
  margin: $space-content $space-page;
  padding: $space-card;
  border-radius: $radius-card;

  &:active {
    background: #F9F9F9;
  }
}

.card-header {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 12rpx;
}

.report-type-tag {
  font-size: $text-xs;
  color: #fff;
  background: $color-primary;
  padding: 4rpx 16rpx;
  border-radius: $radius-full;
}

.status-tag {
  font-size: $text-xs;
  padding: 4rpx 16rpx;
  border-radius: $radius-full;

  &--pending {
    color: $color-warning;
    background: #FFF7E6;
  }

  &--processing {
    color: $color-primary;
    background: $color-primary-light;
  }

  &--resolved {
    color: $color-success;
    background: #E6FFFA;
  }
}

.card-desc {
  font-size: $text-sm;
  color: $color-body;
  line-height: $line-height;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 8rpx;
}

.card-meta {
  display: flex;
  gap: 16rpx;
  margin-bottom: 8rpx;
}

.meta-item {
  font-size: $text-xs;
  color: $color-muted;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 8rpx;
  border-top: 1rpx solid $color-divider;
}

.footer-reported {
  font-size: $text-xs;
  color: $color-muted;
}

.footer-time {
  font-size: $text-xs;
  color: $color-muted;
}

// ── 加载更多 ──
.load-more {
  padding: 24rpx 0;
  text-align: center;
}

.load-more-text {
  font-size: $text-xs;
  color: $color-muted;
}
</style>

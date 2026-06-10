<template>
  <view class="dashboard-page">
    <!-- ============================================================ -->
    <!-- 权限拦截 — 非管理员不渲染任何内容 -->
    <!-- ============================================================ -->
    <view v-if="userRole !== 'admin'" class="status-center">
      <text class="status-text">仅管理员可访问</text>
    </view>

    <!-- ============================================================ -->
    <!-- 加载 / 错误状态 -->
    <!-- ============================================================ -->
    <view v-else-if="loading" class="loading-state">
      <text class="loading-text">加载中...</text>
    </view>

    <view v-else-if="errorMsg" class="error-state">
      <text class="error-emoji">📊</text>
      <text class="error-text">{{ errorMsg }}</text>
      <button class="btn-retry" @click="loadDashboard">重试</button>
    </view>

    <template v-else>
      <!-- ============================================================ -->
      <!-- 概览卡片 — 2×3 网格 -->
      <!-- ============================================================ -->
      <view class="section-title-text">平台概览</view>
      <view class="overview-grid">
        <view class="overview-card">
          <text class="overview-num">{{ data.total_users || 0 }}</text>
          <text class="overview-label">注册用户</text>
        </view>
        <view class="overview-card">
          <text class="overview-num">{{ data.total_products || 0 }}</text>
          <text class="overview-label">活跃商品</text>
        </view>
        <view class="overview-card">
          <text class="overview-num">{{ data.total_orders || 0 }}</text>
          <text class="overview-label">交易订单</text>
        </view>
        <view class="overview-card overview-card--warn">
          <text class="overview-num">{{ data.pending_reports || 0 }}</text>
          <text class="overview-label">待处理举报</text>
        </view>
        <view class="overview-card">
          <text class="overview-num overview-num--sm">+{{ data.new_users_7d || 0 }}</text>
          <text class="overview-label">近 7 天新用户</text>
        </view>
        <view class="overview-card">
          <text class="overview-num overview-num--sm">+{{ data.completed_orders_7d || 0 }}</text>
          <text class="overview-label">近 7 天交易</text>
        </view>
      </view>

      <!-- ============================================================ -->
      <!-- 热门分类 — 进度条 -->
      <!-- ============================================================ -->
      <view class="section-card">
        <text class="section-title">热门分类</text>
        <view v-if="data.hot_categories && data.hot_categories.length > 0">
          <view
            v-for="cat in data.hot_categories"
            :key="cat.category"
            class="category-row"
          >
            <view class="category-header">
              <text class="category-name">{{ cat.category }}</text>
              <text class="category-count">{{ cat.count }}</text>
            </view>
            <view class="category-bar">
              <view
                class="category-bar-fill"
                :style="{ width: getBarWidth(cat.count, maxCategoryCount) }"
              />
            </view>
          </view>
        </view>
        <view v-else class="empty-inline">
          <text class="empty-inline-text">暂无数据</text>
        </view>
      </view>

      <!-- ============================================================ -->
      <!-- 热门搜索词 — 标签云 -->
      <!-- ============================================================ -->
      <view class="section-card">
        <text class="section-title">热门搜索词</text>
        <view v-if="data.hot_keywords && data.hot_keywords.length > 0" class="keyword-cloud">
          <text
            v-for="kw in data.hot_keywords"
            :key="kw.keyword"
            class="keyword-tag"
          >{{ kw.keyword }}</text>
        </view>
        <view v-else class="empty-inline">
          <text class="empty-inline-text">暂无数据</text>
        </view>
      </view>

      <!-- ============================================================ -->
      <!-- 近 7 天每日订单 — CSS 柱状图 -->
      <!-- ============================================================ -->
      <view class="section-card">
        <text class="section-title">近 7 天每日新增订单</text>
        <view v-if="data.daily_orders_7d && data.daily_orders_7d.length > 0" class="bar-chart">
          <view
            v-for="day in data.daily_orders_7d"
            :key="day.date"
            class="bar-item"
          >
            <text class="bar-date">{{ formatDateLabel(day.date) }}</text>
            <view class="bar-track">
              <view
                class="bar-fill"
                :style="{ width: getBarWidth(day.count, maxDailyCount) }"
              />
            </view>
            <text class="bar-count">{{ day.count }}</text>
          </view>
        </view>
        <view v-else class="empty-inline">
          <text class="empty-inline-text">暂无数据</text>
        </view>
      </view>

      <!-- ============================================================ -->
      <!-- 敏感词库 -->
      <!-- ============================================================ -->
      <view class="section-card">
        <text class="section-title">敏感词库管理</text>
        <view class="sensitive-row">
          <text class="sensitive-info">
            词库共 <text class="sensitive-count">{{ sensitiveWordCount }}</text> 个敏感词
          </text>
          <button class="btn-reload" :loading="reloading" @click="handleReload">
            重载词库
          </button>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
/**
 * 数据看板页 — 管理后台首页，展示平台运营数据
 *
 * 数据源：
 *   - getDashboard() → 概览 + 热门分类 + 热门搜索词 + 近 7 天每日订单
 *   - getSensitiveStats() → 敏感词库统计
 *
 * 权限：仅 admin 角色可访问
 */
import { ref, computed } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getDashboard, getSensitiveStats, reloadSensitiveWords } from '@/api/admin';
import { useUserStore } from '@/store/user';

// ============================================================
// 权限校验（computed 确保 Pinia 恢复后响应式更新）
// ============================================================
const userStore = useUserStore();
const userRole = computed(() => userStore.user?.role || 'user');

if (userRole.value !== 'admin') {
  uni.showToast({ title: '仅管理员可访问', icon: 'none', duration: 2000 });
  setTimeout(() => uni.navigateBack(), 2000);
}

// ============================================================
// 数据状态
// ============================================================
const data = ref({});
const loading = ref(true);
const errorMsg = ref('');
const sensitiveWordCount = ref(0);
const reloading = ref(false);

// ============================================================
// 柱状图最大值（用于计算百分比宽度）
// ============================================================
const maxCategoryCount = computed(() => {
  if (!data.value.hot_categories?.length) return 1;
  return Math.max(...data.value.hot_categories.map(c => c.count));
});

const maxDailyCount = computed(() => {
  if (!data.value.daily_orders_7d?.length) return 1;
  return Math.max(...data.value.daily_orders_7d.map(d => d.count));
});

// ============================================================
// 辅助函数
// ============================================================
function getBarWidth(count, max) {
  if (!max || max === 0) return '0%';
  return Math.max(4, (count / max) * 100) + '%';
}

function formatDateLabel(dateStr) {
  if (!dateStr) return '';
  // dateStr 格式: "2024-12-01" 或 "2024-12-01T..."
  const parts = dateStr.split('T')[0].split('-');
  if (parts.length >= 3) {
    return `${parseInt(parts[1], 10)}/${parseInt(parts[2], 10)}`;
  }
  return dateStr;
}

// ============================================================
// 数据加载
// ============================================================
async function loadDashboard() {
  if (userRole.value !== 'admin') return;
  loading.value = true;
  errorMsg.value = '';
  try {
    const [dashData, sensitiveData] = await Promise.all([
      getDashboard(),
      getSensitiveStats().catch(() => ({ word_count: 0 })),
    ]);
    data.value = dashData;
    sensitiveWordCount.value = sensitiveData.word_count || 0;
  } catch (err) {
    errorMsg.value = err.message || '加载失败';
    data.value = {};
  } finally {
    loading.value = false;
  }
}

// ============================================================
// 重载敏感词库
// ============================================================
async function handleReload() {
  if (reloading.value) return;
  reloading.value = true;
  try {
    const result = await reloadSensitiveWords();
    sensitiveWordCount.value = result.word_count || 0;
    uni.showToast({ title: '词库已重载', icon: 'success' });
  } catch (err) {
    uni.showToast({ title: err.message || '重载失败', icon: 'none' });
  } finally {
    reloading.value = false;
  }
}

// ============================================================
// 生命周期
// ============================================================
onShow(() => {
  if (userRole.value !== 'admin') return;
  loadDashboard();
});
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.dashboard-page {
  min-height: 100vh;
  background: $color-bg;
  padding-bottom: $safe-area-bottom;
}

// ── 权限拦截 ──
.status-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 200rpx $space-page;
}

.status-text {
  font-size: $text-base;
  color: $color-muted;
}

// ── 加载 / 错误 ──
.loading-state, .error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 200rpx $space-page;
}

.loading-text, .error-text {
  font-size: $text-base;
  color: $color-muted;
}

.error-emoji {
  font-size: 80rpx;
  margin-bottom: $space-card;
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

// ── 概览标题 ──
.section-title-text {
  font-size: $text-base;
  font-weight: $weight-bold;
  color: $color-title;
  padding: $space-card $space-page $space-content;
}

// ── 概览卡片 2×3 网格 ──
.overview-grid {
  display: flex;
  flex-wrap: wrap;
  gap: $space-content;
  padding: 0 $space-page;
}

.overview-card {
  width: calc(50% - 8rpx);
  background: $color-surface;
  border-radius: $radius-card;
  padding: $space-card;
  text-align: center;
  box-shadow: $shadow-card;

  &--warn {
    .overview-num {
      color: $color-warning;
    }
  }
}

.overview-num {
  font-size: $text-2xl;
  font-weight: $weight-bold;
  color: $color-primary;
  display: block;

  &--sm {
    font-size: $text-xl;
  }
}

.overview-label {
  font-size: $text-xs;
  color: $color-muted;
  margin-top: 4rpx;
  display: block;
}

// ── 分区卡片 ──
.section-card {
  background: $color-surface;
  margin: $space-card $space-page 0;
  border-radius: $radius-card;
  padding: $space-card;
}

.section-title {
  font-size: $text-base;
  font-weight: $weight-bold;
  color: $color-title;
  display: block;
  margin-bottom: $space-content;
}

// ── 热门分类 ──
.category-row {
  margin-bottom: $space-content;
}

.category-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6rpx;
}

.category-name {
  font-size: $text-sm;
  color: $color-body;
}

.category-count {
  font-size: $text-xs;
  color: $color-muted;
  font-family: $font-mono;
}

.category-bar {
  height: 12rpx;
  background: $color-divider;
  border-radius: 6rpx;
  overflow: hidden;
}

.category-bar-fill {
  height: 100%;
  background: $color-primary-gradient;
  border-radius: 6rpx;
  transition: width 0.5s ease;
}

// ── 搜索词云 ──
.keyword-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.keyword-tag {
  font-size: $text-xs;
  color: $color-primary;
  background: $color-primary-light;
  padding: 8rpx 20rpx;
  border-radius: $radius-full;
}

// ── 柱状图 ──
.bar-chart {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.bar-item {
  display: flex;
  align-items: center;
  gap: $space-content;
}

.bar-date {
  font-size: $text-xs;
  color: $color-muted;
  width: 60rpx;
  flex-shrink: 0;
}

.bar-track {
  flex: 1;
  height: 28rpx;
  background: $color-divider;
  border-radius: 4rpx;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  background: $color-primary;
  border-radius: 4rpx;
  min-width: 4rpx;
  transition: width 0.5s ease;
}

.bar-count {
  font-size: $text-xs;
  color: $color-title;
  font-family: $font-mono;
  width: 48rpx;
  text-align: right;
  flex-shrink: 0;
}

// ── 敏感词库 ──
.sensitive-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sensitive-info {
  font-size: $text-sm;
  color: $color-body;
}

.sensitive-count {
  font-weight: $weight-bold;
  color: $color-primary;
}

.btn-reload {
  padding: 8rpx 32rpx;
  font-size: $text-sm;
  color: $color-primary;
  background: $color-primary-light;
  border-radius: $radius-full;
  border: none;
  line-height: 1.5;
}

// ── 空数据 ──
.empty-inline {
  padding: $space-card 0;
}

.empty-inline-text {
  font-size: $text-sm;
  color: $color-muted;
}
</style>

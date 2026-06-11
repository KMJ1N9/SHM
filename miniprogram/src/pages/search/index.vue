<template>
  <view class="search-page">
    <!-- 搜索栏 -->
    <view class="search-bar">
      <view class="search-input-row">
        <view class="search-input-wrap">
          <text class="search-icon">
            🔍
          </text>
          <input
            v-model="keyword"
            class="search-input"
            placeholder="搜索商品/卖家..."
            confirm-type="search"
            :focus="true"
            @confirm="onSearch"
          >
          <view v-if="keyword" class="search-clear" @click="clearKeyword">
            <text class="search-clear-icon">
              ✕
            </text>
          </view>
        </view>
        <view class="search-cancel" @click="goBack">
          <text class="search-cancel-text">
            取消
          </text>
        </view>
      </view>
    </view>

    <!-- ============================================================ -->
    <!-- 搜索前：历史记录 + 热门搜索 -->
    <!-- ============================================================ -->
    <view v-if="!hasSearched" class="search-hint">
      <!-- 搜索历史 -->
      <view v-if="history.length > 0" class="hint-section">
        <view class="hint-header">
          <text class="hint-title">
            搜索历史
          </text>
          <view class="hint-clear" @click="clearHistory">
            <text class="hint-clear-text">
              清除历史
            </text>
          </view>
        </view>
        <view class="hint-tags">
          <view
            v-for="(item, index) in history"
            :key="index"
            class="hint-tag"
            @click="onHistoryTap(item)"
          >
            <text class="hint-tag-text">
              {{ item }}
            </text>
          </view>
        </view>
      </view>

      <!-- 热门搜索 -->
      <view class="hint-section">
        <view class="hint-header">
          <text class="hint-title">
            热门搜索
          </text>
        </view>
        <view class="hint-tags">
          <view
            v-for="(item, index) in HOT_KEYWORDS"
            :key="index"
            class="hint-tag hint-tag--hot"
            @click="onHotTap(item)"
          >
            <text class="hint-tag-text">
              {{ item }}
            </text>
          </view>
        </view>
      </view>
    </view>

    <!-- ============================================================ -->
    <!-- 搜索后：排序 + 筛选 + 结果 -->
    <!-- ============================================================ -->
    <view v-else class="search-results">
      <!-- 排序 + 筛选栏 -->
      <view class="results-bar">
        <view class="sort-tabs">
          <view
            v-for="tab in sortTabs"
            :key="tab.value"
            class="sort-tab"
            :class="{ 'sort-tab--active': activeSort === tab.value }"
            @click="onSortChange(tab.value)"
          >
            <text class="sort-tab-text">
              {{ tab.label }}
            </text>
          </view>
        </view>
        <view class="filter-btn" @click="showFilter = true">
          <text class="filter-btn-icon">
            🔧
          </text>
          <text class="filter-btn-text">
            筛选
          </text>
          <view v-if="activeFilterCount > 0" class="filter-badge">
            <text class="filter-badge-text">
              {{ activeFilterCount }}
            </text>
          </view>
        </view>
      </view>

      <!-- 激活的筛选标签 -->
      <view v-if="activeFilterCount > 0" class="active-filters">
        <view v-if="filters.category" class="active-filter-tag" @click="removeFilter('category')">
          <text class="active-filter-text">
            {{ filters.category }}
          </text>
          <text class="active-filter-close">
            ✕
          </text>
        </view>
        <view v-if="filters.condition" class="active-filter-tag" @click="removeFilter('condition')">
          <text class="active-filter-text">
            {{ filters.condition }}
          </text>
          <text class="active-filter-close">
            ✕
          </text>
        </view>
        <view v-if="filters.priceMin || filters.priceMax" class="active-filter-tag" @click="removeFilter('price')">
          <text class="active-filter-text">
            {{ filters.priceMin || '0' }} — {{ filters.priceMax || '∞' }}
          </text>
          <text class="active-filter-close">
            ✕
          </text>
        </view>
      </view>

      <!-- 商品瀑布流 -->
      <view class="waterfall">
        <view class="waterfall-col">
          <view v-for="item in leftList" :key="item.id" class="waterfall-item">
            <ProductCard :product="item" />
          </view>
        </view>
        <view class="waterfall-col">
          <view v-for="item in rightList" :key="item.id" class="waterfall-item">
            <ProductCard :product="item" />
          </view>
        </view>
      </view>

      <!-- 状态 -->
      <view v-if="loading" class="load-more">
        <text class="load-more-text">
          搜索中...
        </text>
      </view>
      <view v-else-if="list.length === 0 && !loading">
        <EmptyState icon="🔍" title="未找到相关商品" />
      </view>
      <view v-else-if="noMore" class="load-more">
        <text class="load-more-text">
          — 没有更多了 —
        </text>
      </view>
    </view>

    <!-- 筛选侧边栏 -->
    <FilterSidebar
      :visible="showFilter"
      :model-value="filters"
      @update:visible="showFilter = $event"
      @apply="onFilterApply"
      @reset="onFilterReset"
    />
  </view>
</template>

<script setup>
import { ref, reactive, computed } from 'vue';
import { onReachBottom, onPullDownRefresh } from '@dcloudio/uni-app';
import { list as listProducts } from '@/api/product';
import ProductCard from '@/components/ProductCard.vue';
import FilterSidebar from '@/components/FilterSidebar.vue';
import EmptyState from '@/components/EmptyState.vue';

// ============================================================
// 常量
// ============================================================

const STORAGE_KEY = 'search_history';
const MAX_HISTORY = 20;

const HOT_KEYWORDS = ['教材', '电子产品', '生活用品', '运动户外', '服饰鞋包'];

const sortTabs = [
  { label: '最新', value: 'latest' },
  { label: '价格↑', value: 'priceAsc' },
  { label: '价格↓', value: 'priceDesc' },
];

// ============================================================
// 搜索状态
// ============================================================

const keyword = ref('');
const hasSearched = ref(false);

/** 搜索历史 */
const history = ref(loadHistory());

/** 当前筛选条件 */
const filters = reactive({
  category: '',
  condition: '',
  priceMin: '',
  priceMax: '',
});

/** 激活筛选数量（用于 badge） */
const activeFilterCount = computed(() => {
  let count = 0;
  if (filters.category) count++;
  if (filters.condition) count++;
  if (filters.priceMin || filters.priceMax) count++;
  return count;
});

/** 排序 */
const activeSort = ref('latest');

/** 筛选面板显隐 */
const showFilter = ref(false);

// ============================================================
// 结果状态
// ============================================================

const list = ref([]);
const page = ref(1);
const total = ref(0);
const loading = ref(false);
const noMore = ref(false);

const pageSize = 20;

const leftList = computed(() => list.value.filter((_, i) => i % 2 === 0));
const rightList = computed(() => list.value.filter((_, i) => i % 2 === 1));

// ============================================================
// 搜索历史（本地 Storage）
// ============================================================

function loadHistory() {
  try {
    const raw = uni.getStorageSync(STORAGE_KEY);
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function saveHistory(kw) {
  if (!kw || !kw.trim()) return;
  const trimmed = kw.trim();
  // 去重
  const filtered = history.value.filter((h) => h !== trimmed);
  filtered.unshift(trimmed);
  history.value = filtered.slice(0, MAX_HISTORY);
  uni.setStorageSync(STORAGE_KEY, JSON.stringify(history.value));
}

function clearHistory() {
  history.value = [];
  uni.removeStorageSync(STORAGE_KEY);
}

// ============================================================
// 数据加载
// ============================================================

async function loadProducts(reset = false) {
  if (loading.value) return;
  if (!reset && noMore.value) return;

  // 先计算目标页码，成功后再持久化 — 防止请求失败时跳过一整页
  const targetPage = reset ? 1 : page.value + 1;

  if (reset) {
    noMore.value = false;
  }

  loading.value = true;
  try {
    const params = {
      keyword: keyword.value.trim(),
      page: targetPage,
      pageSize,
      sort: activeSort.value,
    };
    if (filters.category) params.category = filters.category;
    if (filters.condition) params.condition = filters.condition;
    if (filters.priceMin) params.priceMin = filters.priceMin;
    if (filters.priceMax) params.priceMax = filters.priceMax;

    const data = await listProducts(params);

    if (reset) {
      list.value = data.list || [];
    } else {
      list.value = [...list.value, ...(data.list || [])];
    }
    total.value = data.total || 0;

    // 请求成功后才推进页码
    page.value = targetPage;

    if ((data.list || []).length < pageSize) {
      noMore.value = true;
    }
  } catch (err) {
    uni.showToast({ title: err.message || '搜索失败', icon: 'none', duration: 1500 });
  } finally {
    loading.value = false;
    uni.stopPullDownRefresh();
  }
}

// ============================================================
// 搜索动作
// ============================================================

function onSearch() {
  const kw = keyword.value.trim();
  if (!kw) return;

  saveHistory(kw);
  keyword.value = kw;
  hasSearched.value = true;
  loadProducts(true);
}

function onHistoryTap(kw) {
  keyword.value = kw;
  hasSearched.value = true;
  loadProducts(true);
}

function onHotTap(kw) {
  keyword.value = kw;
  hasSearched.value = true;
  loadProducts(true);
}

function clearKeyword() {
  keyword.value = '';
}

// ============================================================
// 排序 & 筛选
// ============================================================

function onSortChange(value) {
  if (activeSort.value === value) return;
  activeSort.value = value;
  loadProducts(true);
}

function onFilterApply(val) {
  filters.category = val.category || '';
  filters.condition = val.condition || '';
  filters.priceMin = val.priceMin || '';
  filters.priceMax = val.priceMax || '';
  loadProducts(true);
}

function onFilterReset() {
  filters.category = '';
  filters.condition = '';
  filters.priceMin = '';
  filters.priceMax = '';
  loadProducts(true);
}

function removeFilter(key) {
  if (key === 'category') filters.category = '';
  if (key === 'condition') filters.condition = '';
  if (key === 'price') {
    filters.priceMin = '';
    filters.priceMax = '';
  }
  loadProducts(true);
}

// ============================================================
// 导航
// ============================================================

function goBack() {
  uni.navigateBack();
}

// ============================================================
// 页面生命周期
// ============================================================

onPullDownRefresh(() => {
  loadProducts(true);
});

onReachBottom(() => {
  if (!noMore.value && !loading.value) {
    loadProducts(false);
  }
});
</script>

<style lang="scss">
@import '@/styles/tokens.scss';

.search-page {
  min-height: 100vh;
  background: $color-bg;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
}

// ── 搜索栏 ──────────────────────────────────────────────
.search-bar {
  background: $color-primary;
  padding: 16rpx $space-page;
}

.search-input-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.search-input-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  height: 72rpx;
  background: rgba(255, 255, 255, 0.92);
  border-radius: 36rpx;
  padding: 0 16rpx;
}

.search-icon {
  font-size: 28rpx;
  margin-right: 8rpx;
}

.search-input {
  flex: 1;
  height: 72rpx;
  font-size: $text-sm;
  color: $color-title;
}

.search-clear {
  width: 40rpx;
  height: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-full;
  background: rgba(0, 0, 0, 0.15);
  flex-shrink: 0;
}

.search-clear-icon {
  font-size: 20rpx;
  color: #FFFFFF;
}

.search-cancel {
  flex-shrink: 0;
}

.search-cancel-text {
  font-size: $text-base;
  color: #FFFFFF;
  font-weight: $weight-medium;
}

// ── 搜索前：历史 + 热门 ──────────────────────────────────
.search-hint {
  padding: $space-page;
}

.hint-section {
  margin-bottom: 32rpx;

  &:last-child {
    margin-bottom: 0;
  }
}

.hint-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $space-card;
}

.hint-title {
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: $color-title;
}

.hint-clear {
  padding: 4rpx 0;
}

.hint-clear-text {
  font-size: $text-xs;
  color: $color-muted;
}

.hint-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.hint-tag {
  padding: 12rpx 28rpx;
  border-radius: $radius-full;
  background: $color-surface;
  border: 1rpx solid $color-divider;

  &--hot {
    background: #FFF7E6;
    border-color: #FFD591;
  }
}

.hint-tag-text {
  font-size: $text-sm;
  color: $color-body;

  .hint-tag--hot & {
    color: #D46B08;
  }
}

// ── 搜索结果 ────────────────────────────────────────────
.search-results {
  flex: 1;
  display: flex;
  flex-direction: column;
}

// 排序 + 筛选栏
.results-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 $space-page;
  background: $color-surface;
  border-bottom: 1rpx solid $color-divider;
}

.sort-tabs {
  display: flex;
  gap: 0;
}

.sort-tab {
  padding: 20rpx 24rpx;
  position: relative;

  &--active {
    .sort-tab-text {
      color: $color-primary;
      font-weight: $weight-medium;
    }

    &::after {
      content: '';
      position: absolute;
      bottom: 0;
      left: 50%;
      transform: translateX(-50%);
      width: 32rpx;
      height: 4rpx;
      background: $color-primary;
      border-radius: 2rpx;
    }
  }
}

.sort-tab-text {
  font-size: $text-sm;
  color: $color-body;
}

.filter-btn {
  display: flex;
  align-items: center;
  gap: 4rpx;
  padding: 12rpx 16rpx;
  position: relative;
}

.filter-btn-icon {
  font-size: 24rpx;
}

.filter-btn-text {
  font-size: $text-sm;
  color: $color-body;
}

.filter-badge {
  position: absolute;
  top: 4rpx;
  right: 2rpx;
  width: 28rpx;
  height: 28rpx;
  background: $color-error;
  border-radius: $radius-full;
  display: flex;
  align-items: center;
  justify-content: center;
}

.filter-badge-text {
  font-size: 18rpx;
  color: #FFFFFF;
  font-weight: $weight-bold;
}

// 激活的筛选标签
.active-filters {
  display: flex;
  gap: 12rpx;
  padding: 12rpx $space-page;
  background: $color-surface;
  flex-wrap: wrap;
}

.active-filter-tag {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx 20rpx;
  background: $color-primary-light;
  border-radius: $radius-full;
}

.active-filter-text {
  font-size: $text-xs;
  color: $color-primary;
  font-weight: $weight-medium;
}

.active-filter-close {
  font-size: 18rpx;
  color: $color-primary;
}

// ── 瀑布流 ──────────────────────────────────────────────
.waterfall {
  display: flex;
  gap: 16rpx;
  padding: 16rpx $space-page;
  flex: 1;
}

.waterfall-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.waterfall-item {
  width: 100%;
}

// ── 加载更多 ────────────────────────────────────────────
.load-more {
  padding: 32rpx 0;
  display: flex;
  justify-content: center;
  align-items: center;
}

.load-more-text {
  font-size: $text-xs;
  color: $color-muted;
}
</style>

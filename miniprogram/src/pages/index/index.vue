<template>
  <view class="home-page">
    <!-- 搜索栏 -->
    <view class="search-bar">
      <view class="search-input-wrap" @click="goSearch">
        <text class="search-icon">
          🔍
        </text>
        <text class="search-placeholder">
          搜索商品...
        </text>
      </view>
      <view class="search-filter-btn" @click="showFilter = true">
        <text class="search-filter-icon">
          🔧
        </text>
        <view v-if="activeFilterCount > 0" class="search-filter-badge">
          <text class="search-filter-badge-text">
            {{ activeFilterCount }}
          </text>
        </view>
      </view>
    </view>

    <!-- 分类快捷入口 -->
    <view class="category-tabs">
      <view
        v-for="cat in categoryTabs"
        :key="cat.value"
        class="category-tab"
        :class="{ active: activeCategory === cat.value }"
        @click="onCategoryChange(cat.value)"
      >
        <text class="category-tab-text">
          {{ cat.icon }} {{ cat.label }}
        </text>
      </view>
    </view>

    <!-- 商品瀑布流 -->
    <view class="waterfall">
      <view class="waterfall-col">
        <view
          v-for="item in leftList"
          :key="item.id"
          class="waterfall-item"
        >
          <ProductCard :product="item" />
        </view>
      </view>
      <view class="waterfall-col">
        <view
          v-for="item in rightList"
          :key="item.id"
          class="waterfall-item"
        >
          <ProductCard :product="item" />
        </view>
      </view>
    </view>

    <!-- 加载更多状态 -->
    <view v-if="loading" class="load-more">
      <text class="load-more-text">
        加载中...
      </text>
    </view>
    <view v-else-if="list.length === 0 && !loading" class="load-more">
      <text class="load-more-text">
        — 暂无商品 —
      </text>
    </view>
    <view v-else-if="noMore" class="load-more">
      <text class="load-more-text">
        — 没有更多了 —
      </text>
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
import { useUserStore } from '@/store/user';
import ProductCard from '@/components/ProductCard.vue';
import FilterSidebar from '@/components/FilterSidebar.vue';

/** 分类标签（含 "全部"） */
const categoryTabs = [
  { value: '', label: '全部', icon: '📋' },
  { value: '电子产品', label: '电子', icon: '📱' },
  { value: '书籍教材', label: '书籍', icon: '📚' },
  { value: '生活用品', label: '生活', icon: '🏠' },
  { value: '服饰鞋包', label: '服饰', icon: '👗' },
  { value: '运动户外', label: '运动', icon: '⚽' },
  { value: '其他', label: '其他', icon: '📦' },
];

/** 当前选中分类 */
const activeCategory = ref('');

/** 筛选条件 */
const filters = reactive({
  category: '',
  condition: '',
  priceMax: '',
  priceMin: '',
});

/** 激活筛选数量（badge 红点） */
const activeFilterCount = computed(() => {
  let count = 0;
  if (filters.category) count++;
  if (filters.condition) count++;
  if (filters.priceMin || filters.priceMax) count++;
  return count;
});

/** 筛选面板显隐 */
const showFilter = ref(false);

/** 商品列表 */
const list = ref([]);
const page = ref(1);
const total = ref(0);
const loading = ref(false);
const noMore = ref(false);

const pageSize = 20;

/** 左列商品（奇数索引） */
const leftList = computed(() => list.value.filter((_, i) => i % 2 === 0));

/** 右列商品（偶数索引） */
const rightList = computed(() => list.value.filter((_, i) => i % 2 === 1));

/**
 * 加载商品列表
 * @param {boolean} [reset=false] — 是否重置（下拉刷新时重置）
 */
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
      page: targetPage,
      pageSize,
      sort: 'latest',
    };
    // 侧边栏筛选优先，回退到顶部 Tab
    const category = filters.category || activeCategory.value;
    if (category) params.category = category;
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

    // 判断是否已加载全部：返回条数 < pageSize 即已到末页（与 my.vue 保持一致）
    if ((data.list || []).length < pageSize) {
      noMore.value = true;
    }
  } catch (err) {
    uni.showToast({ title: err.message || '加载失败', icon: 'none', duration: 1500 });
  } finally {
    loading.value = false;
    uni.stopPullDownRefresh();
  }
}

/**
 * 切换分类
 */
function onCategoryChange(value) {
  if (activeCategory.value === value) return;
  activeCategory.value = value;
  loadProducts(true);
}

/**
 * 筛选 — 确定
 */
function onFilterApply(val) {
  filters.category = val.category || '';
  filters.condition = val.condition || '';
  filters.priceMin = val.priceMin || '';
  filters.priceMax = val.priceMax || '';
  loadProducts(true);
}

/**
 * 筛选 — 重置
 */
function onFilterReset() {
  filters.category = '';
  filters.condition = '';
  filters.priceMin = '';
  filters.priceMax = '';
  loadProducts(true);
}

/**
 * 搜索 — 跳转搜索页
 */
function goSearch() {
  uni.navigateTo({ url: '/pages/search/index' });
}

/**
 * 下拉刷新
 */
onPullDownRefresh(() => {
  loadProducts(true);
});

/**
 * 触底加载更多
 */
onReachBottom(() => {
  if (!noMore.value && !loading.value) {
    loadProducts(false);
  }
});

// 首次加载 — 仅在已登录（有 Token）时请求，否则等 App.vue onLaunch 跳转登录页
const userStore = useUserStore();
if (userStore.accessToken || uni.getStorageSync('accessToken')) {
  loadProducts(true);
}
</script>

<style lang="scss">
@import '@/styles/tokens.scss';

.home-page {
  min-height: 100vh;
  background: $color-bg;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
}

// ── 搜索栏 ──────────────────────────────────────────────
.search-bar {
  background: $color-primary;
  padding: 16rpx $space-page;
  display: flex;
  align-items: center;
}

.search-input-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  height: 72rpx;
  background: rgba(255, 255, 255, 0.92);
  border-radius: 36rpx;
  padding: 0 24rpx;
}

.search-icon {
  font-size: 28rpx;
  margin-right: 12rpx;
}

.search-placeholder {
  font-size: $text-sm;
  color: $color-muted;
}

.search-filter-btn {
  position: relative;
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.92);
  border-radius: 36rpx;
  margin-left: 12rpx;
  flex-shrink: 0;
}

.search-filter-icon {
  font-size: 32rpx;
}

.search-filter-badge {
  position: absolute;
  top: 4rpx;
  right: 4rpx;
  width: 28rpx;
  height: 28rpx;
  background: $color-error;
  border-radius: $radius-full;
  display: flex;
  align-items: center;
  justify-content: center;
}

.search-filter-badge-text {
  font-size: 18rpx;
  color: #FFFFFF;
  font-weight: $weight-bold;
}

// ── 分类标签 ────────────────────────────────────────────
.category-tabs {
  background: $color-surface;
  padding: 16rpx $space-page;
  display: flex;
  gap: 16rpx;
  white-space: nowrap;
  overflow-x: auto;

  // 隐藏滚动条
  &::-webkit-scrollbar {
    display: none;
  }
}

.category-tab {
  flex-shrink: 0;
  padding: 10rpx 24rpx;
  border-radius: $radius-full;
  background: $color-bg;
  font-size: $text-xs;
  color: $color-body;

  &.active {
    background: $color-primary-light;
    color: $color-primary;
    font-weight: $weight-medium;
  }
}

.category-tab-text {
  line-height: 1;
}

// ── 瀑布流 ──────────────────────────────────────────────
.waterfall {
  display: flex;
  gap: 16rpx;
  padding: 16rpx $space-page;
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

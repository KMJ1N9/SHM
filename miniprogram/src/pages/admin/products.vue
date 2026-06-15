<template>
  <view class="products-page">
    <!-- 权限守卫 -->
    <view v-if="!userStore.isAdmin" class="empty-state">
      <EmptyState icon="🔒" title="仅管理员可访问" />
    </view>

    <template v-else>
      <!-- 搜索栏 -->
      <view class="search-bar">
        <input
          v-model="keyword"
          class="search-input"
          placeholder="搜索商品标题/卖家昵称"
          confirm-type="search"
          @confirm="onSearch"
        />
        <view class="search-btn" @click="onSearch">
          <text class="search-btn-text">搜索</text>
        </view>
      </view>

      <!-- 状态 Tab -->
      <view class="tabs-bar">
        <view
          v-for="tab in statusTabs"
          :key="tab.value"
          :class="['tab-item', activeStatus === tab.value ? 'tab-item--active' : '']"
          @click="onSwitchStatus(tab.value)"
        >
          <text class="tab-text">{{ tab.label }}</text>
        </view>
      </view>

      <!-- 商品列表 -->
      <scroll-view
        v-if="products.length > 0"
        class="products-scroll"
        scroll-y
        :lower-threshold="100"
        @scrolltolower="loadMore"
      >
        <view
          v-for="product in products"
          :key="product.id"
          class="product-card"
          @click="goDetail(product.id)"
        >
          <!-- 缩略图 -->
          <SafeImage
            v-if="product.cover_image"
            class="product-card-thumb"
            :src="resolveImageUrl(product.cover_image)"
            mode="aspectFill"
            :lazy-load="true"
          />
          <view v-else class="product-card-thumb product-card-thumb--default">
            <text class="product-card-thumb-icon">📷</text>
          </view>

          <!-- 信息 -->
          <view class="product-card-info">
            <text class="product-card-title">{{ product.title }}</text>
            <view class="product-card-meta">
              <text class="product-card-condition">{{ product.condition }}</text>
              <text class="product-card-price">¥{{ product.price }}</text>
            </view>
            <text class="product-card-seller">
              卖家：{{ product.seller?.nickname || '未知' }}
            </text>
            <text class="product-card-time">{{ formatTime(product.created_at) }}</text>
          </view>

          <!-- 状态标签 + 操作 -->
          <view class="product-card-right">
            <text :class="['tag', 'tag-status', 'tag-status--' + product.status]">
              {{ statusLabel(product.status) }}
            </text>
            <view
              v-if="product.status === 'active' || product.status === 'reserved'"
              class="off-shelf-btn"
              @click.stop="handleOffShelf(product)"
            >
              <text class="off-shelf-btn-text">下架</text>
            </view>
          </view>
        </view>
      </scroll-view>

      <!-- 空状态 -->
      <EmptyState
        v-if="!loading && products.length === 0"
        icon="📦"
        :title="keyword ? '未找到匹配的商品' : '暂无商品'"
        :description="keyword ? '试试调整搜索条件' : ''"
        :action-text="keyword ? '清除搜索' : ''"
        @action="clearSearch"
      />

      <!-- 加载提示 -->
      <view v-if="loading" class="loading-tip">
        <text class="loading-tip-text">加载中...</text>
      </view>
      <view v-else-if="!hasMore && products.length > 0" class="loading-tip">
        <text class="loading-tip-text">— 没有更多了 —</text>
      </view>
    </template>
  </view>
</template>

<script setup>
/**
 * 商品管理页 — 管理员查看所有商品，支持搜索/筛选/强制下架。
 *
 * 权限：仅 admin 角色可访问。
 * 分页模式：固化 A6-001 修复的 targetPage 模式。
 */
import { ref } from 'vue';
import { onShow, onUnload } from '@dcloudio/uni-app';
import { useUserStore } from '@/store/user';
import { getAdminProducts, offShelfProduct } from '@/api/admin';
import { resolveImageUrl } from '@/api/index';
import EmptyState from '@/components/EmptyState.vue';
import SafeImage from '@/components/SafeImage.vue';

const userStore = useUserStore();

// ── 筛选状态 ──────────────────────────────────────────────
const keyword = ref('');
const activeStatus = ref('');

const statusTabs = [
  { label: '全部', value: '' },
  { label: '在售', value: 'active' },
  { label: '已下架', value: 'off_shelf' },
  { label: '已售', value: 'sold' },
];

// ── 列表状态 ──────────────────────────────────────────────
const products = ref([]);
const page = ref(1);
const total = ref(0);
const hasMore = ref(false);
const loading = ref(false);
const PAGE_SIZE = 20;

// ── 生命周期 ──────────────────────────────────────────────
let guardTimer = null;

onShow(() => {
  if (!userStore.isAdmin) {
    uni.showToast({ title: '仅管理员可访问', icon: 'none' });
    guardTimer = setTimeout(() => uni.navigateBack({ delta: 1 }), 1500);
    return;
  }
  fetchProducts(true);
});

onUnload(() => {
  if (guardTimer) {
    clearTimeout(guardTimer);
    guardTimer = null;
  }
});

// ── 数据加载 ──────────────────────────────────────────────
async function fetchProducts(reset = false) {
  if (loading.value) return;
  const targetPage = reset ? 1 : page.value + 1;
  loading.value = true;

  try {
    const result = await getAdminProducts({
      keyword: keyword.value || undefined,
      status: activeStatus.value || undefined,
      page: targetPage,
      pageSize: PAGE_SIZE,
    });

    if (reset) {
      products.value = result.list || [];
    } else {
      products.value.push(...(result.list || []));
    }
    total.value = result.total;
    hasMore.value = products.value.length < result.total;
    page.value = targetPage;
  } catch (err) {
    uni.showToast({ title: err.message || '加载失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}

// ── 筛选操作 ──────────────────────────────────────────────
function onSearch() {
  fetchProducts(true);
}

function onSwitchStatus(val) {
  if (activeStatus.value === val) return;
  activeStatus.value = val;
  fetchProducts(true);
}

function clearSearch() {
  keyword.value = '';
  activeStatus.value = '';
  fetchProducts(true);
}

function loadMore() {
  if (hasMore.value && !loading.value) {
    fetchProducts(false);
  }
}

// ── 下架操作 ──────────────────────────────────────────────
async function handleOffShelf(product) {
  const { confirm } = await uni.showModal({
    title: '下架商品',
    content: `确定下架商品「${product.title}」？下架后将不再公开展示。`,
    confirmText: '确定下架',
    confirmColor: '#FF4D4F',
  });
  if (!confirm) return;

  try {
    await offShelfProduct(product.id);
    product.status = 'off_shelf';
    uni.showToast({ title: '已下架', icon: 'success' });
  } catch (err) {
    uni.showToast({ title: err.message || '操作失败', icon: 'error' });
  }
}

// ── 导航 ──────────────────────────────────────────────
function goDetail(id) {
  uni.navigateTo({ url: `/pages/product/detail?id=${id}` });
}

// ── 工具函数 ──────────────────────────────────────────────
function formatTime(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  const m = d.getMonth() + 1;
  const day = d.getDate();
  return `${m}-${day}`;
}

function statusLabel(status) {
  const map = {
    active: '在售',
    reserved: '已预定',
    sold: '已售',
    off_shelf: '已下架',
  };
  return map[status] || status;
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.products-page {
  min-height: 100vh;
  background: $color-bg;
}

// ── 搜索栏 ──────────────────────────────────────────────
.search-bar {
  display: flex;
  align-items: center;
  padding: 20rpx $space-page;
  background: $color-surface;
  gap: 16rpx;
}

.search-input {
  flex: 1;
  height: $input-height;
  border-radius: $radius-card;
  background: $color-bg;
  padding: 0 24rpx;
  font-size: $text-base;
}

.search-btn {
  height: $input-height;
  padding: 0 32rpx;
  border-radius: $radius-card;
  background: $color-primary;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.search-btn-text {
  font-size: $text-base;
  color: #FFFFFF;
}

// ── 状态 Tab ──────────────────────────────────────────────
.tabs-bar {
  background: $color-surface;
  padding: 0 $space-page 16rpx;
}

.tab-item {
  display: inline-block;
  padding: 12rpx 32rpx;
  margin-right: 16rpx;
  border-radius: $radius-card;
  background: $color-bg;
}

.tab-item--active {
  background: $color-primary;
}

.tab-text {
  font-size: $text-sm;
  color: $color-title;
}

.tab-item--active .tab-text {
  color: #FFFFFF;
}

// ── 商品列表 ──────────────────────────────────────────────
.products-scroll {
  flex: 1;
}

.product-card {
  display: flex;
  align-items: flex-start;
  padding: 24rpx $space-page;
  background: $color-surface;
  margin-top: 2rpx;

  &:active {
    background: $color-bg;
  }
}

.product-card-thumb {
  width: 140rpx;
  height: 140rpx;
  border-radius: $radius-card;
  background: $color-divider;
  flex-shrink: 0;
}

.product-card-thumb--default {
  display: flex;
  align-items: center;
  justify-content: center;
}

.product-card-thumb-icon {
  font-size: 48rpx;
}

.product-card-info {
  flex: 1;
  margin-left: 20rpx;
  min-width: 0;
}

.product-card-title {
  font-size: $text-base;
  font-weight: $weight-bold;
  color: $color-title;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.4;
}

.product-card-meta {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 8rpx;
}

.product-card-condition {
  font-size: $text-xs;
  color: $color-muted;
  padding: 2rpx 12rpx;
  background: $color-divider;
  border-radius: $radius-card;
}

.product-card-price {
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: $color-error;
  font-family: $font-mono;
}

.product-card-seller {
  font-size: $text-xs;
  color: $color-muted;
  margin-top: 6rpx;
}

.product-card-time {
  font-size: $text-xs;
  color: $color-muted;
  margin-top: 4rpx;
}

.product-card-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12rpx;
  flex-shrink: 0;
  margin-left: 12rpx;
}

// ── 标签 ──────────────────────────────────────────────
.tag {
  font-size: 20rpx;
  padding: 4rpx 16rpx;
  border-radius: $radius-card;
  white-space: nowrap;
}

.tag-status--active {
  background: rgba(82, 196, 26, 0.08);
  color: $color-success;
}

.tag-status--reserved {
  background: rgba(250, 173, 20, 0.08);
  color: $color-warning;
}

.tag-status--sold {
  background: rgba(74, 144, 217, 0.08);
  color: $color-primary;
}

.tag-status--off_shelf {
  background: $color-divider;
  color: $color-muted;
}

// ── 下架按钮 ──────────────────────────────────────────────
.off-shelf-btn {
  padding: 8rpx 20rpx;
  border-radius: $radius-card;
  background: rgba(255, 77, 79, 0.08);
}

.off-shelf-btn-text {
  font-size: $text-sm;
  color: $color-error;
}

// ── 加载/结束提示 ──────────────────────────────────────────
.loading-tip {
  padding: 32rpx 0;
  text-align: center;
}

.loading-tip-text {
  font-size: $text-sm;
  color: $color-muted;
}
</style>

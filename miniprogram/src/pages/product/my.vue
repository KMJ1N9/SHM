<template>
  <view class="my-products-page">
    <!-- 状态标签切换 -->
    <view class="status-tabs">
      <view
        v-for="tab in statusTabs"
        :key="tab.value"
        class="tab-item"
        :class="{ active: activeStatus === tab.value }"
        @click="onStatusChange(tab.value)"
      >
        <text class="tab-text">
          {{ tab.label }}
        </text>
      </view>
    </view>

    <!-- 商品列表 -->
    <view v-if="list.length > 0" class="product-list">
      <view
        v-for="item in list"
        :key="item.id"
        class="product-item"
        @click="goDetail(item.id)"
      >
        <!-- 封面图 -->
        <SafeImage
          class="item-cover"
          :src="getCoverImage(item.images)"
          mode="aspectFill"
          :lazy-load="true"
        />

        <!-- 信息 -->
        <view class="item-body">
          <text class="item-title">
            {{ item.title }}
          </text>
          <view class="item-meta">
            <text class="item-price">
              ¥{{ formatPrice(item.price) }}
            </text>
            <text class="item-status" :class="'item-status-' + item.status">
              {{ statusLabel(item.status) }}
            </text>
          </view>
          <text class="item-date">
            {{ formatTime(item.created_at) }}
          </text>
        </view>

        <!-- 操作按钮 -->
        <view v-if="item.status === 'active'" class="item-actions">
          <button class="item-btn item-btn-edit" @click.stop="goEdit(item.id)">
            编辑
          </button>
          <button class="item-btn item-btn-delete" @click.stop="onDelete(item)">
            删除
          </button>
        </view>
      </view>
    </view>

    <!-- 空状态 -->
    <view v-else-if="!loading">
      <EmptyState
        icon="📦"
        title="还没有发布过商品"
        action-text="去发布"
        @action="goPublish"
      />
    </view>

    <!-- 加载更多 -->
    <view v-if="loading" class="load-more">
      <text class="load-more-text">
        加载中...
      </text>
    </view>
    <view v-else-if="noMore && list.length > 0" class="load-more">
      <text class="load-more-text">
        — 没有更多了 —
      </text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onShow, onReachBottom } from '@dcloudio/uni-app';
import { my as myProducts, remove as deleteProduct } from '@/api/product';
import { resolveImageUrl } from '@/api/index';
import { useAppStore } from '@/store/app';
import SafeImage from '@/components/SafeImage.vue';

const appStore = useAppStore();

/** 状态筛选 Tabs */
const statusTabs = [
  { value: 'active', label: '在售' },
  { value: 'reserved', label: '已预定' },
  { value: 'sold', label: '已售出' },
  { value: 'all', label: '全部' },
];

const activeStatus = ref('active');
const list = ref([]);
const page = ref(1);
const loading = ref(false);
const noMore = ref(false);
const pageSize = 20;

/**
 * 每次页面显示时刷新列表
 */
onShow(() => {
  loadProducts(true);
});

/**
 * 加载我发布的商品
 */
async function loadProducts(reset = false) {
  if (loading.value) return;
  if (!reset && noMore.value) return;

  if (reset) {
    page.value = 1;
    noMore.value = false;
  }

  loading.value = true;
  try {
    const params = {
      page: page.value,
      pageSize,
    };
    if (activeStatus.value !== 'all') {
      params.status = activeStatus.value;
    } else {
      params.status = 'all';
    }

    const data = await myProducts(params);

    if (reset) {
      list.value = data.list || [];
    } else {
      list.value = [...list.value, ...(data.list || [])];
    }

    if ((data.list || []).length < pageSize) {
      noMore.value = true;
    }
  } catch (err) {
    uni.showToast({ title: err.message || '加载失败', icon: 'none', duration: 1500 });
  } finally {
    loading.value = false;
  }
}

/**
 * 切换状态标签
 */
function onStatusChange(status) {
  if (activeStatus.value === status) return;
  activeStatus.value = status;
  loadProducts(true);
}

/**
 * 触底加载更多
 */
onReachBottom(() => {
  if (!noMore.value && !loading.value) {
    page.value++;
    loadProducts(false);
  }
});

/**
 * 从 images 字段提取封面图
 */
function getCoverImage(images) {
  if (!images) return '';
  let arr = images;
  if (typeof arr === 'string') {
    try { arr = JSON.parse(arr); } catch { return ''; }
  }
  const raw = Array.isArray(arr) && arr.length > 0 ? arr[0] : '';
  // 真机 localhost 不可达，必须解析图片 URL
  return resolveImageUrl(raw);
}

/**
 * 格式化价格
 */
function formatPrice(price) {
  if (price == null) return '0';
  return parseFloat(price).toFixed(2).replace(/\.00$/, '');
}

/**
 * 格式化时间
 */
function formatTime(dateStr) {
  if (!dateStr) return '';
  try {
    const d = new Date(dateStr);
    const month = d.getMonth() + 1;
    const day = d.getDate();
    return `${month}月${day}日`;
  } catch {
    return '';
  }
}

/**
 * 状态标签文字
 */
function statusLabel(status) {
  const map = {
    active: '在售',
    reserved: '已预定',
    sold: '已售出',
    deleted: '已删除',
    off_shelf: '已下架',
  };
  return map[status] || status;
}

/**
 * 跳转商品详情
 */
function goDetail(id) {
  uni.navigateTo({ url: `/pages/product/detail?id=${id}` });
}

/**
 * 跳转编辑页（复用发布页，预填已有数据）
 *
 * publish 页是 tabBar 页面，无法通过 navigateTo 传 query 参数。
 * 改为写入 appStore.pendingEditProductId → switchTab 跳转。
 */
function goEdit(id) {
  appStore.setPendingEditProductId(id);
  uni.switchTab({ url: '/pages/product/publish' });
}

/**
 * 删除商品（软删除）
 */
function onDelete(item) {
  uni.showModal({
    title: '确认删除',
    content: `确定要删除"${item.title}"吗？删除后无法恢复。`,
    confirmText: '确认删除',
    confirmColor: '#FF4D4F',
    success: async (res) => {
      if (!res.confirm) return;
      try {
        await deleteProduct(item.id);
        uni.showToast({ title: '已删除', icon: 'success', duration: 1200 });
        // 从列表移除
        list.value = list.value.filter((p) => p.id !== item.id);
      } catch (err) {
        uni.showToast({
          title: err.message || '删除失败',
          icon: 'none',
          duration: 2000,
        });
      }
    },
  });
}

/**
 * 跳转发布页
 */
function goPublish() {
  uni.switchTab({ url: '/pages/product/publish' });
}
</script>

<style lang="scss">
@import '@/styles/tokens.scss';

.my-products-page {
  min-height: 100vh;
  background: $color-bg;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
}

// ── 状态 Tabs ───────────────────────────────────────────
.status-tabs {
  background: $color-surface;
  display: flex;
  padding: 0 $space-page;
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 24rpx 0;
  position: relative;
  border-bottom: 4rpx solid transparent;

  &.active {
    border-bottom-color: $color-primary;
  }
}

.tab-text {
  font-size: $text-sm;
  color: $color-body;

  .active & {
    color: $color-primary;
    font-weight: $weight-medium;
  }
}

// ── 商品列表 ────────────────────────────────────────────
.product-list {
  padding: 16rpx $space-page;
}

.product-item {
  background: $color-surface;
  border-radius: $radius-card;
  padding: $space-card;
  margin-bottom: 16rpx;
  display: flex;
  gap: 20rpx;
  box-shadow: $shadow-card;
}

.item-cover {
  width: 160rpx;
  height: 160rpx;
  border-radius: $radius-card;
  background: $color-divider;
  flex-shrink: 0;
}

.item-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-width: 0;
}

.item-title {
  font-size: $text-base;
  font-weight: $weight-medium;
  color: $color-title;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  line-height: 1.4;
}

.item-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.item-price {
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: $color-error;
  font-family: $font-mono;
}

.item-status {
  font-size: $text-xs;
  padding: 4rpx 12rpx;
  border-radius: $radius-full;
}

.item-status-active {
  background: $color-primary-light;
  color: $color-primary;
}

.item-status-sold {
  background: #F0F0F0;
  color: $color-muted;
}

.item-status-reserved {
  background: #FFF7E6;
  color: #FA8C16;
}

.item-status-deleted {
  background: #FFF1F0;
  color: $color-error;
}

.item-date {
  font-size: $text-xs;
  color: $color-muted;
}

// 操作按钮
.item-actions {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  justify-content: center;
  flex-shrink: 0;
}

.item-btn {
  height: 56rpx;
  padding: 0 20rpx;
  border-radius: $radius-card;
  border: none;
  font-size: $text-xs;
  display: flex;
  align-items: center;
  justify-content: center;

  &::after {
    border: none;
  }
}

.item-btn-edit {
  background: $color-primary-light;
  color: $color-primary;
}

.item-btn-delete {
  background: #FFF1F0;
  color: $color-error;
}

// ── 空状态 ──────────────────────────────────────────────
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 200rpx 0;
}

.empty-icon {
  font-size: 96rpx;
  margin-bottom: $space-card;
}

.empty-text {
  font-size: $text-base;
  color: $color-muted;
  margin-bottom: $space-card;
}

.empty-btn {
  padding: 12rpx 48rpx;
  border-radius: $radius-card;
  background: $color-primary;
  color: $color-surface;
  font-size: $text-sm;
  border: none;

  &::after {
    border: none;
  }
}

// ── 加载更多 ────────────────────────────────────────────
.load-more {
  padding: 32rpx 0;
  display: flex;
  justify-content: center;
}

.load-more-text {
  font-size: $text-xs;
  color: $color-muted;
}
</style>

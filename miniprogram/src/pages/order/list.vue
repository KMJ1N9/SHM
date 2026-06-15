<template>
  <view class="order-list-page">
    <!-- ============================================================ -->
    <!-- 状态 Tab 切换 -->
    <!-- ============================================================ -->
    <view class="tabs-bar">
      <scroll-view class="tabs-scroll" scroll-x :show-scrollbar="false">
        <view
          v-for="tab in statusTabs"
          :key="tab.value"
          :class="['tab-item', activeStatus === tab.value ? 'tab-item--active' : '']"
          @click="onSwitchTab(tab.value)"
        >
          <text class="tab-text">
            {{ tab.label }}
          </text>
        </view>
      </scroll-view>
    </view>

    <!-- ============================================================ -->
    <!-- 订单列表 -->
    <!-- ============================================================ -->
    <scroll-view
      v-if="orders.length > 0"
      class="order-scroll"
      scroll-y
      refresher-enabled
      :refresher-triggered="refreshing"
      :lower-threshold="100"
      @refresherrefresh="onRefresh"
      @scrolltolower="loadMore"
    >
      <view
        v-for="order in orders"
        :key="order.id"
        class="order-card"
        @click="goDetail(order.id)"
      >
        <!-- 商品信息行 -->
        <view class="card-product-row">
          <SafeImage
            class="card-image"
            :src="resolveImageUrl(getProductImage(order))"
            mode="aspectFill"
          />
          <view class="card-info">
            <text class="card-title" :lines="1">
              {{ getProductTitle(order) }}
            </text>
            <text class="card-condition">
              {{ getProductCondition(order) }}
            </text>
            <view class="card-price-row">
              <text class="card-price">
                ¥{{ formatPrice(getProductPrice(order)) }}
              </text>
            </view>
          </view>
        </view>

        <!-- 交易对象 + 状态 -->
        <view class="card-meta-row">
          <text class="card-role-label">
            {{ getRoleLabel(order) }}：{{ getPartnerName(order) }}
          </text>
          <view class="status-badge" :class="'status-' + order.status">
            <text class="status-text">
              {{ statusLabel(order.status) }}
            </text>
          </view>
        </view>

        <!-- 操作按钮区（简化版，详情页做完整交互） -->
        <view class="card-actions">
          <text class="card-time">
            {{ formatTime(order.created_at) }}
          </text>
        </view>
      </view>

      <!-- 加载更多 -->
      <view v-if="loadingMore" class="loadmore-tip">
        <text class="loadmore-text">
          加载中...
        </text>
      </view>
      <view v-else-if="!hasMore && orders.length > 0" class="loadmore-tip">
        <text class="loadmore-text">
          没有更多了
        </text>
      </view>
    </scroll-view>

    <!-- ============================================================ -->
    <!-- 空状态 -->
    <!-- ============================================================ -->
    <view v-else-if="!loading" class="empty-state">
      <text class="empty-emoji">
        📦
      </text>
      <text class="empty-title">
        暂无订单
      </text>
      <text class="empty-desc">
        去逛逛商品，遇到心仪的就下单吧
      </text>
    </view>

    <!-- 加载中 -->
    <view v-if="loading" class="loading-state">
      <text class="loading-text">
        加载中...
      </text>
    </view>
  </view>
</template>

<script setup>
/**
 * 订单列表页 — 按状态 Tab 筛选，显示我作为买家/卖家的全部订单
 *
 * 使用游标分页（cursor/limit）替代偏移分页（page/pageSize），
 * O(1) 定位避免 OFFSET 大页码退化。
 * 游标 = 上一页最后一条 id，首页传 null。
 */

import { ref, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getOrderList } from '@/api/order';
import { resolveImageUrl } from '@/api/index';
import SafeImage from '@/components/SafeImage.vue';

// ============================================================
// 状态 Tab 配置
// ============================================================
const statusTabs = [
  { label: '全部', value: '' },
  { label: '待面交', value: 'pending' },
  { label: '已面交', value: 'met' },
  { label: '已完成', value: 'completed' },
  { label: '已取消', value: 'cancelled' },
];

/** 订单状态 → 中文标签 */
function statusLabel(status) {
  const map = {
    pending: '待面交',
    met: '已面交',
    completed: '已完成',
    cancelled: '已取消',
  };
  return map[status] || status;
}

// ============================================================
// 数据状态
// ============================================================
const activeStatus = ref('');
const orders = ref([]);
const loading = ref(true);
const refreshing = ref(false);
const loadingMore = ref(false);
const cursor = ref(null);
const limit = 20;
const hasMore = ref(true);

// ============================================================
// 数据加载
// ============================================================
async function fetchOrders(reset = false) {
  if (reset) {
    cursor.value = null;
    hasMore.value = true;
  }

  if (!reset && !hasMore.value) return;

  try {
    const params = {
      limit,
      status: activeStatus.value || undefined,
    };
    if (!reset && cursor.value) {
      params.cursor = cursor.value;
    }

    const result = await getOrderList(params);

    // 预处理：预解析 product_snapshot 避免模板中重复 JSON.parse
    const parsed = (result.list || []).map(order => {
      let _snapshot = {};
      if (order.product_snapshot) {
        try {
          _snapshot = typeof order.product_snapshot === 'string'
            ? JSON.parse(order.product_snapshot)
            : order.product_snapshot;
        } catch (e) {
          console.warn('[OrderList] 解析商品快照失败:', e);
        }
      }
      return { ...order, _snapshot };
    });

    if (reset) {
      orders.value = parsed;
    } else {
      orders.value.push(...parsed);
    }

    cursor.value = result.cursor || null;
    hasMore.value = result.hasMore ?? false;
  } catch (err) {
    uni.showToast({ title: err.message || '加载失败', icon: 'none' });
  }
}

// ============================================================
// Tab 切换
// ============================================================
function onSwitchTab(status) {
  if (activeStatus.value === status) return;
  activeStatus.value = status;
  loading.value = true;
  fetchOrders(true).finally(() => {
    loading.value = false;
  });
}

// ============================================================
// 下拉刷新
// ============================================================
async function onRefresh() {
  refreshing.value = true;
  await fetchOrders(true);
  refreshing.value = false;
}

// ============================================================
// 触底加载更多
// ============================================================
async function loadMore() {
  if (loadingMore.value || !hasMore.value) return;
  loadingMore.value = true;
  await fetchOrders(false);
  loadingMore.value = false;
}

// ============================================================
// 跳转订单详情
// ============================================================
function goDetail(orderId) {
  uni.navigateTo({ url: `/pages/order/detail?id=${orderId}` });
}

// ============================================================
// 辅助函数——从 product_snapshot JSON 提取信息
// ============================================================
function getProductImage(order) {
  let images = order._snapshot?.images;
  // 兼容后端 bug：images 可能被二次序列化为 JSON string 而非数组
  if (typeof images === 'string') {
    try { images = JSON.parse(images); } catch { images = []; }
  }
  if (Array.isArray(images) && images.length > 0) return images[0];
  return '';
}

function getProductTitle(order) {
  return order._snapshot?.title || '商品';
}

function getProductCondition(order) {
  return order._snapshot?.condition || '';
}

function getProductPrice(order) {
  return order._snapshot?.price || 0;
}

/**
 * 获取交易对方昵称
 * 后端 findByUser 返回 buyer_nickname 和 seller_nickname（JOIN users）
 * 同时 product_snapshot 中也包含 seller 信息
 */
function getPartnerName(order) {
  // 判断我的角色：如果我是买家则显示卖家昵称，否则显示买家昵称
  const userStore = useUserStore();
  const myId = userStore.user?.id;
  if (myId === order.buyer_id) {
    return order.seller_nickname || '卖家';
  }
  return order.buyer_nickname || '买家';
}

function getRoleLabel(order) {
  const userStore = useUserStore();
  const myId = userStore.user?.id;
  return myId === order.buyer_id ? '卖家' : '买家';
}

/**
 * 格式化价格（保留最多 2 位小数）
 */
function formatPrice(price) {
  const num = parseFloat(price);
  if (Number.isNaN(num)) return '0';
  return num % 1 === 0 ? String(num) : num.toFixed(2);
}

/**
 * 格式化时间
 */
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

import { useUserStore } from '@/store/user';

// ============================================================
// 生命周期
// ============================================================
onMounted(() => {
  loading.value = true;
  fetchOrders(true).finally(() => {
    loading.value = false;
  });
});

// 从详情页返回时刷新列表（订单操作后状态会变化）
onShow(() => {
  // 首次加载由 onMounted 处理，后续显示时刷新
  if (!loading.value) {
    fetchOrders(true);
  }
});
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.order-list-page {
  min-height: 100vh;
  background: $color-bg;
  display: flex;
  flex-direction: column;
}

// ── Tab 栏 ──
.tabs-bar {
  background: $color-surface;
  padding: $space-content 0;
}

.tabs-scroll {
  white-space: nowrap;
}

.tab-item {
  display: inline-flex;
  align-items: center;
  padding: 12rpx 28rpx;
  margin-left: $space-card;

  &--active {
    .tab-text {
      color: $color-primary;
      font-weight: $weight-bold;
      position: relative;

      &::after {
        content: '';
        position: absolute;
        bottom: -8rpx;
        left: 50%;
        transform: translateX(-50%);
        width: 36rpx;
        height: 6rpx;
        border-radius: 3rpx;
        background: $color-primary;
      }
    }
  }
}

.tab-text {
  font-size: $text-base;
  color: $color-body;
  white-space: nowrap;
}

// ── 订单列表 ──
.order-scroll {
  flex: 1;
  padding: $space-card 0;
}

// ── 订单卡片 ──
.order-card {
  background: $color-surface;
  margin: 0 $space-page $space-card;
  border-radius: $radius-card;
  padding: $space-card;
  box-shadow: $shadow-card;
}

.card-product-row {
  display: flex;
  gap: $space-card;
}

.card-image {
  width: 140rpx;
  height: 140rpx;
  border-radius: $radius-card;
  flex-shrink: 0;
  background: $color-divider;
}

.card-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
}

.card-title {
  font-size: $text-base;
  font-weight: $weight-medium;
  color: $color-title;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-condition {
  font-size: $text-sm;
  color: $color-muted;
  margin-top: 4rpx;
}

.card-price-row {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
}

.card-price {
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: $color-error;
  font-family: $font-mono;
}

// ── 交易对象 + 状态 ──
.card-meta-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: $space-content;
  padding-top: $space-content;
  border-top: 1rpx solid $color-divider;
}

.card-role-label {
  font-size: $text-sm;
  color: $color-body;
}

// ── 状态标签 ──
.status-badge {
  padding: 4rpx 16rpx;
  border-radius: $radius-full;
  font-size: $text-xs;

  &.status-pending {
    background: #E6F7FF;
    .status-text { color: #1890FF; }
  }

  &.status-met {
    background: #FFF7E6;
    .status-text { color: #FA8C16; }
  }

  &.status-completed {
    background: #F6FFED;
    .status-text { color: #52C41A; }
  }

  &.status-cancelled {
    background: #F5F5F5;
    .status-text { color: $color-muted; }
  }
}

.status-text {
  font-weight: $weight-medium;
}

// ── 底部时间 ──
.card-actions {
  margin-top: $space-content;
}

.card-time {
  font-size: $text-xs;
  color: $color-muted;
}

// ── 空状态 ──
.empty-state {
  flex: 1;
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

// ── 加载状态 ──
.loading-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.loading-text {
  font-size: $text-base;
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

<template>
  <view class="detail-page">
    <!-- 加载中 -->
    <view v-if="loading" class="detail-loading">
      <text class="loading-text">加载中...</text>
    </view>

    <!-- 加载失败 -->
    <view v-else-if="errorMsg" class="detail-error">
      <text class="error-icon">😕</text>
      <text class="error-text">{{ errorMsg }}</text>
      <button class="retry-btn" @click="loadDetail">重新加载</button>
    </view>

    <!-- 内容 -->
    <template v-else-if="product">
      <!-- 图片轮播 -->
      <ProductSwiper
        :images="product.images || []"
        :resolve-url="resolveImageUrl"
      />

      <!-- 价格区 -->
      <view class="price-section">
        <view class="price-row">
          <text class="price-current">¥{{ formatPrice(product.price) }}</text>
          <text v-if="product.original_price && product.original_price > product.price" class="price-original">
            ¥{{ formatPrice(product.original_price) }}
          </text>
        </view>
        <view class="price-status">
          <text class="status-tag" :class="'status-' + product.status">{{ statusLabel(product.status) }}</text>
        </view>
      </view>

      <!-- 标题 -->
      <view class="title-section">
        <text class="product-title">{{ product.title }}</text>
      </view>

      <!-- 标签区 -->
      <view class="tags-section">
        <text class="tag tag-condition">{{ product.condition }}</text>
        <text class="tag tag-category">{{ product.category }}</text>
        <text v-if="product.negotiable" class="tag tag-negotiable">可议价</text>
      </view>

      <!-- 描述 -->
      <view v-if="product.description" class="desc-section">
        <text class="section-title">商品描述</text>
        <text class="desc-text">{{ product.description }}</text>
      </view>

      <!-- 交易信息 -->
      <view class="info-section">
        <text class="section-title">交易信息</text>
        <view class="info-row">
          <text class="info-label">📍 交易地点</text>
          <text class="info-value">{{ product.trade_location }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">🕐 发布时间</text>
          <text class="info-value">{{ formatTime(product.created_at) }}</text>
        </view>
      </view>

      <!-- 卖家信息 -->
      <SellerInfo
        v-if="product.seller"
        :seller="product.seller"
        :is-owner="isOwner"
        :product-id="product.id"
        :default-avatar="defaultAvatar"
      />
    </template>

    <!-- 底部操作栏 -->
    <view v-if="product && !loading" class="bottom-actions">
      <button class="action-btn action-chat" @click="goChat">
        <text class="action-chat-icon">💬</text>
        <text>聊一聊</text>
      </button>
      <!-- 非本人发布的在售商品：我想要 -->
      <button
        v-if="!isOwner && product.status === 'active'"
        class="action-btn action-buy"
        :class="{ 'action-btn--disabled': !userStore.canTrade }"
        :disabled="submitting || !userStore.canTrade"
        @click="handleWant"
      >
        {{ submitting ? '处理中...' : '我想要' }}
      </button>
      <!-- 本人发布的商品：active/off_shelf 状态可编辑 -->
      <button
        v-if="isOwner && (product.status === 'active' || product.status === 'off_shelf')"
        class="action-btn action-edit"
        @click="goEdit"
      >
        编辑
      </button>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { detail as getDetail } from '@/api/product';
import { createOrder } from '@/api/order';
import { resolveImageUrl } from '@/api/index';
import { useUserStore } from '@/store/user';
import { useAppStore } from '@/store/app';
import { useChatHandler } from './composables/useChatHandler.js';
import { formatPrice, formatTime } from '@/utils/format';
import ProductSwiper from './components/ProductSwiper.vue';
import SellerInfo from './components/SellerInfo.vue';

const userStore = useUserStore();
const appStore = useAppStore();
const { goChat: doChatRaw } = useChatHandler();

/** 加载状态 */
const loading = ref(true);
const errorMsg = ref('');
const product = ref(null);
const currentId = ref(0);

/** 缺省头像 — 灰色圆形 SVG data URI */
const defaultAvatar =
  'data:image/svg+xml,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><circle cx="50" cy="50" r="50" fill="#F0F0F0"/></svg>'
  );

const isOwner = ref(false);
const submitting = ref(false);

onLoad((options) => {
  currentId.value = options.id ? parseInt(options.id, 10) : 0;
  if (!currentId.value) {
    errorMsg.value = '商品不存在';
    loading.value = false;
    return;
  }
  loadDetail();
});

async function loadDetail() {
  loading.value = true;
  errorMsg.value = '';
  try {
    const data = await getDetail(currentId.value);
    product.value = data;
    if (userStore.user && data.seller) {
      isOwner.value = userStore.user.id === data.seller.id;
    }
  } catch (err) {
    const msg = err.message || '加载失败';
    if (msg.includes('不存在')) {
      errorMsg.value = '商品不存在或已被删除';
    } else {
      errorMsg.value = msg;
    }
  } finally {
    loading.value = false;
  }
}

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

function goChat() {
  doChatRaw(product.value, userStore);
}

function goEdit() {
  if (!product.value) return;
  appStore.setPendingEditProductId(product.value.id);
  uni.switchTab({ url: '/pages/product/publish' });
}

async function handleWant() {
  if (!userStore.isLoggedIn) {
    uni.showToast({ title: '请先登录', icon: 'none', duration: 1500 });
    return;
  }

  if (!userStore.canTrade) {
    uni.showModal({
      title: '信誉分不足',
      content: '你的信誉分低于 30，无法发起交易。请通过完成其他交易来恢复信誉分。',
      showCancel: false,
      confirmText: '我知道了',
    });
    return;
  }

  if (isOwner.value) {
    uni.showToast({ title: '不能购买自己发布的商品', icon: 'none', duration: 1500 });
    return;
  }

  if (!product.value || product.value.status !== 'active') {
    uni.showToast({ title: '该商品当前不可交易', icon: 'none', duration: 1500 });
    return;
  }

  if (submitting.value) return;

  const { confirm } = await uni.showModal({
    title: '确认下单',
    content: '请确认是否要下单？下单后商品将标记为"正在交易"状态，其他人将无法购买。',
    confirmText: '确认下单',
    cancelText: '再想想',
  });
  if (!confirm) return;

  submitting.value = true;
  try {
    const order = await createOrder({ product_id: product.value.id });
    uni.showToast({ title: '下单成功', icon: 'success', duration: 1500 });
    uni.navigateTo({ url: `/pages/order/detail?id=${order.id}` });
  } catch (err) {
    const msg = err.message || '下单失败';
    if (msg.includes('3004') || msg.includes('商品已')) {
      uni.showToast({ title: '该商品已被其他人抢先下单', icon: 'none', duration: 2000 });
      loadDetail();
    } else if (msg.includes('3005') || msg.includes('自己')) {
      uni.showToast({ title: '不能购买自己发布的商品', icon: 'none', duration: 1500 });
    } else if (msg.includes('4009') || msg.includes('信誉')) {
      uni.showModal({
        title: '信誉分不足',
        content: '你的信誉分低于 30，无法发起交易。请通过完成其他交易来恢复信誉分。',
        showCancel: false,
      });
    } else {
      uni.showToast({ title: msg, icon: 'none', duration: 2000 });
    }
  } finally {
    submitting.value = false;
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.detail-page {
  min-height: 100vh;
  background: $color-bg;
  padding-bottom: calc(128rpx + env(safe-area-inset-bottom));
}

// ── 加载/错误状态 ──
.detail-loading,
.detail-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 200rpx 0;
}

.loading-text {
  font-size: $text-base;
  color: $color-muted;
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

.retry-btn {
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

// ── 价格区 ──
.price-section {
  background: $color-surface;
  padding: $space-card $space-page;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
}

.price-current {
  font-size: $text-3xl;
  font-weight: $weight-bold;
  color: $color-error;
  font-family: $font-mono;
}

.price-original {
  font-size: $text-sm;
  color: $color-muted;
  text-decoration: line-through;
}

.price-status {
  flex-shrink: 0;
}

.status-tag {
  padding: 6rpx 16rpx;
  border-radius: $radius-full;
  font-size: $text-xs;
}

.status-active {
  background: $color-primary-light;
  color: $color-primary;
}

.status-sold {
  background: #F0F0F0;
  color: $color-muted;
}

// ── 标题区 ──
.title-section {
  background: $color-surface;
  padding: 0 $space-page $space-card;
}

.product-title {
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: $color-title;
  line-height: 1.4;
}

// ── 标签区 ──
.tags-section {
  background: $color-surface;
  padding: 0 $space-page $space-card;
  display: flex;
  gap: 12rpx;
}

.tag {
  padding: 6rpx 16rpx;
  border-radius: $radius-full;
  font-size: $text-xs;
}

.tag-condition {
  background: #FFF7E6;
  color: #FA8C16;
}

.tag-category {
  background: $color-primary-light;
  color: $color-primary;
}

.tag-negotiable {
  background: #F6FFED;
  color: $color-success;
}

// ── 分区 ──
.desc-section,
.info-section {
  background: $color-surface;
  margin-top: 16rpx;
  padding: $space-card $space-page;
}

.section-title {
  font-size: $text-base;
  font-weight: $weight-bold;
  color: $color-title;
  display: block;
  margin-bottom: 16rpx;
}

// ── 描述 ──
.desc-text {
  font-size: $text-sm;
  color: $color-body;
  line-height: 1.8;
  white-space: pre-wrap;
}

// ── 交易信息 ──
.info-row {
  display: flex;
  justify-content: space-between;
  padding: 10rpx 0;
}

.info-label {
  font-size: $text-sm;
  color: $color-muted;
}

.info-value {
  font-size: $text-sm;
  color: $color-body;
}

// ── 底部操作栏 ──
.bottom-actions {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  gap: 16rpx;
  padding: 16rpx $space-page calc(16rpx + env(safe-area-inset-bottom));
  background: $color-surface;
  box-shadow: 0 -2rpx 16rpx rgba(0, 0, 0, 0.04);
}

.action-btn {
  height: $btn-height-md;
  border-radius: $radius-card;
  border: none;
  font-size: $text-base;
  font-weight: $weight-medium;
  display: flex;
  align-items: center;
  justify-content: center;

  &::after {
    border: none;
  }
}

.action-chat {
  flex: 1;
  background: $color-bg;
  color: $color-title;
}

.action-chat-icon {
  margin-right: 8rpx;
}

.action-buy {
  flex: 2;
  background: $color-primary-gradient;
  color: $color-surface;
  box-shadow: $shadow-button;
}

.action-edit {
  flex: 1;
  background: $color-bg;
  color: $color-body;
  border: 1px solid $color-divider;
}

.action-btn--disabled {
  background: $color-divider !important;
  color: $color-muted !important;
  box-shadow: none !important;
}
</style>

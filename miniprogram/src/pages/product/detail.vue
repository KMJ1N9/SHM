<template>
  <view class="detail-page">
    <!-- 加载中 -->
    <view v-if="loading" class="detail-loading">
      <text class="loading-text">
        加载中...
      </text>
    </view>

    <!-- 加载失败 -->
    <view v-else-if="errorMsg" class="detail-error">
      <text class="error-icon">
        😕
      </text>
      <text class="error-text">
        {{ errorMsg }}
      </text>
      <button class="retry-btn" @click="loadDetail">
        重新加载
      </button>
    </view>

    <!-- 内容 -->
    <template v-else-if="product">
      <!-- 图片轮播 -->
      <view class="image-swiper">
        <swiper
          class="swiper"
          :indicator-dots="product.images && product.images.length > 1"
          indicator-color="rgba(255,255,255,0.5)"
          indicator-active-color="#FFFFFF"
          :autoplay="false"
          circular
        >
          <swiper-item v-for="(url, i) in product.images" :key="i">
            <SafeImage
              class="swiper-image"
              :src="resolveImageUrl(url)"
              mode="aspectFill"
              @click="previewImages(i)"
            />
          </swiper-item>
        </swiper>
        <!-- 图片计数 -->
        <view v-if="product.images && product.images.length > 1" class="image-count">
          <text>{{ product.images.length }} 张图片</text>
        </view>
      </view>

      <!-- 价格区 -->
      <view class="price-section">
        <view class="price-row">
          <text class="price-current">
            ¥{{ formatPrice(product.price) }}
          </text>
          <text v-if="product.original_price && product.original_price > product.price" class="price-original">
            ¥{{ formatPrice(product.original_price) }}
          </text>
        </view>
        <view class="price-status">
          <text class="status-tag" :class="'status-' + product.status">
            {{ statusLabel(product.status) }}
          </text>
        </view>
      </view>

      <!-- 标题 -->
      <view class="title-section">
        <text class="product-title">
          {{ product.title }}
        </text>
      </view>

      <!-- 标签区 -->
      <view class="tags-section">
        <text class="tag tag-condition">
          {{ product.condition }}
        </text>
        <text class="tag tag-category">
          {{ product.category }}
        </text>
        <text v-if="product.negotiable" class="tag tag-negotiable">
          可议价
        </text>
      </view>

      <!-- 描述 -->
      <view v-if="product.description" class="desc-section">
        <text class="section-title">
          商品描述
        </text>
        <text class="desc-text">
          {{ product.description }}
        </text>
      </view>

      <!-- 交易信息 -->
      <view class="info-section">
        <text class="section-title">
          交易信息
        </text>
        <view class="info-row">
          <text class="info-label">
            📍 交易地点
          </text>
          <text class="info-value">
            {{ product.trade_location }}
          </text>
        </view>
        <view class="info-row">
          <text class="info-label">
            🕐 发布时间
          </text>
          <text class="info-value">
            {{ formatTime(product.created_at) }}
          </text>
        </view>
      </view>

      <!-- 卖家信息 -->
      <view v-if="product.seller" class="seller-section" @click="goProfile">
        <text class="section-title">
          卖家信息
        </text>
        <view class="seller-card">
          <image
            class="seller-avatar"
            :src="product.seller.avatar || defaultAvatar"
            mode="aspectFill"
          />
          <view class="seller-info">
            <text class="seller-name">
              {{ product.seller.nickname }}
            </text>
            <text v-if="product.seller.class_name" class="seller-detail">
              {{ product.seller.class_name }}
            </text>
            <text v-if="product.seller.dorm_building" class="seller-detail">
              {{ product.seller.dorm_building }}
            </text>
          </view>
          <view class="seller-credit">
            <text class="credit-score">
              {{ product.seller.credit_score || 100 }}
            </text>
            <text class="credit-label">
              信誉分
            </text>
          </view>
          <text class="seller-arrow">
            ›
          </text>
        </view>
      </view>
    </template>

    <!-- 底部操作栏 -->
    <view v-if="product && !loading" class="bottom-actions">
      <button class="action-btn action-chat" @click="goChat">
        <text class="action-chat-icon">
          💬
        </text>
        <text>聊一聊</text>
      </button>
      <!-- 本人发布的商品显示编辑入口 -->
      <button
        v-if="isOwner"
        class="action-btn action-manage"
        @click="goManage"
      >
        管理
      </button>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { detail as getDetail } from '@/api/product';
import { resolveImageUrl } from '@/api/index';
import SafeImage from '@/components/SafeImage.vue';
import { useUserStore } from '@/store/user';
import { isIMReady, waitForReady, reInitIM, getLastError, cachePeerProfile } from '@/utils/im';
import { ensureAccount } from '@/api/im';

const userStore = useUserStore();

/** 加载状态 */
const loading = ref(true);
const errorMsg = ref('');
const product = ref(null);

/** 当前商品 ID */
const currentId = ref(0);

/**
 * 缺省头像 — 灰色圆形 SVG data URI
 * 当 seller.avatar 为 null/undefined 时显示，避免渲染破损图片图标
 */
const defaultAvatar =
  'data:image/svg+xml,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><circle cx="50" cy="50" r="50" fill="#F0F0F0"/></svg>'
  );

/** 是否为自己的商品 */
const isOwner = ref(false);

/**
 * 页面加载
 */
onLoad((options) => {
  currentId.value = options.id ? parseInt(options.id, 10) : 0;
  if (!currentId.value) {
    errorMsg.value = '商品不存在';
    loading.value = false;
    return;
  }
  loadDetail();
});

/**
 * 加载商品详情（从 currentId ref 读取商品 ID）
 */
async function loadDetail() {
  loading.value = true;
  errorMsg.value = '';
  try {
    const data = await getDetail(currentId.value);
    product.value = data;
    // 判断是否为自己的商品
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
    const now = new Date();
    const diff = now - d;
    if (diff < 60 * 1000) return '刚刚';
    if (diff < 60 * 60 * 1000) return `${Math.floor(diff / (60 * 1000))} 分钟前`;
    if (diff < 24 * 60 * 60 * 1000) return `${Math.floor(diff / (60 * 60 * 1000))} 小时前`;
    const month = d.getMonth() + 1;
    const day = d.getDate();
    return `${month} 月 ${day} 日`;
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
 * 预览大图
 */
function previewImages(index) {
  if (product.value && product.value.images) {
    const resolved = product.value.images.map(resolveImageUrl);
    uni.previewImage({
      current: resolved[index],
      urls: resolved,
    });
  }
}

/**
 * 跳转卖家个人主页（暂未实现）
 */
function goProfile() {
  if (product.value && product.value.seller) {
    // TODO: 跳转用户个人主页
    // uni.navigateTo({ url: `/pages/user/profile?id=${product.value.seller.id}` });
  }
}

/**
 * 发起聊天（接入 IM SDK）
 *
 * 流程：
 *   1. 校验商品和卖家信息
 *   2. 防止和自己聊天
 *   3. 确保 IM SDK 已就绪
 *   4. 构造 conversationID（C2C{sellerId}） → 跳转聊天详情页
 */
async function goChat() {
  if (!product.value || !product.value.seller) {
    uni.showToast({ title: '卖家信息不可用', icon: 'none', duration: 1500 });
    return;
  }

  const myId = userStore.user?.id;
  const sellerId = product.value.seller.id;

  if (!myId) {
    uni.showToast({ title: '请先登录', icon: 'none', duration: 1500 });
    return;
  }

  // 不能和自己聊天
  if (myId === sellerId) {
    uni.showToast({ title: '这是你自己发布的商品', icon: 'none', duration: 1500 });
    return;
  }

  // 确保 IM SDK 已就绪
  if (!isIMReady()) {
    uni.showToast({ title: '消息服务连接中…', icon: 'none', duration: 2000 });
    try {
      // 主动尝试重新初始化（InitIM 在 onLaunch 中可能已失败）
      await reInitIM();
    } catch (err) {
      const reason = getLastError() || err.message || '未知错误';
      console.error('[goChat] IM 初始化失败:', reason);
      uni.showModal({
        title: '消息服务连接失败',
        content: `无法连接到聊天服务。\n\n原因: ${reason}\n\n请检查网络后重试。如持续失败，请截图联系开发者。`,
        showCancel: false,
      });
      return;
    }
  }

  // 最终确认
  if (!isIMReady()) {
    uni.showToast({ title: '消息服务未就绪，请稍后再试', icon: 'none', duration: 2000 });
    return;
  }

  // conversationID 格式：C2C + 对方 userId（腾讯云 IM C2C 规范）
  const conversationID = `C2C${sellerId}`;
  const sellerNickname = product.value.seller.nickname || '用户';
  const sellerAvatar = product.value.seller.avatar || '';

  // 缓存卖家资料到本地 — 消息列表页 mapConversation() 会优先从缓存读取昵称，
  // 避免 IM SDK userProfile 为空时降级显示 "用户X"
  cachePeerProfile(sellerId, sellerNickname, sellerAvatar);

  // 确保卖家 IM 账号已导入，同时同步昵称和头像到 IM 资料系统，
  // 防止 IM 服务端因缺少资料而生成 "用户X" 占位昵称
  ensureAccount(sellerId, sellerNickname, sellerAvatar).catch(() => {
    // 导入失败不阻塞跳转 —— 若账号已存在，发消息仍会成功
  });

  uni.navigateTo({
    url: `/pages/chat/detail?conversationId=${conversationID}&nickname=${encodeURIComponent(sellerNickname)}&avatar=${encodeURIComponent(sellerAvatar)}`,
  });
}

/**
 * 管理商品（本人发布）
 */
function goManage() {
  uni.showToast({ title: '商品管理功能即将上线', icon: 'none', duration: 1500 });
}
</script>

<style lang="scss">
@import '@/styles/tokens.scss';

.detail-page {
  min-height: 100vh;
  background: $color-bg;
  padding-bottom: calc(128rpx + env(safe-area-inset-bottom));
}

// ── 加载/错误状态 ────────────────────────────────────────
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

// ── 图片轮播 ────────────────────────────────────────────
.image-swiper {
  position: relative;
  background: #000000;
}

.swiper {
  width: 100%;
  height: 660rpx;
}

.swiper-image {
  width: 100%;
  height: 100%;
}

.image-count {
  position: absolute;
  bottom: 16rpx;
  right: 16rpx;
  padding: 4rpx 16rpx;
  background: rgba(0, 0, 0, 0.5);
  border-radius: $radius-full;
}

.image-count text {
  font-size: $text-xs;
  color: #FFFFFF;
}

// ── 价格区 ──────────────────────────────────────────────
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

// ── 标题区 ──────────────────────────────────────────────
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

// ── 标签区 ──────────────────────────────────────────────
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

// ── 分区 ────────────────────────────────────────────────
.desc-section,
.info-section,
.seller-section {
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

// ── 描述 ────────────────────────────────────────────────
.desc-text {
  font-size: $text-sm;
  color: $color-body;
  line-height: 1.8;
  white-space: pre-wrap;
}

// ── 交易信息 ────────────────────────────────────────────
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

// ── 卖家信息 ────────────────────────────────────────────
.seller-card {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.seller-avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: $color-divider;
  flex-shrink: 0;
}

.seller-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.seller-name {
  font-size: $text-base;
  font-weight: $weight-medium;
  color: $color-title;
}

.seller-detail {
  font-size: $text-xs;
  color: $color-muted;
  margin-top: 4rpx;
}

.seller-credit {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
}

.credit-score {
  font-size: $text-xl;
  font-weight: $weight-bold;
  color: $color-success;
  font-family: $font-mono;
}

.credit-label {
  font-size: 20rpx;
  color: $color-muted;
}

.seller-arrow {
  font-size: 36rpx;
  color: $color-muted;
  flex-shrink: 0;
}

// ── 底部操作栏 ──────────────────────────────────────────
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

.action-manage {
  flex: 1;
  background: $color-bg;
  color: $color-body;
  border: 1px solid $color-divider;
}
</style>

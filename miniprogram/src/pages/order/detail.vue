<template>
  <view class="detail-page">
    <!-- ============================================================ -->
    <!-- 加载中 -->
    <!-- ============================================================ -->
    <view v-if="loading" class="state-center">
      <text class="state-text">
        加载中...
      </text>
    </view>

    <!-- ============================================================ -->
    <!-- 加载失败 -->
    <!-- ============================================================ -->
    <view v-else-if="errorMsg" class="state-center">
      <text class="error-icon">
        😕
      </text>
      <text class="error-text">
        {{ errorMsg }}
      </text>
      <button class="retry-btn" @click="loadOrder">
        重新加载
      </button>
    </view>

    <!-- ============================================================ -->
    <!-- 订单内容 -->
    <!-- ============================================================ -->
    <template v-else-if="order">
      <!-- ── 状态时间线 ── -->
      <view class="section-card">
        <text class="section-title">
          订单进度
        </text>
        <view class="timeline">
          <!-- 节点 1：已下单 -->
          <view class="tl-node" :class="{ 'tl-node--active': timelineActive(0) }">
            <view class="tl-dot" />
            <view class="tl-content">
              <text class="tl-label">
                已下单
              </text>
              <text class="tl-time">
                {{ formatDateTime(order.created_at) }}
              </text>
            </view>
          </view>
          <!-- 节点 2：已面交 -->
          <view class="tl-node" :class="{ 'tl-node--active': timelineActive(1) }">
            <view class="tl-dot" />
            <view class="tl-content">
              <text class="tl-label">
                已面交
              </text>
              <text v-if="order.met_at" class="tl-time">
                {{ formatDateTime(order.met_at) }}
              </text>
              <text v-else class="tl-time tl-time--pending">
                待完成
              </text>
            </view>
          </view>
          <!-- 节点 3：已确认收货 -->
          <view class="tl-node" :class="{ 'tl-node--active': timelineActive(2) }">
            <view class="tl-dot" />
            <view class="tl-content">
              <text class="tl-label">
                已确认收货
              </text>
              <text v-if="order.confirmed_at" class="tl-time">
                {{ formatDateTime(order.confirmed_at) }}
              </text>
              <text v-else class="tl-time tl-time--pending">
                待完成
              </text>
            </view>
          </view>
          <!-- 节点 4：已评价 — 动态显示互评完成度 -->
          <view class="tl-node" :class="{ 'tl-node--active': timelineActive(3) }">
            <view class="tl-dot" />
            <view class="tl-content">
              <text class="tl-label">
                {{ reviewCompletion.label }}
              </text>
              <text
                v-if="reviewCompletion.done === 1"
                class="tl-time tl-time--pending"
              >
                等待对方评价
              </text>
              <text
                v-else-if="reviewCompletion.done === 0"
                class="tl-time tl-time--pending"
              >
                待完成
              </text>
            </view>
          </view>
        </view>
      </view>

      <!-- ── 商品信息 ── -->
      <view class="section-card">
        <text class="section-title">
          商品信息
        </text>
        <view class="product-row" @click="goProduct">
          <SafeImage
            class="product-image"
            :src="resolveImageUrl(productImage)"
            mode="aspectFill"
          />
          <view class="product-info">
            <text class="product-title">
              {{ productTitle }}
            </text>
            <text class="product-condition">
              {{ productCondition }}
            </text>
            <view class="product-price-row">
              <text class="product-price">
                ¥{{ formatPrice(productPrice) }}
              </text>
            </view>
            <text class="product-location">
              {{ productLocation }}
            </text>
          </view>
          <text class="arrow-right">
            ›
          </text>
        </view>
      </view>

      <!-- ── 交易对象 ── -->
      <view class="section-card">
        <text class="section-title">
          交易对象
        </text>
        <view class="partner-row">
          <image
            v-if="partnerAvatar"
            class="partner-avatar"
            :src="partnerAvatar"
            mode="aspectFill"
          />
          <view v-else class="partner-avatar partner-avatar--default">
            <text class="partner-avatar-emoji">
              👤
            </text>
          </view>
          <text class="partner-name">
            {{ partnerName }}
          </text>
        </view>
      </view>

      <!-- ── 订单信息 ── -->
      <view class="section-card">
        <text class="section-title">
          订单信息
        </text>
        <view class="info-item">
          <text class="info-label">
            订单编号
          </text>
          <text class="info-value" selectable>
            #{{ order.id }}
          </text>
        </view>
        <view class="info-item">
          <text class="info-label">
            创建时间
          </text>
          <text class="info-value">
            {{ formatDateTime(order.created_at) }}
          </text>
        </view>
        <view class="info-item">
          <text class="info-label">
            当前状态
          </text>
          <view class="status-badge" :class="'status-' + order.status">
            <text class="status-text">
              {{ statusLabel(order.status) }}
            </text>
          </view>
        </view>
        <view v-if="order.cancelled_by" class="info-item">
          <text class="info-label">
            取消方
          </text>
          <text class="info-value">
            {{ order.cancelled_by === 'buyer' ? '买家' : '卖家' }}
          </text>
        </view>
      </view>

      <!-- ── 评价信息 ── -->
      <view v-if="order.status === STATUS.COMPLETED" class="section-card">
        <text class="section-title">
          评价信息
        </text>

        <!-- 互评进度条 -->
        <view class="review-progress">
          <view class="review-progress-bar">
            <view
              class="review-progress-fill"
              :style="{ width: (reviewCompletion.done / 2 * 100) + '%' }"
            />
          </view>
          <text class="review-progress-text">
            {{ reviewCompletion.done }}/2
          </text>
        </view>
        <text
          v-if="reviewCompletion.done === 1"
          class="review-progress-hint"
        >
          {{ myReview ? '对方尚未评价，评价后双方可见' : '你尚未评价，请先完成评价' }}
        </text>
        <text
          v-else-if="reviewCompletion.done === 0"
          class="review-progress-hint"
        >
          双方完成评价后可见评价详情
        </text>

        <!-- 已有评价列表 -->
        <view
          v-for="rv in reviews"
          :key="rv.id"
          class="review-item"
        >
          <view class="review-item-header">
            <image
              v-if="rv.reviewer_avatar"
              class="review-item-avatar"
              :src="rv.reviewer_avatar"
              mode="aspectFill"
            />
            <view v-else class="review-item-avatar review-item-avatar--default">
              <text class="review-item-avatar-emoji">
                👤
              </text>
            </view>
            <text class="review-item-name">
              {{ rv.reviewer_nickname || '匿名用户' }}
            </text>
            <text class="review-item-time">
              {{ formatDateTime(rv.created_at) }}
            </text>
          </view>
          <!-- 三维评分 -->
          <view class="review-item-scores">
            <view class="review-dim-item">
              <text class="review-dim-label">
                沟通
              </text>
              <StarRating
                :model-value="rv.communication_score"
                :readonly="true"
                size="sm"
              />
            </view>
            <view class="review-dim-item">
              <text class="review-dim-label">
                守时
              </text>
              <StarRating
                :model-value="rv.punctuality_score"
                :readonly="true"
                size="sm"
              />
            </view>
            <view class="review-dim-item">
              <text class="review-dim-label">
                描述
              </text>
              <StarRating
                :model-value="rv.accuracy_score"
                :readonly="true"
                size="sm"
              />
            </view>
          </view>
          <!-- 文字评价 -->
          <text v-if="rv.comment" class="review-item-comment">
            "{{ rv.comment }}"
          </text>
        </view>
      </view>

      <!-- ── 操作按钮区 ── -->
      <view v-if="showActions" class="actions-bar">
        <button
          v-if="canCancel"
          class="action-btn action-btn--secondary"
          @click="handleCancel"
        >
          取消订单
        </button>
        <button
          v-if="canMarkMet"
          class="action-btn action-btn--primary"
          @click="handleMarkMet"
        >
          标记面交
        </button>
        <button
          v-if="canConfirm"
          class="action-btn action-btn--primary"
          @click="handleConfirm"
        >
          确认收货
        </button>
        <button
          v-if="canReview"
          class="action-btn action-btn--primary"
          @click="showReviewModal = true"
        >
          去评价
        </button>
        <button
          v-if="canReport"
          class="action-btn action-btn--report"
          @click="goReport"
        >
          举报
        </button>
      </view>

      <!-- ── 评价弹窗 ── -->
      <view v-if="showReviewModal" class="review-modal-mask" @click="closeReviewModal">
        <view class="review-modal" @click.stop>
          <text class="review-modal-title">
            评价 {{ partnerName }}
          </text>

          <!-- 沟通态度 -->
          <view class="review-dim">
            <text class="review-dim-label">
              沟通态度
            </text>
            <StarRating
              v-model="reviewForm.communicationScore"
              size="lg"
            />
          </view>

          <!-- 守时程度 -->
          <view class="review-dim">
            <text class="review-dim-label">
              守时程度
            </text>
            <StarRating
              v-model="reviewForm.punctualityScore"
              size="lg"
            />
          </view>

          <!-- 描述一致度 -->
          <view class="review-dim">
            <text class="review-dim-label">
              描述一致度
            </text>
            <StarRating
              v-model="reviewForm.accuracyScore"
              size="lg"
            />
          </view>

          <!-- 文字评价 -->
          <view class="review-comment-box">
            <textarea
              v-model="reviewForm.comment"
              class="review-textarea"
              placeholder="说说这次交易的感受吧（选填）"
              :maxlength="500"
              auto-height
            />
            <text class="review-comment-count">
              {{ reviewForm.comment.length }}/500
            </text>
          </view>

          <!-- 操作 -->
          <view class="review-modal-actions">
            <button
              class="review-btn review-btn--cancel"
              @click="closeReviewModal"
            >
              取消
            </button>
            <button
              class="review-btn review-btn--submit"
              :disabled="reviewSubmitting"
              @click="submitReview"
            >
              {{ reviewSubmitting ? '提交中...' : '提交评价' }}
            </button>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
/**
 * 订单详情页 — 状态时间线 + 商品快照 + 交易对象 + 动态操作按钮
 *
 * 根据订单状态和当前用户角色（买家/卖家）动态显示可执行操作。
 * 操作后自动刷新订单数据。
 */

import { ref, computed } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import { getOrderDetail, markAsMet, confirmOrder, cancelOrder } from '@/api/order';
import { createReview, getReviewsByOrder } from '@/api/review';
import { resolveImageUrl } from '@/api/index';
import { useUserStore } from '@/store/user';
import SafeImage from '@/components/SafeImage.vue';
import StarRating from '@/components/StarRating.vue';

// ============================================================
// 订单状态常量
// ============================================================
const STATUS = {
  PENDING: 'pending',
  MET: 'met',
  COMPLETED: 'completed',
  CANCELLED: 'cancelled',
};

/** 状态 → 中文标签 */
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
const order = ref(null);
const loading = ref(true);
const errorMsg = ref('');
const showReviewModal = ref(false);

/** 当前订单的评价列表 */
const reviews = ref([]);

// ============================================================
// 评价表单状态
// ============================================================
const reviewSubmitting = ref(false);
const reviewForm = ref({
  communicationScore: 5,
  punctualityScore: 5,
  accuracyScore: 5,
  comment: '',
});

const userStore = useUserStore();

// ============================================================
// 角色判断
// ============================================================
const myId = computed(() => userStore.user?.id);
const isBuyer = computed(() => order.value?.buyer_id === myId.value);
const isSeller = computed(() => order.value?.seller_id === myId.value);

// ============================================================
// 时间线节点激活判断
// ============================================================
function timelineActive(nodeIndex) {
  if (!order.value) return false;
  const status = order.value.status;

  // 节点 0 (已下单)：所有状态都激活
  if (nodeIndex === 0) return true;
  // 节点 1 (已面交)：met / completed 状态激活
  if (nodeIndex === 1) return status === STATUS.MET || status === STATUS.COMPLETED;
  // 节点 2 (已确认收货)：completed 状态激活
  if (nodeIndex === 2) return status === STATUS.COMPLETED;
  // 节点 3 (已评价)：双方都完成评价后激活
  if (nodeIndex === 3) return reviewCompletion.value.active;

  return false;
}

// ============================================================
// 操作按钮显示逻辑
// ============================================================
const showActions = computed(() => {
  return order.value?.status !== STATUS.CANCELLED;
});

const canCancel = computed(() => {
  if (!order.value) return false;
  const status = order.value.status;
  // pending：买家/卖家均可取消
  if (status === STATUS.PENDING) return isBuyer.value || isSeller.value;
  // met：仅买家可取消
  if (status === STATUS.MET) return isBuyer.value;
  return false;
});

const canMarkMet = computed(() => {
  return order.value?.status === STATUS.PENDING
    && (isBuyer.value || isSeller.value);
});

const canConfirm = computed(() => {
  return order.value?.status === STATUS.MET && isBuyer.value;
});

/** 当前用户对该订单的评价（若有） */
const myReview = computed(() => {
  return reviews.value.find(r => r.reviewer_id === myId.value) || null;
});

const canReport = computed(() => {
  // completed 状态可举报交易对方（任一角色）
  return order.value?.status === STATUS.COMPLETED;
});

const canReview = computed(() => {
  // completed 状态 且 当前用户尚未评价对方
  return order.value?.status === STATUS.COMPLETED && !myReview.value;
});

/** 双方互评完成度 */
const reviewCompletion = computed(() => {
  const total = reviews.value.length;
  if (total === 0) return { done: 0, total: 2, active: false, label: '待完成' };
  if (total === 1) return { done: 1, total: 2, active: false, label: '已评价 (1/2)' };
  return { done: 2, total: 2, active: true, label: '已完成互评' };
});

// ============================================================
// 交易对方信息
// ============================================================
const partnerName = computed(() => {
  if (!order.value) return '';
  if (isBuyer.value) {
    return order.value.seller_nickname || '卖家';
  }
  return order.value.buyer_nickname || '买家';
});

const partnerAvatar = computed(() => {
  if (!order.value) return '';
  if (isBuyer.value) {
    return order.value.seller_avatar || '';
  }
  return order.value.buyer_avatar || '';
});

// ============================================================
// 商品快照解析
// ============================================================
function parseSnapshot() {
  if (!order.value?.product_snapshot) return {};
  try {
    return typeof order.value.product_snapshot === 'string'
      ? JSON.parse(order.value.product_snapshot)
      : order.value.product_snapshot;
  } catch (err) {
    console.warn('[OrderDetail] 解析商品快照失败:', err);
    return {};
  }
}

const productImage = computed(() => {
  const s = parseSnapshot();
  const images = s.images;
  return Array.isArray(images) && images.length > 0 ? images[0] : '';
});

const productTitle = computed(() => parseSnapshot().title || '商品');
const productCondition = computed(() => parseSnapshot().condition || '');
const productPrice = computed(() => parseSnapshot().price || 0);
const productLocation = computed(() => parseSnapshot().trade_location || '');

// ============================================================
// 数据加载
// ============================================================
let orderId = null;

onLoad((options) => {
  orderId = options.id;
  if (!orderId) {
    errorMsg.value = '缺少订单 ID';
    loading.value = false;
    return;
  }
  loadOrder();
});

onShow(() => {
  // 从评价弹窗返回后刷新
  if (orderId && !loading.value) {
    loadOrder();
  }
});

async function loadOrder() {
  loading.value = true;
  errorMsg.value = '';
  try {
    const [orderData, reviewData] = await Promise.all([
      getOrderDetail(orderId),
      getReviewsByOrder(orderId).catch(() => ({ list: [] })),
    ]);
    order.value = orderData;
    reviews.value = reviewData.list || [];
  } catch (err) {
    errorMsg.value = err.message || '加载失败';
    order.value = null;
  } finally {
    loading.value = false;
  }
}

// ============================================================
// 操作处理
// ============================================================
async function handleMarkMet() {
  uni.showModal({
    title: '确认面交',
    content: '确认已与对方完成面交？',
    success: async (res) => {
      if (!res.confirm) return;
      uni.showLoading({ title: '操作中...', mask: true });
      try {
        await markAsMet(order.value.id);
        uni.hideLoading();
        uni.showToast({ title: '已确认面交', icon: 'success' });
        await loadOrder();
      } catch (err) {
        uni.hideLoading();
        uni.showToast({ title: err.message || '操作失败', icon: 'error' });
      }
    },
  });
}

async function handleConfirm() {
  uni.showModal({
    title: '确认收货',
    content: '确认已收到商品？确认后订单将完成，并开放双方互评。',
    success: async (res) => {
      if (!res.confirm) return;
      uni.showLoading({ title: '操作中...', mask: true });
      try {
        await confirmOrder(order.value.id);
        uni.hideLoading();
        uni.showToast({ title: '已确认收货，请评价', icon: 'success' });
        await loadOrder();
      } catch (err) {
        uni.hideLoading();
        uni.showToast({ title: err.message || '操作失败', icon: 'error' });
      }
    },
  });
}

async function handleCancel() {
  uni.showModal({
    title: '取消订单',
    content: '确认取消该订单？',
    success: async (res) => {
      if (!res.confirm) return;
      uni.showLoading({ title: '操作中...', mask: true });
      try {
        await cancelOrder(order.value.id);
        uni.hideLoading();
        uni.showToast({ title: '订单已取消', icon: 'success' });
        await loadOrder();
      } catch (err) {
        uni.hideLoading();
        uni.showToast({ title: err.message || '操作失败', icon: 'error' });
      }
    },
  });
}

/**
 * 举报交易对方 — 跳转举报提交页
 */
function goReport() {
  if (!order.value) return;
  const reportedUserId = isBuyer.value
    ? order.value.seller_id
    : order.value.buyer_id;
  uni.navigateTo({
    url: `/pages/report/submit?product_id=${order.value.product_id || ''}&order_id=${order.value.id}&reported_user_id=${reportedUserId}`,
  });
}

// ============================================================
// 评价操作
// ============================================================
function closeReviewModal() {
  showReviewModal.value = false;
  // 重置表单
  reviewForm.value = {
    communicationScore: 5,
    punctualityScore: 5,
    accuracyScore: 5,
    comment: '',
  };
}

async function submitReview() {
  if (reviewSubmitting.value) return;

  reviewSubmitting.value = true;
  try {
    await createReview({
      order_id: order.value.id,
      reviewee_id: isBuyer.value ? order.value.seller_id : order.value.buyer_id,
      communication_score: reviewForm.value.communicationScore,
      punctuality_score: reviewForm.value.punctualityScore,
      accuracy_score: reviewForm.value.accuracyScore,
      comment: reviewForm.value.comment || undefined,
    });
    // 将新评价加入本地列表（避免额外网络请求，且即时更新 UI）
    reviews.value.push({
      id: 'temp_' + Date.now(), // 临时 ID，下次 loadOrder 时被真实数据覆盖
      order_id: order.value.id,
      reviewer_id: myId.value,
      reviewee_id: isBuyer.value ? order.value.seller_id : order.value.buyer_id,
      communication_score: reviewForm.value.communicationScore,
      punctuality_score: reviewForm.value.punctualityScore,
      accuracy_score: reviewForm.value.accuracyScore,
      comment: reviewForm.value.comment || '',
      reviewer_nickname: userStore.user?.nickname || '我',
      reviewer_avatar: userStore.user?.avatar || '',
      created_at: new Date().toISOString(),
    });
    uni.showToast({ title: '评价成功', icon: 'success' });
    closeReviewModal();
    // 后台静默刷新（获取真实 ID + 对方可能也已评价）
    await loadOrder();
  } catch (err) {
    const msg = err.message || '评价失败';
    if (msg.includes('3006') || msg.includes('重复') || msg.includes('已评价')) {
      uni.showToast({ title: '你已经评价过该订单了', icon: 'none', duration: 2000 });
      closeReviewModal();
    } else if (msg.includes('3001') || msg.includes('状态')) {
      uni.showToast({ title: '仅可评价已完成的订单', icon: 'none', duration: 2000 });
      closeReviewModal();
      await loadOrder();
    } else {
      uni.showToast({ title: msg, icon: 'none', duration: 2000 });
    }
  } finally {
    reviewSubmitting.value = false;
  }
}

// ============================================================
// 跳转
// ============================================================
function goProduct() {
  if (order.value?.product_id) {
    uni.navigateTo({ url: `/pages/product/detail?id=${order.value.product_id}` });
  }
}

// ============================================================
// 辅助函数
// ============================================================
function formatPrice(price) {
  const num = parseFloat(price);
  if (Number.isNaN(num)) return '0';
  return num % 1 === 0 ? String(num) : num.toFixed(2);
}

function formatDateTime(isoString) {
  if (!isoString) return '';
  const d = new Date(isoString);
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  const hour = String(d.getHours()).padStart(2, '0');
  const min = String(d.getMinutes()).padStart(2, '0');
  return `${month}-${day} ${hour}:${min}`;
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.detail-page {
  min-height: 100vh;
  background: $color-bg;
  padding-bottom: calc(120rpx + $safe-area-bottom);
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

// ── 区块卡片 ──
.section-card {
  background: $color-surface;
  margin: $space-card $space-page;
  padding: $space-card;
  border-radius: $radius-card;
  box-shadow: $shadow-card;
}

.section-title {
  font-size: $text-base;
  font-weight: $weight-medium;
  color: $color-title;
  margin-bottom: $space-content;
  display: block;
}

// ── 时间线 ──
.timeline {
  padding-left: 8rpx;
}

.tl-node {
  display: flex;
  align-items: flex-start;
  padding-bottom: $space-card;

  &:last-child {
    padding-bottom: 0;
  }
}

.tl-dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
  background: $color-divider;
  margin-top: 4rpx;
  margin-right: $space-content;
  flex-shrink: 0;
  position: relative;

  // 连接线
  &::after {
    content: '';
    position: absolute;
    top: 24rpx;
    left: 9rpx;
    width: 2rpx;
    height: calc(100% + 8rpx);
    background: $color-divider;
  }

  .tl-node:last-child &::after {
    display: none;
  }
}

.tl-node--active {
  .tl-dot {
    background: $color-primary;
    box-shadow: 0 0 0 4rpx $color-primary-light;

    &::after {
      background: $color-primary;
    }
  }

  .tl-label {
    color: $color-title;
    font-weight: $weight-medium;
  }
}

.tl-content {
  flex: 1;
}

.tl-label {
  font-size: $text-base;
  color: $color-muted;
  display: block;
}

.tl-node--active .tl-label {
  color: $color-title;
}

.tl-time {
  font-size: $text-sm;
  color: $color-muted;
  margin-top: 2rpx;
  display: block;
}

.tl-time--pending {
  color: $color-divider;
}

// ── 商品信息 ──
.product-row {
  display: flex;
  align-items: center;
  gap: $space-card;
}

.product-image {
  width: 120rpx;
  height: 120rpx;
  border-radius: $radius-card;
  flex-shrink: 0;
  background: $color-divider;
}

.product-info {
  flex: 1;
  overflow: hidden;
}

.product-title {
  font-size: $text-base;
  font-weight: $weight-medium;
  color: $color-title;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}

.product-condition {
  font-size: $text-sm;
  color: $color-muted;
  margin-top: 4rpx;
}

.product-price-row {
  margin-top: 4rpx;
}

.product-price {
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: $color-error;
  font-family: $font-mono;
}

.product-location {
  font-size: $text-sm;
  color: $color-muted;
  margin-top: 2rpx;
}

.arrow-right {
  font-size: 40rpx;
  color: $color-divider;
  flex-shrink: 0;
}

// ── 交易对象 ──
.partner-row {
  display: flex;
  align-items: center;
  gap: $space-content;
}

.partner-avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: $color-divider;
  flex-shrink: 0;

  &--default {
    display: flex;
    align-items: center;
    justify-content: center;
  }
}

.partner-avatar-emoji {
  font-size: 36rpx;
}

.partner-name {
  font-size: $text-base;
  color: $color-title;
  font-weight: $weight-medium;
}

// ── 订单信息 ──
.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: $space-content 0;

  & + & {
    border-top: 1rpx solid $color-divider;
  }
}

.info-label {
  font-size: $text-sm;
  color: $color-muted;
}

.info-value {
  font-size: $text-sm;
  color: $color-body;
}

// 状态标签
.status-badge {
  padding: 2rpx 16rpx;
  border-radius: $radius-full;

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
  font-size: $text-xs;
  font-weight: $weight-medium;
}

// ── 操作按钮 ──
.actions-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: $color-surface;
  padding: $space-card $space-page;
  padding-bottom: calc($space-card + $safe-area-bottom);
  display: flex;
  gap: $space-card;
  box-shadow: 0 -2rpx 16rpx rgba(0, 0, 0, 0.04);
}

.action-btn {
  flex: 1;
  height: $btn-height-md;
  border-radius: $radius-card;
  font-size: $text-base;
  font-weight: $weight-medium;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;

  &::after {
    border: none;
  }

  &--primary {
    background: $color-primary-gradient;
    color: #FFFFFF;
  }

  &--secondary {
    background: $color-surface;
    color: $color-body;
    border: 1rpx solid $color-divider;
  }

  &--report {
    flex: 0.5;
    background: $color-surface;
    color: $color-muted;
    border: 1rpx solid $color-divider;
    font-size: $text-xs;
  }
}

// ── 评价弹窗 ──
.review-modal-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 1000;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.review-modal {
  width: 100%;
  max-height: 85vh;
  background: $color-surface;
  border-radius: $radius-card $radius-card 0 0;
  padding: 40rpx $space-page calc(40rpx + $safe-area-bottom);
  overflow-y: auto;
}

.review-modal-title {
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: $color-title;
  text-align: center;
  display: block;
  margin-bottom: 40rpx;
}

.review-dim {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: $space-content 0;

  & + & {
    border-top: 1rpx solid $color-divider;
  }
}

.review-dim-label {
  font-size: $text-base;
  color: $color-body;
}

// ── 文字评价 ──
.review-comment-box {
  margin-top: $space-card;
  padding: $space-content;
  background: $color-bg;
  border-radius: $radius-card;
  position: relative;
}

.review-textarea {
  width: 100%;
  min-height: 120rpx;
  font-size: $text-sm;
  color: $color-body;
  line-height: $line-height;
}

.review-comment-count {
  text-align: right;
  font-size: $text-xs;
  color: $color-muted;
  display: block;
  margin-top: 4rpx;
}

// ── 弹窗按钮 ──
.review-modal-actions {
  display: flex;
  gap: $space-card;
  margin-top: 32rpx;
}

.review-btn {
  flex: 1;
  height: $btn-height-md;
  border-radius: $radius-card;
  font-size: $text-base;
  font-weight: $weight-medium;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;

  &::after {
    border: none;
  }

  &--cancel {
    background: $color-bg;
    color: $color-body;
  }

  &--submit {
    background: $color-primary-gradient;
    color: #FFFFFF;

    &[disabled] {
      opacity: 0.5;
    }
  }
}

// ── 评价信息展示（订单详情页） ──

// 互评进度条
.review-progress {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: $space-content;
}

.review-progress-bar {
  flex: 1;
  height: 12rpx;
  background: $color-divider;
  border-radius: 6rpx;
  overflow: hidden;
}

.review-progress-fill {
  height: 100%;
  background: $color-primary-gradient;
  border-radius: 6rpx;
  transition: width 0.4s ease;
  min-width: 0;
}

.review-progress-text {
  font-size: $text-sm;
  color: $color-muted;
  font-family: $font-mono;
  flex-shrink: 0;
}

.review-progress-hint {
  display: block;
  font-size: $text-xs;
  color: $color-muted;
  margin-bottom: $space-content;
  padding-bottom: $space-content;
  border-bottom: 1rpx solid $color-divider;
}

.review-item {
  padding: 24rpx 0;
  border-bottom: 1rpx solid $color-divider;

  &:last-child {
    border-bottom: none;
    padding-bottom: 0;
  }
}

.review-item-header {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.review-item-avatar {
  width: 48rpx;
  height: 48rpx;
  border-radius: $radius-full;
  flex-shrink: 0;

  &--default {
    background: $color-bg;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &-emoji {
    font-size: 28rpx;
  }
}

.review-item-name {
  font-size: $text-sm;
  font-weight: $weight-medium;
  color: $color-body;
  flex: 1;
}

.review-item-time {
  font-size: $text-xs;
  color: $color-muted;
  flex-shrink: 0;
}

.review-item-scores {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-top: 16rpx;
}

.review-dim-item {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.review-dim-label {
  font-size: $text-xs;
  color: $color-muted;
}

.review-item-comment {
  display: block;
  margin-top: 16rpx;
  font-size: $text-sm;
  color: $color-body;
  font-style: italic;
  line-height: 1.6;
}
</style>

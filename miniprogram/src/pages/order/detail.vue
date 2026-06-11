<template>
  <view class="detail-page">
    <!-- 加载中 -->
    <view v-if="loading" class="state-center">
      <text class="state-text">加载中...</text>
    </view>

    <!-- 加载失败 -->
    <view v-else-if="errorMsg" class="state-center">
      <text class="error-icon">😕</text>
      <text class="error-text">{{ errorMsg }}</text>
      <button class="retry-btn" @click="loadOrder">重新加载</button>
    </view>

    <!-- 订单内容 -->
    <template v-else-if="order">
      <!-- 状态时间线 -->
      <OrderTimeline :order="order" :reviews="reviews" />

      <!-- 商品 / 交易对象 / 订单信息 -->
      <OrderSummaryCard :order="order" :is-buyer="isBuyer" />

      <!-- 评价信息 -->
      <ReviewInfo
        v-if="order.status === STATUS.COMPLETED"
        :reviews="reviews"
        :review-completion="reviewCompletion"
        :my-review="myReview"
      />

      <!-- 操作按钮区 -->
      <view v-if="showActions" class="actions-bar">
        <button v-if="canCancel" class="action-btn action-btn--secondary" @click="doCancel">
          取消订单
        </button>
        <button v-if="canMarkMet" class="action-btn action-btn--primary" @click="doMarkMet">
          标记面交
        </button>
        <button v-if="canConfirm" class="action-btn action-btn--primary" @click="doConfirm">
          确认收货
        </button>
        <button v-if="canReview" class="action-btn action-btn--primary" @click="showReviewModal = true">
          去评价
        </button>
        <button v-if="canReport" class="action-btn action-btn--report" @click="doReport">
          举报
        </button>
      </view>

      <!-- 评价弹窗 -->
      <ReviewModal
        :visible="showReviewModal"
        :order="order"
        :partner-name="partnerName"
        :is-buyer="isBuyer"
        :my-id="myId"
        :user-nickname="userStore.user?.nickname || ''"
        :user-avatar="userStore.user?.avatar || ''"
        @close="showReviewModal = false"
        @submitted="onReviewSubmitted"
      />
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
import { getReviewsByOrder } from '@/api/review';
import { useUserStore } from '@/store/user';
import OrderTimeline from './components/OrderTimeline.vue';
import OrderSummaryCard from './components/OrderSummaryCard.vue';
import ReviewInfo from './components/ReviewInfo.vue';
import ReviewModal from './components/ReviewModal.vue';
import { useOrderActions } from './composables/useOrderActions.js';

// ============================================================
// 订单状态常量
// ============================================================
const STATUS = {
  PENDING: 'pending',
  MET: 'met',
  COMPLETED: 'completed',
  CANCELLED: 'cancelled',
};

// ============================================================
// 数据状态
// ============================================================
const order = ref(null);
const loading = ref(true);
const errorMsg = ref('');
const showReviewModal = ref(false);
const reviews = ref([]);

const userStore = useUserStore();

// ============================================================
// 角色判断
// ============================================================
const myId = computed(() => userStore.user?.id);
const isBuyer = computed(() => order.value?.buyer_id === myId.value);
const isSeller = computed(() => order.value?.seller_id === myId.value);

// ============================================================
// 交易对方信息（供 ReviewModal 使用）
// ============================================================
const partnerName = computed(() => {
  if (!order.value) return '';
  if (isBuyer.value) return order.value.seller_nickname || '卖家';
  return order.value.buyer_nickname || '买家';
});

// ============================================================
// 操作按钮显示逻辑
// ============================================================
const showActions = computed(() => {
  return order.value?.status !== STATUS.CANCELLED;
});

const canCancel = computed(() => {
  if (!order.value) return false;
  const status = order.value.status;
  if (status === STATUS.PENDING) return isBuyer.value || isSeller.value;
  if (status === STATUS.MET) return isBuyer.value;
  return false;
});

const canMarkMet = computed(() => {
  return order.value?.status === STATUS.PENDING && (isBuyer.value || isSeller.value);
});

const canConfirm = computed(() => {
  return order.value?.status === STATUS.MET && isBuyer.value;
});

/** 当前用户对该订单的评价（若有） */
const myReview = computed(() => {
  return reviews.value.find((r) => r.reviewer_id === myId.value) || null;
});

const canReport = computed(() => {
  return order.value?.status === STATUS.COMPLETED;
});

const canReview = computed(() => {
  return order.value?.status === STATUS.COMPLETED && !myReview.value;
});

/** 双方互评完成度（供模板和 OrderTimeline 使用） */
const reviewCompletion = computed(() => {
  const total = reviews.value.length;
  if (total === 0) return { done: 0, total: 2, active: false, label: '待评价' };
  if (total === 1) return { done: 1, total: 2, active: false, label: '已评价 (1/2)' };
  return { done: 2, total: 2, active: true, label: '已完成互评' };
});

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
// 评价回调：子组件提交成功后，追加本地评价并静默刷新
// ============================================================
function onReviewSubmitted(localReview) {
  reviews.value.push(localReview);
  loadOrder();
}

// ============================================================
// 操作处理（委托给 composable）
// ============================================================
const { handleMarkMet, handleConfirm, handleCancel, goReport } = useOrderActions();

/** 模板绑定的 wrapper — 注入运行时参数 */
function doMarkMet() {
  handleMarkMet(order.value.id, markAsMet, loadOrder);
}
function doConfirm() {
  handleConfirm(order.value.id, confirmOrder, loadOrder);
}
function doCancel() {
  handleCancel(order.value.id, cancelOrder, loadOrder);
}
function doReport() {
  goReport(order.value, isBuyer.value);
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
</style>

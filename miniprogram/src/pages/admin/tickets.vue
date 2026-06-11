<template>
  <view class="tickets-page">
    <!-- ============================================================ -->
    <!-- 权限拦截 — 非 cs/admin 不渲染内容 -->
    <!-- ============================================================ -->
    <view v-if="userRole !== 'cs' && userRole !== 'admin'" class="empty-state">
      <text class="empty-emoji">🔒</text>
      <text class="empty-title">仅客服和管理员可访问</text>
    </view>

    <template v-else>
    <!-- ============================================================ -->
    <!-- 状态 Tab -->
    <!-- ============================================================ -->
    <view class="tabs-bar">
      <scroll-view class="tabs-scroll" scroll-x :show-scrollbar="false">
        <view
          v-for="tab in statusTabs"
          :key="tab.value"
          :class="['tab-item', activeStatus === tab.value ? 'tab-item--active' : '']"
          @click="onSwitchTab(tab.value)"
        >
          <text class="tab-text">{{ tab.label }}</text>
        </view>
      </scroll-view>
    </view>

    <!-- ============================================================ -->
    <!-- 类型筛选下拉 -->
    <!-- ============================================================ -->
    <view class="filter-bar">
      <view
        v-for="t in typeFilters"
        :key="t.value"
        :class="['filter-chip', activeType === t.value ? 'filter-chip--active' : '']"
        @click="onSwitchType(t.value)"
      >
        <text class="filter-chip-text">{{ t.label }}</text>
      </view>
    </view>

    <!-- ============================================================ -->
    <!-- 工单列表 -->
    <!-- ============================================================ -->
    <scroll-view
      v-if="tickets.length > 0"
      class="ticket-scroll"
      scroll-y
      refresher-enabled
      :refresher-triggered="refreshing"
      :lower-threshold="100"
      @refresherrefresh="onRefresh"
      @scrolltolower="loadMore"
    >
      <view
        v-for="ticket in tickets"
        :key="ticket.id"
        class="ticket-card"
        @click="goDetail(ticket.id)"
      >
        <!-- 工单头部 -->
        <view class="ticket-header">
          <text class="ticket-id">#{{ ticket.id }}</text>
          <text class="ticket-type">{{ ticket.type }}</text>
          <view :class="['ticket-status', 'status-' + ticket.status]">
            <text class="ticket-status-text">{{ statusLabel(ticket.status) }}</text>
          </view>
        </view>

        <!-- 举报双方 -->
        <view class="ticket-users">
          <text class="ticket-user-label">
            {{ ticket.reporter_nickname || '用户' + ticket.reporter_id }}
          </text>
          <text class="ticket-arrow">→</text>
          <text class="ticket-user-label">
            {{ ticket.reported_nickname || '用户' + ticket.reported_user_id }}
          </text>
        </view>

        <!-- 关联订单 -->
        <view v-if="ticket.order_id" class="ticket-meta">
          <text class="ticket-meta-text">关联订单：#{{ ticket.order_id }}</text>
        </view>

        <!-- 底部时间 + 操作 -->
        <view class="ticket-footer">
          <text class="ticket-time">{{ formatTime(ticket.created_at) }}</text>
          <view class="ticket-actions" @click.stop>
            <button
              v-if="ticket.status === 'pending'"
              class="action-btn action-btn--primary"
              @click="handleProcess(ticket)"
            >
              受理
            </button>
            <button
              v-if="ticket.status === 'processing'"
              class="action-btn action-btn--warning"
              @click="openResolveModal(ticket)"
            >
              裁决
            </button>
          </view>
        </view>
      </view>

      <!-- 加载更多 -->
      <view v-if="loadingMore" class="loadmore-tip">
        <text class="loadmore-text">加载中...</text>
      </view>
      <view v-else-if="!hasMore" class="loadmore-tip">
        <text class="loadmore-text">没有更多工单了</text>
      </view>
    </scroll-view>

    <!-- ============================================================ -->
    <!-- 空状态 -->
    <!-- ============================================================ -->
    <view v-else-if="!loading" class="empty-state">
      <text class="empty-emoji">📋</text>
      <text class="empty-title">暂无工单</text>
      <text class="empty-desc">当前筛选条件下没有工单</text>
    </view>

    <!-- 加载中 -->
    <view v-if="loading" class="loading-state">
      <text class="loading-text">加载中...</text>
    </view>

    <!-- ============================================================ -->
    <!-- 裁决弹窗 -->
    <!-- ============================================================ -->
    <view v-if="resolveModal.visible" class="modal-mask" @click="closeResolveModal">
      <view class="modal-card" @click.stop>
        <text class="modal-title">裁决工单 #{{ resolveModal.ticket?.id }}</text>

        <view class="modal-field">
          <text class="modal-label">处理结论（必填）</text>
          <textarea
            v-model="resolveModal.resolution"
            class="modal-textarea"
            placeholder="请描述处理结果..."
            :maxlength="500"
          />
        </view>

        <view class="modal-field">
          <text class="modal-label">扣减信誉分（选填，0-100）</text>
          <input
            v-model="resolveModal.deductCredit"
            class="modal-input"
            type="number"
            placeholder="0"
          />
        </view>

        <view class="modal-buttons">
          <button class="modal-btn modal-btn--cancel" @click="closeResolveModal">
            取消
          </button>
          <button
            class="modal-btn modal-btn--confirm"
            :disabled="!resolveModal.resolution || resolveModal.submitting"
            :loading="resolveModal.submitting"
            @click="handleResolve"
          >
            确认裁决
          </button>
        </view>
      </view>
    </view>
    </template>
  </view>
</template>

<script setup>
/**
 * 工单处理页 — 客服/管理员视角的举报工单管理
 *
 * 功能：
 *   - 状态 Tab 筛选：全部 / 待处理 / 处理中 / 已处理
 *   - 类型 Chip 筛选：全部 / 描述不符 / 辱骂骚扰 / 疑似骗子 / 其他
 *   - 受理工单（pending → processing）
 *   - 裁决工单（processing → resolved，含信誉分扣减）
 *   - 点击卡片跳转举报详情页
 *   - 权限校验：仅 cs / admin 角色可访问
 */
import { ref, reactive, computed } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getTicketList, processTicket, resolveTicket } from '@/api/admin';
import { useUserStore } from '@/store/user';

// ============================================================
// 权限校验（computed 确保 Pinia 恢复后响应式更新）
// 注意：不做模块级同步判断——Pinia 在 <script setup> 执行时尚未从本地存储恢复，
// 同步判断 userRole.value 永远是默认值 'user'，会导致 cs/admin 用户被误踢。
// 权限守卫完全由模板 v-if + onShow 协同完成。
// ============================================================
const userStore = useUserStore();
const userRole = computed(() => userStore.user?.role || 'user');

// ============================================================
// 筛选配置
// ============================================================
const statusTabs = [
  { label: '全部', value: '' },
  { label: '待处理', value: 'pending' },
  { label: '处理中', value: 'processing' },
  { label: '已处理', value: 'resolved' },
];

const typeFilters = [
  { label: '全部类型', value: '' },
  { label: '描述不符', value: '描述不符' },
  { label: '辱骂骚扰', value: '辱骂骚扰' },
  { label: '疑似骗子', value: '疑似骗子' },
  { label: '其他', value: '其他' },
];

function statusLabel(status) {
  const map = { pending: '待处理', processing: '处理中', resolved: '已处理' };
  return map[status] || status;
}

// ============================================================
// 数据状态
// ============================================================
const activeStatus = ref('');
const activeType = ref('');
const tickets = ref([]);
const loading = ref(true);
const refreshing = ref(false);
const loadingMore = ref(false);
const page = ref(1);
const pageSize = 20;
const hasMore = ref(true);

// ============================================================
// 裁决弹窗状态
// ============================================================
const resolveModal = reactive({
  visible: false,
  ticket: null,
  resolution: '',
  deductCredit: '',
  submitting: false,
});

// ============================================================
// 数据加载
// ============================================================
async function fetchTickets(reset = false) {
  const targetPage = reset ? 1 : page.value + 1;

  if (reset) {
    hasMore.value = true;
  }

  try {
    const result = await getTicketList({
      status: activeStatus.value || undefined,
      type: activeType.value || undefined,
      page: targetPage,
      pageSize,
    });

    if (reset) {
      tickets.value = result.list || [];
    } else {
      tickets.value.push(...(result.list || []));
    }

    page.value = targetPage;
    hasMore.value = tickets.value.length < result.total;
  } catch (err) {
    uni.showToast({ title: err.message || '加载失败', icon: 'none' });
  }
}

// ============================================================
// Tab / 类型切换
// ============================================================
function onSwitchTab(status) {
  if (activeStatus.value === status) return;
  activeStatus.value = status;
  loading.value = true;
  fetchTickets(true).finally(() => {
    loading.value = false;
  });
}

function onSwitchType(type) {
  if (activeType.value === type) return;
  activeType.value = type;
  loading.value = true;
  fetchTickets(true).finally(() => {
    loading.value = false;
  });
}

// ============================================================
// 下拉刷新 / 加载更多
// ============================================================
async function onRefresh() {
  refreshing.value = true;
  await fetchTickets(true);
  refreshing.value = false;
}

async function loadMore() {
  if (loadingMore.value || !hasMore.value) return;
  loadingMore.value = true;
  await fetchTickets(false);
  loadingMore.value = false;
}

// ============================================================
// 操作：受理工单
// ============================================================
function handleProcess(ticket) {
  uni.showModal({
    title: '确认受理',
    content: `确定受理工单 #${ticket.id}？受理后将进入处理中状态。`,
    success: async (res) => {
      if (!res.confirm) return;
      try {
        await processTicket(ticket.id);
        uni.showToast({ title: '已受理', icon: 'success' });
        loading.value = true;
        await fetchTickets(true);
        loading.value = false;
      } catch (err) {
        uni.showToast({ title: err.message || '受理失败', icon: 'none' });
      }
    },
  });
}

// ============================================================
// 操作：裁决工单
// ============================================================
function openResolveModal(ticket) {
  resolveModal.ticket = ticket;
  resolveModal.resolution = '';
  resolveModal.deductCredit = '';
  resolveModal.submitting = false;
  resolveModal.visible = true;
}

function closeResolveModal() {
  if (resolveModal.submitting) return; // 提交中禁止关闭
  resolveModal.visible = false;
  resolveModal.ticket = null;
}

async function handleResolve() {
  if (!resolveModal.resolution || resolveModal.submitting) return;

  const deductCredit = parseInt(resolveModal.deductCredit, 10) || 0;
  if (deductCredit < 0 || deductCredit > 100) {
    uni.showToast({ title: '扣分值需在 0-100 之间', icon: 'none' });
    return;
  }

  resolveModal.submitting = true;
  try {
    await resolveTicket(resolveModal.ticket.id, {
      resolution: resolveModal.resolution,
      deduct_credit: deductCredit,
    });
    uni.showToast({ title: '裁决完成', icon: 'success' });
    resolveModal.submitting = false;
    closeResolveModal();
    loading.value = true;
    await fetchTickets(true);
    loading.value = false;
  } catch (err) {
    uni.showToast({ title: err.message || '裁决失败', icon: 'none' });
  } finally {
    resolveModal.submitting = false;
  }
}

// ============================================================
// 跳转详情
// ============================================================
function goDetail(ticketId) {
  uni.navigateTo({ url: `/pages/report/detail?id=${ticketId}` });
}

// ============================================================
// 格式化时间
// ============================================================
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

// ============================================================
// 生命周期
// ============================================================
onShow(() => {
  if (userRole.value !== 'cs' && userRole.value !== 'admin') return;
  loading.value = true;
  fetchTickets(true).finally(() => {
    loading.value = false;
  });
});
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.tickets-page {
  min-height: 100vh;
  background: $color-bg;
  display: flex;
  flex-direction: column;
}

// ── 状态 Tab 栏 ──
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

// ── 类型筛选条 ──
.filter-bar {
  display: flex;
  gap: $space-content;
  padding: $space-content $space-page;
  background: $color-surface;
  border-top: 1rpx solid $color-divider;
  overflow-x: auto;
  white-space: nowrap;
}

.filter-chip {
  padding: 8rpx 20rpx;
  border-radius: $radius-full;
  background: $color-bg;
  border: 1rpx solid $color-divider;
  flex-shrink: 0;

  &--active {
    background: $color-primary-light;
    border-color: $color-primary;

    .filter-chip-text {
      color: $color-primary;
    }
  }
}

.filter-chip-text {
  font-size: $text-xs;
  color: $color-body;
}

// ── 工单列表 ──
.ticket-scroll {
  flex: 1;
  padding: $space-card 0;
}

.ticket-card {
  background: $color-surface;
  margin: 0 $space-page $space-card;
  border-radius: $radius-card;
  padding: $space-card;
  box-shadow: $shadow-card;
}

.ticket-header {
  display: flex;
  align-items: center;
  gap: $space-content;
}

.ticket-id {
  font-size: $text-sm;
  color: $color-muted;
  font-family: $font-mono;
}

.ticket-type {
  font-size: $text-xs;
  background: $color-bg;
  color: $color-body;
  padding: 2rpx 12rpx;
  border-radius: $radius-full;
  flex: 1;
}

.ticket-status {
  padding: 4rpx 16rpx;
  border-radius: $radius-full;

  &.status-pending {
    background: #E6F7FF;
    .ticket-status-text { color: #1890FF; }
  }

  &.status-processing {
    background: #FFF7E6;
    .ticket-status-text { color: #FA8C16; }
  }

  &.status-resolved {
    background: #F6FFED;
    .ticket-status-text { color: #52C41A; }
  }
}

.ticket-status-text {
  font-size: $text-xs;
  font-weight: $weight-medium;
}

.ticket-users {
  display: flex;
  align-items: center;
  gap: $space-content;
  margin-top: $space-content;
}

.ticket-user-label {
  font-size: $text-sm;
  color: $color-body;
}

.ticket-arrow {
  font-size: $text-xs;
  color: $color-muted;
}

.ticket-meta {
  margin-top: 8rpx;
}

.ticket-meta-text {
  font-size: $text-xs;
  color: $color-muted;
}

.ticket-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: $space-content;
  padding-top: $space-content;
  border-top: 1rpx solid $color-divider;
}

.ticket-time {
  font-size: $text-xs;
  color: $color-muted;
}

.ticket-actions {
  display: flex;
  gap: $space-content;
}

.action-btn {
  padding: 8rpx 24rpx;
  border-radius: $radius-full;
  font-size: $text-xs;
  font-weight: $weight-medium;
  border: none;
  line-height: 1.5;

  &--primary {
    background: $color-primary-light;
    color: $color-primary;
  }

  &--warning {
    background: #FFF7E6;
    color: #FA8C16;
  }
}

// ── 空状态 / 加载 ──
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

.loadmore-tip {
  text-align: center;
  padding: $space-card 0;
}

.loadmore-text {
  font-size: $text-sm;
  color: $color-muted;
}

// ── 裁决弹窗 ──
.modal-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-card {
  width: 600rpx;
  background: $color-surface;
  border-radius: $radius-modal;
  padding: 48rpx $space-card $space-card;
}

.modal-title {
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: $color-title;
  display: block;
  margin-bottom: $space-card;
}

.modal-field {
  margin-bottom: $space-card;
}

.modal-label {
  font-size: $text-sm;
  color: $color-body;
  display: block;
  margin-bottom: 8rpx;
}

.modal-textarea {
  width: 100%;
  min-height: 160rpx;
  font-size: $text-base;
  color: $color-title;
  line-height: $line-height;
  padding: $space-content;
  background: $color-bg;
  border-radius: $radius-card;
  box-sizing: border-box;
}

.modal-input {
  width: 100%;
  height: 72rpx;
  font-size: $text-base;
  color: $color-title;
  padding: 0 $space-content;
  background: $color-bg;
  border-radius: $radius-card;
  box-sizing: border-box;
}

.modal-buttons {
  display: flex;
  gap: $space-card;
  margin-top: $space-card;
}

.modal-btn {
  flex: 1;
  height: $btn-height-md;
  line-height: $btn-height-md;
  border-radius: $radius-card;
  font-size: $text-base;
  font-weight: $weight-medium;
  border: none;
  text-align: center;

  &--cancel {
    background: $color-bg;
    color: $color-body;
  }

  &--confirm {
    background: $color-primary-gradient;
    color: #fff;

    &[disabled] {
      opacity: $opacity-disabled;
    }
  }
}
</style>

<template>
  <view class="logs-page">
    <!-- 权限守卫 -->
    <view v-if="!userStore.isAdmin" class="empty-state">
      <EmptyState icon="🔒" title="仅管理员可访问" />
    </view>

    <template v-else>
      <!-- 操作类型筛选 -->
      <view class="filter-bar">
        <view
          v-for="a in actionFilters"
          :key="a.value"
          :class="['filter-chip', activeAction === a.value ? 'filter-chip--active' : '']"
          @click="onSwitchAction(a.value)"
        >
          <text class="filter-chip-icon">{{ a.icon }}</text>
          <text class="filter-chip-text">{{ a.label }}</text>
        </view>
      </view>

      <!-- 时间范围筛选 -->
      <view class="date-bar">
        <text class="date-bar-label">时间：</text>
        <view
          v-for="t in timeRanges"
          :key="t.value"
          :class="['filter-chip', activeTimeRange === t.value ? 'filter-chip--active' : '']"
          @click="onSwitchTimeRange(t.value)"
        >
          <text class="filter-chip-text">{{ t.label }}</text>
        </view>
        <view
          v-if="activeTimeRange || activeAction"
          class="filter-chip filter-chip--reset"
          @click="onReset"
        >
          <text class="filter-chip-reset-text">重置</text>
        </view>
      </view>

      <!-- 日志列表 -->
      <scroll-view
        v-if="logs.length > 0"
        class="logs-scroll"
        scroll-y
        :lower-threshold="100"
        @scrolltolower="loadMore"
      >
        <view v-for="log in logs" :key="log.id" class="log-card">
          <view class="log-header">
            <text class="log-icon">{{ actionMap(log.action).icon }}</text>
            <text class="log-action">{{ actionMap(log.action).label }}</text>
          </view>

          <view class="log-body">
            <view class="log-row">
              <text class="log-label">管理员</text>
              <text class="log-value">{{ maskPhone(log.admin_phone) }}</text>
            </view>
            <view class="log-row">
              <text class="log-label">对象</text>
              <text class="log-value">
                {{ targetTypeLabel(log.target_type) }} #{{ log.target_id }}
              </text>
            </view>
            <view v-if="log.reason" class="log-row">
              <text class="log-label">原因</text>
              <text class="log-value log-reason">{{ log.reason }}</text>
            </view>
          </view>

          <text class="log-time">{{ formatDateTime(log.created_at) }}</text>
        </view>
      </scroll-view>

      <!-- 空状态 -->
      <EmptyState
        v-if="!loading && logs.length === 0"
        icon="📋"
        title="暂无操作记录"
        :description="activeAction || activeTimeRange ? '试试调整筛选条件' : ''"
        :action-text="activeAction || activeTimeRange ? '清除筛选' : ''"
        @action="onReset"
      />

      <!-- 加载提示 -->
      <view v-if="loading" class="loading-tip">
        <text class="loading-tip-text">加载中...</text>
      </view>
      <view v-else-if="!hasMore && logs.length > 0" class="loading-tip">
        <text class="loading-tip-text">— 没有更多了 —</text>
      </view>
    </template>
  </view>
</template>

<script setup>
/**
 * 审计日志页 — 管理员查看所有管理操作记录。
 *
 * 支持按操作类型和日期范围筛选，分页加载。
 * 权限：仅 admin 角色可访问。
 */
import { ref } from 'vue';
import { onShow, onUnload } from '@dcloudio/uni-app';
import { useUserStore } from '@/store/user';
import { getAdminLogs } from '@/api/admin';
import EmptyState from '@/components/EmptyState.vue';

const userStore = useUserStore();

// ── 操作类型映射 ──────────────────────────────────────────
const ACTION_MAP = {
  ban:              { icon: '🚫', label: '封禁用户' },
  unban:            { icon: '✅', label: '解封用户' },
  off_shelf:        { icon: '📦', label: '下架商品' },
  process_ticket:   { icon: '📋', label: '受理工单' },
  resolve_ticket:   { icon: '⚖️', label: '裁决工单' },
};

const actionFilters = [
  { icon: '📋', label: '全部', value: '' },
  ...Object.entries(ACTION_MAP).map(([value, { icon, label }]) => ({ icon, label, value })),
];

// ── 筛选状态 ──────────────────────────────────────────────
const activeAction = ref('');
const activeTimeRange = ref('');

const timeRanges = [
  { label: '全部', value: '' },
  { label: '今天', value: 'today' },
  { label: '近7天', value: '7days' },
  { label: '近30天', value: '30days' },
  { label: '本月', value: 'thisMonth' },
];

// ── 列表状态 ──────────────────────────────────────────────
const logs = ref([]);
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
  fetchLogs(true);
});

onUnload(() => {
  if (guardTimer) {
    clearTimeout(guardTimer);
    guardTimer = null;
  }
});

// ── 数据加载 ──────────────────────────────────────────────
async function fetchLogs(reset = false) {
  if (loading.value) return;
  const targetPage = reset ? 1 : page.value + 1;
  loading.value = true;

  try {
    const result = await getAdminLogs({
      action: activeAction.value || undefined,
      start_date: computeStartDate(activeTimeRange.value),
      end_date: computeEndDate(activeTimeRange.value),
      page: targetPage,
      pageSize: PAGE_SIZE,
    });

    if (reset) {
      logs.value = result.list || [];
    } else {
      logs.value.push(...(result.list || []));
    }
    total.value = result.total;
    hasMore.value = logs.value.length < result.total;
    page.value = targetPage;
  } catch (err) {
    uni.showToast({ title: err.message || '加载失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}

// ── 筛选操作 ──────────────────────────────────────────────
function onSwitchAction(val) {
  if (activeAction.value === val) return;
  activeAction.value = val;
  fetchLogs(true);
}

function onSwitchTimeRange(val) {
  if (activeTimeRange.value === val) return;
  activeTimeRange.value = val;
  fetchLogs(true);
}

function onReset() {
  activeAction.value = '';
  activeTimeRange.value = '';
  fetchLogs(true);
}

// ── 时间范围计算 ──────────────────────────────────────────
/** 计算筛选开始日期（YYYY-MM-DD），空字符串返回 undefined */
function computeStartDate(range) {
  if (!range) return undefined;
  const now = new Date();
  switch (range) {
    case 'today':
      return formatDateStr(now);
    case '7days': {
      const d = new Date(now);
      d.setDate(d.getDate() - 6);
      return formatDateStr(d);
    }
    case '30days': {
      const d = new Date(now);
      d.setDate(d.getDate() - 29);
      return formatDateStr(d);
    }
    case 'thisMonth':
      return formatDateStr(new Date(now.getFullYear(), now.getMonth(), 1));
    default:
      return undefined;
  }
}

/**
 * 计算筛选结束日期（均为今天 23:59:59）
 *
 * 必须带时间分量，否则 MySQL 将 '2026-06-11' 视为 '2026-06-11 00:00:00'，
 * 导致当天 00:00:00 之后的所有记录被排除。
 */
function computeEndDate(range) {
  if (!range) return undefined;
  return formatDateStr(new Date()) + ' 23:59:59';
}

/** Date → YYYY-MM-DD */
function formatDateStr(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function loadMore() {
  if (hasMore.value && !loading.value) {
    fetchLogs(false);
  }
}

// ── 工具函数 ──────────────────────────────────────────────
function actionMap(action) {
  return ACTION_MAP[action] || { icon: '📝', label: action };
}

function targetTypeLabel(type) {
  const map = {
    ticket: '工单',
    user: '用户',
    product: '商品',
  };
  return map[type] || type;
}

function maskPhone(phone) {
  if (!phone) return '';
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2');
}

function formatDateTime(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  const h = String(d.getHours()).padStart(2, '0');
  const min = String(d.getMinutes()).padStart(2, '0');
  return `${y}-${m}-${day} ${h}:${min}`;
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.logs-page {
  min-height: 100vh;
  background: $color-bg;
}

// ── 操作类型筛选 ──────────────────────────────────────────
.filter-bar {
  background: $color-surface;
  padding: 20rpx $space-page 16rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.filter-chip {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 10rpx 24rpx;
  border-radius: $radius-full;
  border: 1rpx solid $color-divider;
  background: $color-surface;
}

.filter-chip--active {
  background: $color-primary-light;
  border-color: $color-primary;
}

.filter-chip-icon {
  font-size: 22rpx;
}

.filter-chip-text {
  font-size: $text-xs;
  color: $color-muted;
}

.filter-chip--active .filter-chip-text {
  color: $color-primary;
}

// ── 时间范围筛选 ──────────────────────────────────────────
.date-bar {
  display: flex;
  align-items: center;
  padding: 0 $space-page 16rpx;
  background: $color-surface;
  gap: 12rpx;
  flex-wrap: wrap;
}

.date-bar-label {
  font-size: $text-xs;
  color: $color-muted;
  flex-shrink: 0;
}

.filter-chip--reset {
  background: $color-divider;
  border-color: transparent;
}

.filter-chip-reset-text {
  font-size: $text-xs;
  color: $color-muted;
}

// ── 日志列表 ──────────────────────────────────────────────
.logs-scroll {
  flex: 1;
}

.log-card {
  padding: 24rpx $space-page;
  background: $color-surface;
  margin-top: 2rpx;
}

.log-header {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 16rpx;
}

.log-icon {
  font-size: 32rpx;
}

.log-action {
  font-size: $text-base;
  font-weight: $weight-bold;
  color: $color-title;
}

.log-body {
  margin-bottom: 12rpx;
}

.log-row {
  display: flex;
  align-items: baseline;
  margin-top: 6rpx;
}

.log-label {
  font-size: $text-xs;
  color: $color-muted;
  width: 88rpx;
  flex-shrink: 0;
}

.log-value {
  font-size: $text-sm;
  color: $color-body;
}

.log-reason {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.log-time {
  font-size: $text-xs;
  color: $color-muted;
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

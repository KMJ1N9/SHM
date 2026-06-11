<template>
  <view class="users-page">
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
          placeholder="搜索手机号/昵称"
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

      <!-- 角色筛选 -->
      <view class="filter-bar">
        <view
          v-for="r in roleFilters"
          :key="r.value"
          :class="['filter-chip', activeRole === r.value ? 'filter-chip--active' : '']"
          @click="onSwitchRole(r.value)"
        >
          <text class="filter-chip-text">{{ r.label }}</text>
        </view>
      </view>

      <!-- 用户列表 -->
      <scroll-view
        v-if="users.length > 0"
        class="users-scroll"
        scroll-y
        :lower-threshold="100"
        @scrolltolower="loadMore"
      >
        <view v-for="user in users" :key="user.id" class="user-card">
          <!-- 头像 -->
          <image
            v-if="user.avatar"
            class="user-card-avatar"
            :src="user.avatar"
            mode="aspectFill"
          />
          <view v-else class="user-card-avatar user-card-avatar--default">
            <text class="user-card-avatar-text">👤</text>
          </view>

          <!-- 信息 -->
          <view class="user-card-info">
            <view class="user-card-name-row">
              <text class="user-card-name">{{ user.nickname || '微信用户' }}</text>
            </view>
            <text class="user-card-phone">{{ maskPhone(user.phone) }}</text>
            <view class="user-card-meta">
              <text class="user-card-credit" :style="{ color: creditColor(user.credit_score) }">
                信誉 {{ user.credit_score ?? 100 }}
              </text>
            </view>
          </view>

          <!-- 标签 -->
          <view class="user-card-tags">
            <text :class="['tag', 'tag-role', 'tag-role--' + user.role]">
              {{ roleLabel(user.role) }}
            </text>
            <text :class="['tag', 'tag-status', 'tag-status--' + user.status]">
              {{ statusLabel(user.status) }}
            </text>
          </view>

          <!-- 操作按钮 -->
          <view
            v-if="user.role !== 'admin'"
            class="user-card-action"
            @click.stop="handleToggleBan(user)"
          >
            <text
              :class="['action-text', user.status === 'banned' ? 'action-text--unban' : 'action-text--ban']"
            >
              {{ user.status === 'banned' ? '解封' : '封禁' }}
            </text>
          </view>
        </view>
      </scroll-view>

      <!-- 空状态 -->
      <EmptyState
        v-if="!loading && users.length === 0"
        icon="🔍"
        :title="keyword ? '未找到匹配的用户' : '暂无用户'"
        :description="keyword ? '试试调整搜索条件' : ''"
        :action-text="keyword ? '清除搜索' : ''"
        @action="clearSearch"
      />

      <!-- 加载更多 -->
      <view v-if="loading" class="loading-tip">
        <text class="loading-tip-text">加载中...</text>
      </view>
      <view v-else-if="!hasMore && users.length > 0" class="loading-tip">
        <text class="loading-tip-text">— 没有更多了 —</text>
      </view>
    </template>
  </view>
</template>

<script setup>
/**
 * 用户管理页 — 管理员查看所有用户，支持搜索/筛选/封禁/解封。
 *
 * 权限：仅 admin 角色可访问（onShow 守卫 + 模板层渲染拦截）。
 * 分页模式：固化 A6-001 修复的 targetPage 模式。
 */
import { ref } from 'vue';
import { onShow, onUnload } from '@dcloudio/uni-app';
import { useUserStore } from '@/store/user';
import { getUserList, banUser, unbanUser } from '@/api/admin';
import EmptyState from '@/components/EmptyState.vue';

const userStore = useUserStore();

// ── 筛选状态 ──────────────────────────────────────────────
const keyword = ref('');
const activeStatus = ref('');
const activeRole = ref('');

const statusTabs = [
  { label: '全部', value: '' },
  { label: '正常', value: 'active' },
  { label: '已封禁', value: 'banned' },
];

const roleFilters = [
  { label: '全部角色', value: '' },
  { label: '管理员', value: 'admin' },
  { label: '普通用户', value: 'user' },
  { label: '客服', value: 'cs' },
];

// ── 列表状态 ──────────────────────────────────────────────
const users = ref([]);
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
  fetchUsers(true);
});

onUnload(() => {
  if (guardTimer) {
    clearTimeout(guardTimer);
    guardTimer = null;
  }
});

// ── 数据加载 ──────────────────────────────────────────────
async function fetchUsers(reset = false) {
  if (loading.value) return;
  const targetPage = reset ? 1 : page.value + 1;
  loading.value = true;

  try {
    const result = await getUserList({
      keyword: keyword.value || undefined,
      status: activeStatus.value || undefined,
      role: activeRole.value || undefined,
      page: targetPage,
      pageSize: PAGE_SIZE,
    });

    if (reset) {
      users.value = result.list || [];
    } else {
      users.value.push(...(result.list || []));
    }
    total.value = result.total;
    hasMore.value = users.value.length < result.total;
    page.value = targetPage;
  } catch (err) {
    uni.showToast({ title: err.message || '加载失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}

// ── 筛选操作 ──────────────────────────────────────────────
function onSearch() {
  fetchUsers(true);
}

function onSwitchStatus(val) {
  if (activeStatus.value === val) return;
  activeStatus.value = val;
  fetchUsers(true);
}

function onSwitchRole(val) {
  if (activeRole.value === val) return;
  activeRole.value = val;
  fetchUsers(true);
}

function clearSearch() {
  keyword.value = '';
  activeStatus.value = '';
  activeRole.value = '';
  fetchUsers(true);
}

function loadMore() {
  if (hasMore.value && !loading.value) {
    fetchUsers(false);
  }
}

// ── 封禁/解封 ──────────────────────────────────────────────
async function handleToggleBan(user) {
  const isBanned = user.status === 'banned';

  const { confirm } = await uni.showModal({
    title: isBanned ? '解封用户' : '封禁用户',
    content: isBanned
      ? `确定解封用户「${user.nickname || '微信用户'}」？`
      : `确定封禁用户「${user.nickname || '微信用户'}」？封禁后该用户将无法登录。`,
    confirmText: isBanned ? '确定解封' : '确定封禁',
    confirmColor: isBanned ? '#4A90D9' : '#FF4D4F',
  });
  if (!confirm) return;

  try {
    if (isBanned) {
      await unbanUser(user.id);
      user.status = 'active';
      // 如果在"已封禁" Tab 中，解封后从列表移除
      if (activeStatus.value === 'banned') {
        users.value = users.value.filter(u => u.id !== user.id);
        total.value = Math.max(0, total.value - 1);
      }
    } else {
      await banUser(user.id);
      user.status = 'banned';
      // 如果在"正常" Tab 中，封禁后从列表移除
      if (activeStatus.value === 'active') {
        users.value = users.value.filter(u => u.id !== user.id);
        total.value = Math.max(0, total.value - 1);
      }
    }
    uni.showToast({ title: isBanned ? '已解封' : '已封禁', icon: 'success' });
  } catch (err) {
    uni.showToast({ title: err.message || '操作失败', icon: 'error' });
  }
}

// ── 工具函数 ──────────────────────────────────────────────
function maskPhone(phone) {
  if (!phone) return '';
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2');
}

function creditColor(score) {
  const s = score ?? 100;
  if (s >= 60) return '#52C41A';
  if (s >= 30) return '#FAAD14';
  return '#FF4D4F';
}

function roleLabel(role) {
  const map = { admin: '管理员', cs: '客服', user: '用户' };
  return map[role] || role;
}

function statusLabel(status) {
  const map = { active: '正常', banned: '已封禁' };
  return map[status] || status;
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.users-page {
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

// ── 角色筛选 ──────────────────────────────────────────────
.filter-bar {
  background: $color-surface;
  padding: 0 $space-page 16rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.filter-chip {
  padding: 10rpx 24rpx;
  border-radius: $radius-full;
  border: 1rpx solid $color-divider;
  background: $color-surface;
}

.filter-chip--active {
  background: $color-primary-light;
  border-color: $color-primary;
}

.filter-chip-text {
  font-size: $text-xs;
  color: $color-muted;
}

.filter-chip--active .filter-chip-text {
  color: $color-primary;
}

// ── 用户列表 ──────────────────────────────────────────────
.users-scroll {
  flex: 1;
}

.user-card {
  display: flex;
  align-items: center;
  padding: 24rpx $space-page;
  background: $color-surface;
  margin-top: 2rpx;
  position: relative;
}

.user-card-avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  flex-shrink: 0;
  background: $color-divider;
}

.user-card-avatar--default {
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-card-avatar-text {
  font-size: 36rpx;
}

.user-card-info {
  flex: 1;
  margin-left: 20rpx;
  min-width: 0;
}

.user-card-name-row {
  display: flex;
  align-items: center;
}

.user-card-name {
  font-size: $text-base;
  font-weight: $weight-bold;
  color: $color-title;
}

.user-card-phone {
  font-size: $text-sm;
  color: $color-muted;
  margin-top: 4rpx;
}

.user-card-meta {
  margin-top: 4rpx;
}

.user-card-credit {
  font-size: $text-xs;
  font-weight: $weight-medium;
}

.user-card-tags {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8rpx;
  margin-right: 12rpx;
}

.user-card-action {
  position: absolute;
  right: $space-page;
  bottom: 24rpx;
}

.action-text {
  font-size: $text-sm;
  font-weight: $weight-medium;
  padding: 8rpx 20rpx;
  border-radius: $radius-card;
}

.action-text--ban {
  color: $color-error;
  background: rgba(255, 77, 79, 0.08);
}

.action-text--unban {
  color: $color-success;
  background: rgba(82, 196, 26, 0.08);
}

// ── 标签 ──────────────────────────────────────────────
.tag {
  font-size: 20rpx;
  padding: 4rpx 16rpx;
  border-radius: $radius-card;
  white-space: nowrap;
}

.tag-role--admin {
  background: rgba(255, 77, 79, 0.08);
  color: $color-error;
}

.tag-role--cs {
  background: rgba(74, 144, 217, 0.08);
  color: $color-primary;
}

.tag-role--user {
  background: $color-divider;
  color: $color-muted;
}

.tag-status--active {
  background: rgba(82, 196, 26, 0.08);
  color: $color-success;
}

.tag-status--banned {
  background: rgba(255, 77, 79, 0.08);
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

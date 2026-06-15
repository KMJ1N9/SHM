<template>
  <view class="me-page">
    <!-- 用户信息 -->
    <view class="user-section">
      <view class="user-card">
        <SafeImage
          v-if="avatarUrl"
          class="user-avatar"
          :src="avatarUrl"
          mode="aspectFill"
        />
        <view v-else class="user-avatar user-avatar--default">
          <text class="user-avatar-emoji">👤</text>
        </view>
        <view class="user-info">
          <text class="user-name">{{ user?.nickname || '微信用户' }}</text>
          <text v-if="user?.class_name" class="user-class">{{ user.class_name }}</text>
        </view>
        <view class="user-credit">
          <text class="credit-score">{{ user?.credit_score || 100 }}</text>
          <text class="credit-label">信誉分</text>
        </view>
      </view>
    </view>

    <!-- 功能菜单 -->
    <view class="menu-section">
      <view class="menu-item" @click="goOrders">
        <text class="menu-icon">📋</text>
        <text class="menu-label">我的订单</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goMyProducts">
        <text class="menu-icon">📦</text>
        <text class="menu-label">我的发布</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goReviews">
        <text class="menu-icon">⭐</text>
        <text class="menu-label">我的评价</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goCredit">
        <text class="menu-icon">🛡️</text>
        <text class="menu-label">信誉分明细</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goEditProfile">
        <text class="menu-icon">✏️</text>
        <text class="menu-label">编辑资料</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goSettings">
        <text class="menu-icon">⚙️</text>
        <text class="menu-label">系统设置</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goReports">
        <text class="menu-icon">📝</text>
        <text class="menu-label">我的举报</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goContactCS">
        <text class="menu-icon">🎧</text>
        <text class="menu-label">联系客服</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="goNotifications">
        <text class="menu-icon">🔔</text>
        <text class="menu-label">通知中心</text>
        <view v-if="unreadNum > 0" class="menu-badge">
          <text class="menu-badge-text">{{ unreadNum > 99 ? '99+' : unreadNum }}</text>
        </view>
        <text class="menu-arrow">›</text>
      </view>
    </view>

    <!-- 管理功能（cs + admin 可见工单管理，admin 可见全部） -->
    <view v-if="userStore.isAdmin || userStore.isCS" class="menu-section">
      <view class="menu-section-header">
        <text class="menu-section-title">管理功能</text>
      </view>
      <view class="menu-item" @click="goTickets">
        <text class="menu-icon">🎫</text>
        <text class="menu-label">工单管理</text>
        <text class="menu-arrow">›</text>
      </view>
      <template v-if="userStore.isAdmin">
        <view class="menu-item" @click="goDashboard">
          <text class="menu-icon">📊</text>
          <text class="menu-label">数据看板</text>
          <text class="menu-arrow">›</text>
        </view>
        <view class="menu-item" @click="goUsers">
          <text class="menu-icon">👥</text>
          <text class="menu-label">用户管理</text>
          <text class="menu-arrow">›</text>
        </view>
        <view class="menu-item" @click="goProducts">
          <text class="menu-icon">🛒</text>
          <text class="menu-label">商品管理</text>
          <text class="menu-arrow">›</text>
        </view>
        <view class="menu-item" @click="goLogs">
          <text class="menu-icon">📝</text>
          <text class="menu-label">审计日志</text>
          <text class="menu-arrow">›</text>
        </view>
        <view class="menu-item" @click="goSensitive">
          <text class="menu-icon">🛡️</text>
          <text class="menu-label">敏感词库</text>
          <text class="menu-arrow">›</text>
        </view>
      </template>
    </view>

    <!-- 退出登录 -->
    <view class="logout-section">
      <view class="logout-btn" @click="onLogout">
        <text class="logout-btn-text">退出登录</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { useUserStore } from '@/store/user';
import { resolveImageUrl } from '@/api/index';
import SafeImage from '@/components/SafeImage.vue';
import { unreadCount } from '@/api/notification';
import { getCSContact, ensureAccount } from '@/api/im';
import { isIMReady, reInitIM, cachePeerProfile } from '@/utils/im';

const userStore = useUserStore();
const user = computed(() => userStore.user);

/**
 * 用户头像 URL（经 host 解析——适配模拟器/真机/IP 变更）
 *
 * 问题背景：开发环境上传的图片 URL 写死为当前 BASE_URL 的 host，
 * 若设备（真机 vs 模拟器）对 host 的可达性不同，图片可能加载失败。
 * resolveImageUrl 统一替换 localhost→当前 BASE_URL host。
 */
const avatarUrl = computed(() => resolveImageUrl(user.value?.avatar));

// ── 未读通知数 ──────────────────────────────────────────
const unreadNum = ref(0);

async function loadUnreadCount() {
  try {
    const data = await unreadCount();
    unreadNum.value = data.count || 0;
  } catch (err) {
    // 未读 badge 非关键功能，静默降级但需记录日志
    console.warn('[me] 未读通知数加载失败', err.message);
  }
}

/**
 * 每次回到"我的"页面时刷新用户信息 + 未读通知数
 *
 * 为什么要刷新用户信息：edit.vue 保存资料后虽然更新了 Pinia store，
 * 但 WeChat 小程序 Tab 页面可能在 sub-page 期间被缓存，模板不一定
 * 自动重新渲染。onShow 中显式调用 getMeAction() 确保数据新鲜。
 */
onShow(() => {
  loadUnreadCount();
  if (userStore.isLoggedIn) {
    userStore.getMeAction().catch((err) => {
      console.warn('[me] 用户信息刷新失败', err.message);
    });
  }
});

function goOrders() {
  uni.navigateTo({ url: '/pages/order/list' });
}

function goMyProducts() {
  uni.navigateTo({ url: '/pages/product/my' });
}

function goReviews() {
  uni.navigateTo({ url: '/pages/user/reviews' });
}

function goCredit() {
  uni.navigateTo({ url: '/pages/user/credit' });
}

function goEditProfile() {
  uni.navigateTo({ url: '/pages/user/edit' });
}

function goSettings() {
  uni.navigateTo({ url: '/pages/user/settings' });
}

function goReports() {
  uni.navigateTo({ url: '/pages/report/list' });
}

async function goContactCS() {
  // 1. 检查登录
  if (!userStore.isLoggedIn) {
    uni.showToast({ title: '请先登录', icon: 'none' });
    return;
  }

  // 2. 检查 IM 就绪
  if (!isIMReady()) {
    uni.showToast({ title: '消息服务连接中…', icon: 'none', duration: 2000 });
    try {
      await reInitIM();
    } catch (err) {
      console.error('[goContactCS] IM 初始化失败:', err.message);
      uni.showToast({ title: '消息服务连接失败，请稍后重试', icon: 'none' });
      return;
    }
  }

  // 3. 获取客服信息
  let cs;
  try {
    cs = await getCSContact();
  } catch (err) {
    console.error('[goContactCS] 获取客服信息失败:', err.message);
    uni.showToast({ title: '客服暂时不在线，请稍后重试', icon: 'none' });
    return;
  }

  if (!cs || !cs.id) {
    uni.showToast({ title: '暂无客服在线', icon: 'none' });
    return;
  }

  // 4. 确保 IM 账号 + 缓存资料
  const conversationID = `C2C${cs.id}`;
  cachePeerProfile(cs.id, cs.nickname, cs.avatar);
  ensureAccount(cs.id, cs.nickname, cs.avatar).catch(() => {});

  // 5. 跳转聊天页
  uni.navigateTo({
    url: `/pages/chat/detail?conversationId=${conversationID}&nickname=${encodeURIComponent(cs.nickname)}&avatar=${encodeURIComponent(cs.avatar || '')}`,
  });
}

function goNotifications() {
  uni.navigateTo({ url: '/pages/notification/index' });
}

function goTickets() {
  uni.navigateTo({ url: '/pages/admin/tickets' });
}

function goDashboard() {
  uni.navigateTo({ url: '/pages/admin/dashboard' });
}

function goUsers() {
  uni.navigateTo({ url: '/pages/admin/users' });
}

function goProducts() {
  uni.navigateTo({ url: '/pages/admin/products' });
}

function goLogs() {
  uni.navigateTo({ url: '/pages/admin/logs' });
}

function goSensitive() {
  uni.navigateTo({ url: '/pages/admin/sensitive' });
}

function onLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确定要退出登录吗？',
    confirmText: '退出',
    cancelText: '取消',
    success: (res) => {
      if (res.confirm) {
        userStore.logoutAction();
      }
    },
  });
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.me-page {
  min-height: 100vh;
  background: $color-bg;
}

// ── 用户信息 ──────────────────────────────────────────────
.user-section {
  background: $color-primary-gradient;
  padding: 60rpx $space-page 40rpx;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.user-avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  border: 4rpx solid rgba(255, 255, 255, 0.3);
  background: $color-divider;
  flex-shrink: 0;
}

.user-avatar--default {
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-avatar-emoji {
  font-size: 56rpx;
}

.user-info {
  flex: 1;
}

.user-name {
  font-size: $text-xl;
  font-weight: $weight-bold;
  color: #FFFFFF;
  display: block;
}

.user-class {
  font-size: $text-sm;
  color: rgba(255, 255, 255, 0.8);
  margin-top: 4rpx;
  display: block;
}

.user-credit {
  display: flex;
  flex-direction: column;
  align-items: center;
  background: rgba(255, 255, 255, 0.15);
  border-radius: $radius-card;
  padding: 12rpx 24rpx;
  flex-shrink: 0;
}

.credit-score {
  font-size: $text-2xl;
  font-weight: $weight-bold;
  color: #FFFFFF;
  font-family: $font-mono;
}

.credit-label {
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.8);
}

// ── 功能菜单 ──────────────────────────────────────────────
.menu-section {
  background: $color-surface;
  margin-top: 16rpx;
}

.menu-section-header {
  padding: 24rpx $space-page 12rpx;
}

.menu-section-title {
  font-size: $text-xs;
  color: $color-muted;
  font-weight: $weight-medium;
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 28rpx $space-page;
  border-bottom: 1rpx solid $color-divider;

  &:last-child { border-bottom: none; }

  &:active { background: #F5F5F5; }
}

.menu-icon {
  font-size: 36rpx;
  margin-right: 20rpx;
}

.menu-label {
  flex: 1;
  font-size: $text-base;
  color: $color-title;
}

.menu-arrow {
  font-size: 36rpx;
  color: $color-muted;
}

.menu-badge {
  min-width: 36rpx;
  height: 36rpx;
  border-radius: 18rpx;
  background: $color-error;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12rpx;
  padding: 0 8rpx;
}

.menu-badge-text {
  font-size: 20rpx;
  color: #FFFFFF;
  font-weight: $weight-bold;
}

// ── 退出登录 ──────────────────────────────────────────────
.logout-section {
  margin-top: 80rpx;
  padding: 0 $space-page;
}

.logout-btn {
  height: $btn-height-md;
  border-radius: $radius-card;
  border: 1rpx solid $color-divider;
  display: flex;
  align-items: center;
  justify-content: center;

  &:active { background: $color-bg; }
}

.logout-btn-text {
  font-size: $text-base;
  color: $color-muted;
}
</style>

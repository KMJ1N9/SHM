<template>
  <view class="about-page">
    <view class="about-header">
      <text class="about-logo">🛒</text>
      <text class="about-app-name">校园二手交易</text>
      <text class="about-version">v1.0.0</text>
    </view>

    <view class="about-section">
      <text class="about-section-title">关于我们</text>
      <text class="about-text">
        校园二手交易小程序是一个面向广州应用科技学院肇庆校区的 C2C
        二手交易平台。由计算机学院学生独立开发，旨在为校内师生提供安全、便捷的闲置物品交易服务。
      </text>
    </view>

    <view class="about-section">
      <text class="about-section-title">技术栈</text>
      <view class="tech-stack">
        <text class="tech-item">uni-app (Vue 3) + SCSS</text>
        <text class="tech-item">Node.js + Express</text>
        <text class="tech-item">MySQL</text>
        <text class="tech-item">腾讯云 IM + COS</text>
      </view>
    </view>

    <view class="about-section">
      <text class="about-section-title">联系方式</text>
      <text class="about-text">如有问题或建议，请联系平台管理员。</text>
      <view class="contact-admin-btn" @click="goContactAdmin">
        <text class="contact-admin-icon">💬</text>
        <text class="contact-admin-text">联系管理员</text>
      </view>
    </view>
  </view>
</template>

<script setup>
/**
 * 关于我们 — 应用信息静态展示页
 *
 * 入口：设置页 → 关于我们
 */
import { useUserStore } from '@/store/user';
import { getAdminContact, ensureAccount } from '@/api/im';
import { isIMReady, reInitIM, cachePeerProfile } from '@/utils/im';

const userStore = useUserStore();

/**
 * 跳转到管理员聊天页面
 *
 * 流程与「联系客服」一致：
 *   1. 检查登录 → 2. 检查 IM 就绪 → 3. 获取管理员信息 →
 *   4. 缓存资料 + 确保 IM 账号 → 5. 跳转聊天页
 */
async function goContactAdmin() {
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
      console.error('[goContactAdmin] IM 初始化失败:', err.message);
      uni.showToast({ title: '消息服务连接失败，请稍后重试', icon: 'none' });
      return;
    }
  }

  // 3. 获取管理员信息
  let admin;
  try {
    admin = await getAdminContact();
  } catch (err) {
    console.error('[goContactAdmin] 获取管理员信息失败:', err.message);
    uni.showToast({ title: '管理员暂时不在线，请稍后重试', icon: 'none' });
    return;
  }

  if (!admin || !admin.id) {
    uni.showToast({ title: '暂无管理员在线', icon: 'none' });
    return;
  }

  // 4. 确保 IM 账号 + 缓存资料
  const conversationID = `C2C${admin.id}`;
  cachePeerProfile(admin.id, admin.nickname, admin.avatar);
  ensureAccount(admin.id, admin.nickname, admin.avatar).catch(() => {});

  // 5. 跳转聊天页
  uni.navigateTo({
    url: `/pages/chat/detail?conversationId=${conversationID}&nickname=${encodeURIComponent(admin.nickname)}&avatar=${encodeURIComponent(admin.avatar || '')}`,
  });
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.about-page {
  min-height: 100vh;
  background: $color-bg;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
}

// ── 联系管理员按钮 ──
.contact-admin-btn {
  margin-top: 24rpx;
  height: 80rpx;
  background: $color-primary-gradient;
  border-radius: $radius-card;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  box-shadow: $shadow-button;

  &:active {
    opacity: 0.85;
  }
}

.contact-admin-icon {
  font-size: 36rpx;
}

.contact-admin-text {
  font-size: $text-base;
  font-weight: $weight-medium;
  color: #FFFFFF;
}

.about-header {
  background: $color-primary-gradient;
  padding: 80rpx $space-page 60rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.about-logo {
  font-size: 96rpx;
  margin-bottom: $space-content;
}

.about-app-name {
  font-size: $text-xl;
  font-weight: $weight-bold;
  color: #FFFFFF;
  margin-bottom: 8rpx;
}

.about-version {
  font-size: $text-sm;
  color: rgba(255, 255, 255, 0.7);
  font-family: $font-mono;
}

.about-section {
  background: $color-surface;
  margin-top: 16rpx;
  padding: 24rpx $space-page;
}

.about-section-title {
  font-size: $text-base;
  font-weight: $weight-medium;
  color: $color-title;
  display: block;
  margin-bottom: 16rpx;
}

.about-text {
  font-size: $text-sm;
  color: $color-body;
  line-height: 1.8;
}

.tech-stack {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.tech-item {
  font-size: $text-xs;
  color: $color-primary;
  background: $color-primary-light;
  padding: 6rpx 16rpx;
  border-radius: $radius-card;
}
</style>

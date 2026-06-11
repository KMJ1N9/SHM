<template>
  <view class="login-page">
    <!-- 状态栏占位（custom navigation） -->
    <view class="status-bar" :style="{ height: statusBarHeight + 'px' }" />

    <!-- Logo / 品牌区 -->
    <view class="brand-section">
      <view class="brand-icon">
        <text class="brand-icon-text">
          🎓
        </text>
      </view>
      <text class="brand-title">
        校园二手交易
      </text>
      <text class="brand-subtitle">
        广州应用科技学院 · 肇庆校区
      </text>
    </view>

    <!-- 登录按钮区 -->
    <view class="action-section">
      <!-- 手机号授权按钮 -->
      <button
        class="login-btn"
        open-type="getPhoneNumber"
        :disabled="loading"
        :loading="loading"
        @getphonenumber="onGetPhoneNumber"
      >
        <text v-if="!loading" class="login-btn-text">
          <text class="login-btn-icon">
            📱
          </text>
          手机号授权登录
        </text>
        <text v-else class="login-btn-text">
          登录中...
        </text>
      </button>

      <!-- Dev Mock: 绕过微信授权，填手机号直接登录（仅开发环境显示） -->
      <view v-if="showMockInput" class="mock-section">
        <view class="mock-divider">
          <text class="mock-divider-text">
            或 开发模式直接登录
          </text>
        </view>
        <view class="mock-row">
          <input
            v-model="mockPhone"
            class="mock-input"
            placeholder="输入手机号（如 13800138001）"
            type="number"
            maxlength="11"
          >
          <button
            class="mock-btn"
            :disabled="loading || mockPhone.length < 11"
            :loading="loading"
            @click="onMockLogin"
          >
            <text class="mock-btn-text">
              Mock 登录
            </text>
          </button>
        </view>
      </view>

      <!-- 隐私协议勾选 -->
      <view class="privacy-row">
        <view class="privacy-checkbox" @click="toggleAgreement">
          <text :class="['checkbox-icon', { checked: agreed }]">
            {{ agreed ? '☑' : '☐' }}
          </text>
        </view>
        <text class="privacy-text">
          我已阅读并同意
          <text class="privacy-link" @click.stop="openPrivacy">
            《用户协议》
          </text>
          <text class="privacy-link" @click.stop="openPrivacyPolicy">
            《隐私政策》
          </text>
        </text>
      </view>

      <text class="login-note">
        登录即表示同意以上协议
      </text>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useUserStore } from '@/store/user';
import { BASE_URL } from '@/api/index';

const userStore = useUserStore();

/** 状态栏高度（适配不同机型） */
const statusBarHeight = ref(20);

/** 是否同意隐私协议 */
const agreed = ref(false);

/** 登录加载中——防重复点击 */
const loading = ref(false);

/**
 * Dev Mock: 是否显示 mock 输入框
 *
 * 开发环境始终显示（包括 localhost / LAN IP / 热点 IP），
 * 只有生产域名才隐藏。避免因切换调试 IP 导致 Mock 入口消失。
 */
const showMockInput = !BASE_URL.includes('api.your-domain.com');

/** Dev Mock: 手动输入的手机号 */
const mockPhone = ref('');

/**
 * 页面挂载时：
 * 1. 获取状态栏高度
 * 2. 检查是否已登录——已登录直接跳转首页
 */
onMounted(() => {
  try {
    const sysInfo = uni.getSystemInfoSync();
    statusBarHeight.value = sysInfo.statusBarHeight || 20;
  } catch {
    statusBarHeight.value = 20;
  }

  // 已登录 → 直接跳转首页
  if (userStore.isLoggedIn) {
    uni.switchTab({ url: '/pages/index/index' });
  }
});

/**
 * 切换隐私协议勾选状态
 */
function toggleAgreement() {
  agreed.value = !agreed.value;
}

/**
 * 打开用户协议页面
 */
function openPrivacy() {
  uni.navigateTo({ url: '/pages/agreement/index?type=user' });
}

/**
 * 打开隐私政策页面
 */
function openPrivacyPolicy() {
  uni.navigateTo({ url: '/pages/agreement/index?type=privacy' });
}

/**
 * Dev Mock 登录：用 mock_ 前缀 code 绕过微信授权
 *
 * 后端检测到 code 以 mock_ 开头时，直接取 mock_ 后面的值作为手机号，
 * 不调用微信 API。仅开发环境可用。
 */
async function onMockLogin() {
  if (!agreed.value) {
    uni.showToast({
      title: '请先阅读并同意用户协议和隐私政策',
      icon: 'none',
      duration: 2000,
    });
    return;
  }

  if (mockPhone.value.length !== 11) {
    uni.showToast({ title: '请输入正确的手机号', icon: 'none', duration: 1500 });
    return;
  }

  loading.value = true;
  try {
    await userStore.loginAction(`mock_${mockPhone.value}`);
    uni.showToast({ title: '登录成功', icon: 'success', duration: 1000 });
    setTimeout(() => {
      uni.switchTab({ url: '/pages/index/index' });
    }, 800);
  } catch (err) {
    const msg = err.message || '登录失败，请稍后重试';
    if (msg.includes('限制使用') || msg.includes('封禁')) {
      uni.showModal({
        title: '账号受限',
        content: '您的账号已被限制使用。如有疑问，请联系客服。',
        showCancel: false,
        confirmText: '知道了',
      });
    } else {
      uni.showToast({ title: msg, icon: 'none', duration: 2500 });
    }
  } finally {
    loading.value = false;
  }
}

/**
 * 微信手机号授权回调
 *
 * 流程：微信弹出授权弹窗 → 用户同意 →
 *       微信服务端返回 code → 后端用 code 换取手机号 →
 *       新用户自动注册 / 老用户登录 → 返回 JWT 双 Token
 *
 * @param {Object} e - getPhoneNumber 事件对象
 * @param {Object} e.detail - { code, errMsg, ... }
 */
async function onGetPhoneNumber(e) {
  // 1. 检查隐私协议
  if (!agreed.value) {
    uni.showToast({
      title: '请先阅读并同意用户协议和隐私政策',
      icon: 'none',
      duration: 2000,
    });
    return;
  }

  // 2. 检查微信授权结果
  const { code, errMsg } = e.detail || {};
  if (!code) {
    // 用户拒绝授权或其他错误
    if (errMsg && errMsg.includes('deny')) {
      // 用户主动拒绝，不提示
      return;
    }
    uni.showToast({
      title: '获取手机号失败，请重试',
      icon: 'none',
      duration: 2000,
    });
    return;
  }

  // 3. 调用后端登录
  loading.value = true;
  try {
    await userStore.loginAction(code);
    uni.showToast({ title: '登录成功', icon: 'success', duration: 1000 });
    // 延迟跳转，让用户看到成功提示
    setTimeout(() => {
      uni.switchTab({ url: '/pages/index/index' });
    }, 800);
  } catch (err) {
    const msg = err.message || '登录失败，请稍后重试';
    // 对封禁用户给出明确提示
    if (msg.includes('限制使用') || msg.includes('封禁')) {
      uni.showModal({
        title: '账号受限',
        content: '您的账号已被限制使用。如有疑问，请联系客服。',
        showCancel: false,
        confirmText: '知道了',
      });
    } else {
      uni.showToast({ title: msg, icon: 'none', duration: 2500 });
    }
  } finally {
    loading.value = false;
  }
}
</script>

<style lang="scss">
@import '@/styles/tokens.scss';

.login-page {
  min-height: 100vh;
  background: $color-surface;
  display: flex;
  flex-direction: column;
}

// ── 状态栏占位 ──────────────────────────────────────────
.status-bar {
  width: 100%;
}

// ── 品牌区 ──────────────────────────────────────────────
.brand-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0 $space-page;
}

.brand-icon {
  width: 160rpx;
  height: 160rpx;
  border-radius: $radius-modal;
  background: $color-primary-gradient;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: $shadow-button;
  margin-bottom: $space-card;
}

.brand-icon-text {
  font-size: 72rpx;
}

.brand-title {
  font-size: $text-xl;
  font-weight: $weight-bold;
  color: $color-title;
  margin-bottom: 12rpx;
}

.brand-subtitle {
  font-size: $text-sm;
  color: $color-muted;
}

// ── 操作区 ──────────────────────────────────────────────
.action-section {
  padding: 0 $space-page;
  padding-bottom: calc(48rpx + #{$safe-area-bottom});
}

.login-btn {
  width: 100%;
  height: $btn-height-lg;
  border-radius: $radius-card;
  background: $color-primary-gradient;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: $shadow-button;
  // 重置 button 默认样式
  &::after {
    border: none;
  }
}

.login-btn[disabled] {
  opacity: $opacity-disabled;
}

.login-btn-text {
  color: $color-surface;
  font-size: $text-base;
  font-weight: $weight-medium;
}

.login-btn-icon {
  margin-right: 8rpx;
}

// ── Dev Mock 输入区 ───────────────────────────────────────
.mock-section {
  margin-top: $space-card;
}

.mock-divider {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24rpx;
  position: relative;

  &::before,
  &::after {
    content: '';
    flex: 1;
    height: 1px;
    background: $color-divider;
  }

  &::before {
    margin-right: 24rpx;
  }

  &::after {
    margin-left: 24rpx;
  }
}

.mock-divider-text {
  font-size: $text-xs;
  color: $color-muted;
  white-space: nowrap;
}

.mock-row {
  display: flex;
  gap: 12rpx;
}

.mock-input {
  flex: 1;
  height: $input-height;
  border: 1px solid $color-divider;
  border-radius: $radius-card;
  padding: 0 24rpx;
  font-size: $text-sm;
  color: $color-title;
  background: $color-surface;

  &:focus {
    border-color: $color-primary;
  }
}

.mock-btn {
  flex-shrink: 0;
  width: 180rpx;
  height: $input-height;
  border-radius: $radius-card;
  background: $color-primary;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;

  &::after {
    border: none;
  }
}

.mock-btn[disabled] {
  opacity: $opacity-disabled;
}

.mock-btn-text {
  color: $color-surface;
  font-size: $text-sm;
  font-weight: $weight-medium;
}

// ── 隐私协议勾选 ────────────────────────────────────────
.privacy-row {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  margin-top: $space-card;
  padding: 0 16rpx;
}

.privacy-checkbox {
  flex-shrink: 0;
  padding: 4rpx 8rpx 0 0;
}

.checkbox-icon {
  font-size: $text-lg;
  color: $color-muted;

  &.checked {
    color: $color-primary;
  }
}

.privacy-text {
  font-size: $text-xs;
  color: $color-body;
  line-height: 1.6;
}

.privacy-link {
  color: $color-primary;
}

.login-note {
  text-align: center;
  font-size: $text-xs;
  color: $color-muted;
  margin-top: 8rpx;
}
</style>

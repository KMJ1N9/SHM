<template>
  <view class="settings-page">
    <!-- 关于分组 -->
    <view class="menu-section">
      <view class="menu-section-header">
        <text class="menu-section-title">关于</text>
      </view>
      <view class="menu-item" @click="goAbout">
        <text class="menu-icon">ℹ️</text>
        <text class="menu-label">关于我们</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>

    <!-- 协议分组 -->
    <view class="menu-section">
      <view class="menu-section-header">
        <text class="menu-section-title">协议与政策</text>
      </view>
      <view class="menu-item" @click="showAgreement('user')">
        <text class="menu-icon">📄</text>
        <text class="menu-label">用户协议</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="showAgreement('privacy')">
        <text class="menu-icon">🔒</text>
        <text class="menu-label">隐私政策</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>

    <!-- 账号分组 -->
    <view class="menu-section">
      <view class="menu-section-header">
        <text class="menu-section-title">账号</text>
      </view>
      <view class="menu-item menu-item--danger" @click="onLogout">
        <text class="menu-icon">🚪</text>
        <text class="menu-label menu-label--danger">退出登录</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>

    <!-- 版本信息 -->
    <view class="version-info">
      <text class="version-text">版本 1.0.0</text>
    </view>
  </view>
</template>

<script setup>
/**
 * 设置页 — 应用设置与账号管理
 *
 * 入口：me 页 → 系统设置菜单项
 */
import { useUserStore } from '@/store/user';

const userStore = useUserStore();

function goAbout() {
  uni.navigateTo({ url: '/pages/about/index' });
}

function showAgreement(type) {
  const title = type === 'user' ? '用户协议' : '隐私政策';
  const content =
    type === 'user'
      ? '欢迎使用校园二手交易小程序（以下简称"本平台"）。本平台由广州应用科技学院肇庆校区计算机学院学生独立开发，面向校内师生提供 C2C 闲置物品交易服务。\n\n'
        + '一、用户资格：仅限广州应用科技学院肇庆校区在校师生使用，注册即视为同意本协议。\n\n'
        + '二、用户义务：（1）发布信息须真实准确，不得发布虚假描述或隐瞒商品瑕疵；（2）不得发布法律法规禁止交易的物品（违禁品、盗版、处方药等）；（3）交易过程中应诚信守约，不得恶意取消订单或虚假交易。\n\n'
        + '三、平台责任：本平台仅提供信息展示与交易撮合服务，不参与实际交易，不对商品质量或交易纠纷承担连带责任。平台有权对违规内容进行下架处理，对严重违规用户暂停或终止服务。\n\n'
        + '四、信誉分机制：用户行为将通过信誉分体系进行记录，包括交易完成情况、评价反馈、举报核实等。信誉分低于阈值将限制部分功能使用。\n\n'
        + '五、知识产权：用户发布的内容（文字、图片）视为授权本平台在平台范围内展示使用，用户保留内容的原始所有权。\n\n'
        + '六、免责声明：因不可抗力、网络攻击、系统维护等原因导致的服务中断，平台将尽力恢复但不承担赔偿责任。\n\n'
        + '七、协议更新：本平台有权对本协议进行修订，修订后的协议一经发布即生效。继续使用本平台视为同意修订后的协议。'
      : '本平台重视用户隐私保护。以下隐私政策说明我们如何收集、使用和保护你的个人信息。\n\n'
        + '一、信息收集：（1）微信授权信息：头像、昵称（通过微信手机号授权获取）；（2）用户补充信息：班级、宿舍楼栋（由用户自愿填写）；（3）交易信息：发布记录、订单记录、评价记录、信誉分变动记录；（4）设备信息：设备型号、操作系统版本、网络类型（用于故障排查）。\n\n'
        + '二、信息使用：（1）匹配交易双方：展示用户头像、昵称、班级以便交易对象确认身份；（2）信誉计算：基于交易完成率、评价得分等计算信誉分；（3）平台运营：统计分析用户行为以改进服务。\n\n'
        + '三、信息共享：未经用户明确同意，本平台不会向第三方提供个人信息。以下情形除外：（1）法律法规要求；（2）学校管理部门依规查询；（3）用户自身违规行为需公示处理结果。\n\n'
        + '四、信息安全：采用加密传输（HTTPS）、访问控制、数据库加密存储等措施保护数据安全。用户密码经 bcrypt 加盐哈希存储，不可逆。\n\n'
        + '五、用户权利：（1）可随时在编辑资料页修改个人信息；（2）可申请导出个人数据副本；（3）可申请注销账号，注销后个人信息将在 30 天内彻底删除（法律法规另有规定的除外）。\n\n'
        + '六、数据保留：用户账号存续期间保留数据。账号注销后，交易记录匿名化保留 180 天用于审计，之后永久删除。\n\n'
        + '七、政策更新：本政策如有修订将通过系统通知告知用户，重大变更将要求用户重新确认。';
  uni.showModal({
    title,
    content,
    showCancel: false,
    confirmText: '我知道了',
  });
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

.settings-page {
  min-height: 100vh;
  background: $color-bg;
  padding-bottom: $safe-area-bottom;
}

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

.menu-item--danger {
  &:active { background: rgba(255, 77, 79, 0.05); }
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

.menu-label--danger {
  color: $color-error;
}

.menu-arrow {
  font-size: 36rpx;
  color: $color-muted;
}

.version-info {
  margin-top: 48rpx;
  text-align: center;
}

.version-text {
  font-size: $text-xs;
  color: $color-muted;
}
</style>

<template>
  <view class="error-page">
    <view class="error-content">
      <text class="error-icon">📡</text>
      <text class="error-title">网络连接失败</text>
      <text class="error-desc">请检查你的网络设置后重试</text>
      <button class="error-btn" @click="reload">重新加载</button>
    </view>
  </view>
</template>

<script setup>
/**
 * 网络异常页 — 网络断开或请求超时时的降级展示
 *
 * 集成方式（后续轮次）：
 *   在 api/index.js 拦截器的网络错误分支中跳转到此页面，
 *   或在各页面的 catch 分支中根据错误类型判断是否跳转。
 */
function reload() {
  uni.getNetworkType({
    success: (res) => {
      if (res.networkType === 'none') {
        uni.showToast({ title: '当前无网络连接', icon: 'none' });
        return;
      }
      // 网络已恢复，返回上一页
      const pages = getCurrentPages();
      if (pages.length > 1) {
        uni.navigateBack({ delta: 1 });
      } else {
        uni.switchTab({ url: '/pages/index/index' });
      }
    },
  });
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.error-page {
  min-height: 100vh;
  background: $color-bg;
  display: flex;
  align-items: center;
  justify-content: center;
}

.error-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: $space-page;
}

.error-icon {
  font-size: 128rpx;
  margin-bottom: $space-card;
}

.error-title {
  font-size: $text-xl;
  font-weight: $weight-bold;
  color: $color-title;
  margin-bottom: $space-content;
}

.error-desc {
  font-size: $text-sm;
  color: $color-muted;
  text-align: center;
  line-height: 1.6;
  margin-bottom: 64rpx;
}

.error-btn {
  width: 280rpx;
  height: $btn-height-md;
  line-height: $btn-height-md;
  background: $color-primary;
  color: #FFFFFF;
  font-size: $text-base;
  border-radius: $radius-full;
  border: none;
}
</style>

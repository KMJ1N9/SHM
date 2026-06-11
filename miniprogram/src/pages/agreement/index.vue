<template>
  <view class="agreement-page">
    <scroll-view class="agreement-body" scroll-y>
      <view class="agreement-content">
        <text class="agreement-text">
          {{ content }}
        </text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
/**
 * 用户协议 / 隐私政策展示页
 *
 * Query 参数：
 *   ?type=user    → 用户协议
 *   ?type=privacy → 隐私政策
 *
 * 来源：utils/agreement.js（单一事实源，login/settings/agreement 三处共用）
 */
import { ref, onMounted } from 'vue';
import { USER_AGREEMENT, PRIVACY_POLICY } from '@/utils/agreement';

const title = ref('');
const content = ref('');

onMounted(() => {
  const pages = getCurrentPages();
  const options = pages[pages.length - 1]?.options || {};
  const type = options.type || 'user';

  if (type === 'privacy') {
    title.value = '隐私政策';
    content.value = PRIVACY_POLICY;
  } else {
    title.value = '用户协议';
    content.value = USER_AGREEMENT;
  }

  uni.setNavigationBarTitle({ title: title.value });
});
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.agreement-page {
  min-height: 100vh;
  background: $color-surface;
}

.agreement-body {
  height: 100vh;
}

.agreement-content {
  padding: $space-page;
  padding-bottom: calc(#{$space-page} + #{$safe-area-bottom});
}

.agreement-text {
  font-size: $text-base;
  color: $color-body;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>

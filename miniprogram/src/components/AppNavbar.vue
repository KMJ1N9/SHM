<template>
  <view class="app-navbar" :style="navbarStyle">
    <!-- 状态栏占位 -->
    <view class="app-navbar-status" :style="{ height: statusBarHeight + 'px' }" />

    <!-- 导航栏主体 -->
    <view class="app-navbar-body">
      <!-- 左侧：返回按钮 -->
      <view v-if="showBack" class="app-navbar-left" @click="handleBack">
        <text class="app-navbar-back">{{ '‹' }}</text>
      </view>
      <view v-else class="app-navbar-left" />

      <!-- 中间：标题 -->
      <text class="app-navbar-title" :style="{ color: titleColor }">{{ title }}</text>

      <!-- 右侧：操作按钮 -->
      <view class="app-navbar-right">
        <text
          v-if="rightIcon"
          class="app-navbar-right-icon"
          @click="$emit('rightClick')"
        >{{ rightIcon }}</text>
        <text
          v-else-if="rightText"
          class="app-navbar-right-text"
          @click="$emit('rightClick')"
        >{{ rightText }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
/**
 * AppNavbar — 自定义导航栏组件
 *
 * 用于 navigationStyle: 'custom' 的页面，替代系统导航栏。
 * 自动计算状态栏高度，支持返回按钮和右侧操作区。
 *
 * 【状态：已实现，待集成】当前管理页面使用系统导航栏（pages.json 中
 * navigationBarBackgroundColor），该组件作为基础设施已就绪。后续轮次如需
 * 统一导航栏风格（如渐变背景），将页面 navigationStyle 改为 custom 后即可使用。
 *
 * Props:
 *   title          — 标题文字
 *   showBack       — 是否显示返回按钮
 *   backgroundColor — 背景色（支持渐变字符串）
 *   titleColor     — 标题颜色
 *   rightIcon      — 右侧 emoji 图标
 *   rightText      — 右侧文字
 *
 * Events:
 *   @back       — 点击返回（默认 navigateBack）
 *   @rightClick — 点击右侧按钮
 */
import { computed } from 'vue';

const props = defineProps({
  title:           { type: String,  default: '' },
  showBack:        { type: Boolean, default: false },
  backgroundColor: { type: String,  default: '#4A90D9' },
  titleColor:      { type: String,  default: '#FFFFFF' },
  rightIcon:       { type: String,  default: '' },
  rightText:       { type: String,  default: '' },
});

const emit = defineEmits(['back', 'rightClick']);

const systemInfo = uni.getSystemInfoSync();
const statusBarHeight = systemInfo.statusBarHeight || 20;

const navbarStyle = computed(() => ({
  background: props.backgroundColor,
}));

function handleBack() {
  emit('back');
  uni.navigateBack({ delta: 1 });
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.app-navbar {
  width: 100%;
  position: sticky;
  top: 0;
  z-index: 999;
}

.app-navbar-status {
  width: 100%;
}

.app-navbar-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 88rpx;
  padding: 0 $space-page;
}

.app-navbar-left {
  width: 80rpx;
  flex-shrink: 0;
}

.app-navbar-back {
  font-size: 48rpx;
  color: #FFFFFF;
  line-height: 88rpx;
}

.app-navbar-title {
  flex: 1;
  font-size: $text-lg;
  font-weight: $weight-bold;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-navbar-right {
  width: 80rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.app-navbar-right-icon {
  font-size: $text-lg;
  line-height: 88rpx;
}

.app-navbar-right-text {
  font-size: $text-sm;
  color: #FFFFFF;
  line-height: 88rpx;
}
</style>

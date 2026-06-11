<template>
  <view class="empty-state" :style="{ paddingTop }">
    <slot>
      <text class="empty-state-icon">{{ icon }}</text>
      <text class="empty-state-title">{{ title }}</text>
      <text v-if="description" class="empty-state-desc">{{ description }}</text>
    </slot>
    <view
      v-if="actionText"
      class="empty-state-action"
      @click="$emit('action')"
    >
      <text class="empty-state-action-text">{{ actionText }}</text>
    </view>
  </view>
</template>

<script setup>
/**
 * EmptyState — 通用空状态组件
 *
 * 用于列表为空、搜索无结果等场景。支持自定义图标/文案/操作按钮，
 * 也可通过默认插槽传入自定义内容。
 *
 * Props:
 *   icon        — emoji 图标
 *   title       — 主标题
 *   description — 描述文字（可选）
 *   actionText  — 操作按钮文字（可选，不传不显示按钮）
 *   paddingTop  — 顶部留白
 *
 * Events:
 *   @action — 点击操作按钮
 */
defineProps({
  icon:        { type: String,  default: '📦' },
  title:       { type: String,  default: '暂无数据' },
  description: { type: String,  default: '' },
  actionText:  { type: String,  default: '' },
  paddingTop:  { type: String,  default: '160rpx' },
});

defineEmits(['action']);
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 $space-page;
}

.empty-state-icon {
  font-size: 96rpx;
  line-height: 1;
  margin-bottom: 24rpx;
}

.empty-state-title {
  font-size: $text-base;
  color: $color-muted;
  line-height: $line-height;
  text-align: center;
}

.empty-state-desc {
  font-size: $text-sm;
  color: $color-muted;
  margin-top: 8rpx;
  line-height: $line-height;
  text-align: center;
}

.empty-state-action {
  margin-top: 40rpx;
  padding: 16rpx 48rpx;
  border-radius: $radius-full;
  background: $color-primary;
}

.empty-state-action-text {
  font-size: $text-base;
  color: #FFFFFF;
}
</style>

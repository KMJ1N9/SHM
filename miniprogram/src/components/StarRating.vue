<template>
  <view
    class="star-rating"
    :class="[`star-rating--${size}`, { 'star-rating--readonly': readonly }]"
  >
    <text
      v-for="n in max"
      :key="n"
      class="star"
      :class="{
        'star--filled': n <= displayValue,
        'star--hover': n <= hoverIndex && !readonly,
      }"
      @tap="handleClick(n)"
      @touchstart="hoverIndex = n"
      @touchcancel="hoverIndex = 0"
    >
      {{ n <= displayValue ? '★' : '☆' }}
    </text>
  </view>
</template>

<script setup>
/**
 * StarRating — 交互式五星评分组件
 *
 * 支持 3 种尺寸（sm/md/lg）、只读模式、触摸预览。
 * 用于商品详情评分展示、评价弹窗三维度评分。
 *
 * 遵循 Vue 3 v-model 协议：
 *   - 接收 modelValue prop
 *   - 触发 update:modelValue 事件
 *
 * Props:
 *   modelValue — 当前评分 (1-5)，默认 0，支持 v-model 双向绑定
 *   max        — 最大星星数，默认 5
 *   size       — 星星大小：'sm' | 'md' | 'lg'
 *   readonly   — 只读模式（禁止点击，不显示 hover 效果）
 */

import { ref, computed } from 'vue';

const props = defineProps({
  modelValue: {
    type: Number,
    default: 0,
  },
  max: {
    type: Number,
    default: 5,
  },
  size: {
    type: String,
    default: 'md',
    validator: (v) => ['sm', 'md', 'lg'].includes(v),
  },
  readonly: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(['update:modelValue']);

/** 当前触摸悬停的星星索引（0 表示无悬停） */
const hoverIndex = ref(0);

/** 实际显示的值：悬停时用预览值，否则用 props.modelValue */
const displayValue = computed(() => {
  return hoverIndex.value > 0 ? hoverIndex.value : props.modelValue;
});

/**
 * 点击星星 — Vue 3 的 emit() 在父组件中同步执行，
 * modelValue prop 在 hoverIndex 重置前已完成更新。
 * @param {number} n - 点击的星星序号 (1-based)
 */
function handleClick(n) {
  if (props.readonly) return;
  emit('update:modelValue', n);
  // emit 后父组件已同步更新 modelValue，此时重置 hover
  // displayValue 会回落到刚更新的 modelValue（即 n）
  hoverIndex.value = 0;
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.star-rating {
  display: inline-flex;
  align-items: center;
  gap: 4rpx;

  &--readonly {
    pointer-events: none;
  }

  // 尺寸变体
  &--sm {
    .star {
      font-size: 28rpx;
    }
  }

  &--md {
    .star {
      font-size: 36rpx;
    }
  }

  &--lg {
    .star {
      font-size: 48rpx;
    }
  }
}

.star {
  color: $color-divider;
  transition: transform 0.15s ease, color 0.15s ease;
  line-height: 1;
  // 增大触摸热区（小程序无 cursor:pointer，靠父级 @tap 处理）
  padding: 4rpx;

  &--filled {
    color: $color-warning;
  }

  &--hover {
    transform: scale(1.2);
  }
}
</style>

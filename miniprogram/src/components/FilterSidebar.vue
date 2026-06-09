<template>
  <!-- 筛选侧边栏 — 从右侧滑入的抽屉面板 -->
  <view v-if="visible" class="filter-sidebar">
    <!-- 遮罩层（点击关闭） -->
    <view class="filter-overlay" @click="handleCancel" />

    <!-- 面板区 -->
    <view class="filter-panel">
      <!-- 头部 -->
      <view class="filter-header">
        <text class="filter-title">
          筛选
        </text>
        <view class="filter-close" @click="handleCancel">
          <text class="filter-close-icon">
            ✕
          </text>
        </view>
      </view>

      <!-- 筛选项滚动区 -->
      <scroll-view class="filter-body" scroll-y>
        <!-- 分类 -->
        <view class="filter-group">
          <text class="filter-label">
            分类
          </text>
          <view class="filter-grid">
            <view
              v-for="item in CATEGORIES"
              :key="item.value"
              class="filter-tag"
              :class="{ 'filter-tag--active': local.category === item.value }"
              @click="local.category = item.value"
            >
              <text class="filter-tag-text">
                {{ item.label }}
              </text>
            </view>
          </view>
        </view>

        <!-- 成色 -->
        <view class="filter-group">
          <text class="filter-label">
            成色
          </text>
          <view class="filter-grid">
            <view
              v-for="item in CONDITIONS"
              :key="item.value"
              class="filter-tag"
              :class="{ 'filter-tag--active': local.condition === item.value }"
              @click="local.condition = item.value"
            >
              <text class="filter-tag-text">
                {{ item.label }}
              </text>
            </view>
          </view>
        </view>

        <!-- 价格区间 -->
        <view class="filter-group">
          <text class="filter-label">
            价格区间
          </text>
          <view class="filter-price-row">
            <input
              class="filter-price-input"
              type="digit"
              placeholder="最低价"
              :value="local.priceMin"
              @input="onPriceMinInput"
            >
            <text class="filter-price-dash">
              —
            </text>
            <input
              class="filter-price-input"
              type="digit"
              placeholder="最高价"
              :value="local.priceMax"
              @input="onPriceMaxInput"
            >
          </view>
        </view>
      </scroll-view>

      <!-- 底部按钮 -->
      <view class="filter-footer">
        <view class="filter-btn filter-btn--reset" @click="handleReset">
          <text class="filter-btn-text">
            重置
          </text>
        </view>
        <view class="filter-btn filter-btn--apply" @click="handleApply">
          <text class="filter-btn-text">
            确定
          </text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { reactive, watch } from 'vue';

// ============================================================
// 常量
// ============================================================

const CATEGORIES = [
  { label: '全部', value: '' },
  { label: '电子产品', value: '电子产品' },
  { label: '书籍教材', value: '书籍教材' },
  { label: '生活用品', value: '生活用品' },
  { label: '服饰鞋包', value: '服饰鞋包' },
  { label: '运动户外', value: '运动户外' },
  { label: '其他', value: '其他' },
];

const CONDITIONS = [
  { label: '全部', value: '' },
  { label: '全新', value: '全新' },
  { label: '95新', value: '95新' },
  { label: '9成新', value: '9成新' },
  { label: '8成新', value: '8成新' },
  { label: '7成新及以下', value: '7成新及以下' },
];

// ============================================================
// Props & Emits
// ============================================================

const props = defineProps({
  /** 面板是否可见 */
  visible: {
    type: Boolean,
    default: false,
  },
  /** 外部传入的当前筛选值（用于回显） */
  modelValue: {
    type: Object,
    default: () => ({
      category: '',
      condition: '',
      priceMin: '',
      priceMax: '',
    }),
  },
});

const emit = defineEmits(['update:visible', 'apply', 'reset']);

// ============================================================
// 本地筛选状态（拷贝一份，点击"确定"才提交）
// ============================================================

const local = reactive({
  category: '',
  condition: '',
  priceMin: '',
  priceMax: '',
});

/**
 * 同步外部 props → 本地状态（面板打开时）
 */
watch(
  () => props.modelValue,
  (val) => {
    if (val) {
      local.category = val.category || '';
      local.condition = val.condition || '';
      local.priceMin = val.priceMin || '';
      local.priceMax = val.priceMax || '';
    }
  },
  { immediate: true, deep: true }
);

// ============================================================
// 价格输入处理
// ============================================================

function onPriceMinInput(e) {
  const raw = e.detail.value;
  local.priceMin = sanitizePrice(raw);
}

function onPriceMaxInput(e) {
  const raw = e.detail.value;
  local.priceMax = sanitizePrice(raw);
}

/**
 * 价格输入清洗：去除非数字字符，防多小数点，限两位小数，剥离前导零
 */
function sanitizePrice(value) {
  if (value === '' || value === undefined) return '';

  // 逐字符过滤：只保留数字和第一个小数点
  let cleaned = '';
  let hasDot = false;
  for (const ch of value) {
    if (ch >= '0' && ch <= '9') {
      cleaned += ch;
    } else if (ch === '.' && !hasDot) {
      hasDot = true;
      cleaned += ch;
    }
    // 忽略其他字符（不会静默将非法输入转为合法值）
  }

  // 限制两位小数
  const dotIdx = cleaned.indexOf('.');
  if (dotIdx !== -1 && cleaned.length - dotIdx - 1 > 2) {
    cleaned = cleaned.substring(0, dotIdx + 3);
  }

  // 剥离前导零（保留单个零：0.x → 0.x，00.x → 0.x，000 → 0）
  if (cleaned.length > 1 && cleaned[0] === '0' && cleaned[1] !== '.') {
    cleaned = cleaned.replace(/^0+/, '') || '0';
  }

  return cleaned;
}

// ============================================================
// 事件
// ============================================================

function handleApply() {
  // 价格区间校验
  const min = parseFloat(local.priceMin) || 0;
  const max = parseFloat(local.priceMax) || 0;
  if (local.priceMin && local.priceMax && min > max) {
    uni.showToast({ title: '最低价不能高于最高价', icon: 'none', duration: 1500 });
    return;
  }

  emit('apply', {
    category: local.category,
    condition: local.condition,
    priceMin: local.priceMin,
    priceMax: local.priceMax,
  });
  emit('update:visible', false);
}

function handleReset() {
  local.category = '';
  local.condition = '';
  local.priceMin = '';
  local.priceMax = '';
  emit('reset');
  emit('update:visible', false);
}

function handleCancel() {
  // 取消：恢复到外部传入的值
  local.category = props.modelValue?.category || '';
  local.condition = props.modelValue?.condition || '';
  local.priceMin = props.modelValue?.priceMin || '';
  local.priceMax = props.modelValue?.priceMax || '';
  emit('update:visible', false);
}
</script>

<style lang="scss">
@import '@/styles/tokens.scss';

.filter-sidebar {
  position: fixed;
  inset: 0;
  z-index: 100;
}

// 遮罩层
.filter-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
}

// 面板（从右侧滑入）
.filter-panel {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 560rpx;
  background: $color-surface;
  display: flex;
  flex-direction: column;
  animation: slideInRight 0.25s ease-out;
}

@keyframes slideInRight {
  from { transform: translateX(100%); }
  to   { transform: translateX(0); }
}

// 头部
.filter-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $space-page $space-page 24rpx;
  border-bottom: 1rpx solid $color-divider;
}

.filter-title {
  font-size: $text-xl;
  font-weight: $weight-bold;
  color: $color-title;
}

.filter-close {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-full;
  background: $color-divider;
}

.filter-close-icon {
  font-size: $text-base;
  color: $color-muted;
}

// 滚动区
.filter-body {
  flex: 1;
  padding: $space-card $space-page;
  overflow-y: auto;
}

// 筛选项组
.filter-group {
  margin-bottom: 36rpx;

  &:last-child {
    margin-bottom: 0;
  }
}

.filter-label {
  display: block;
  font-size: $text-base;
  font-weight: $weight-medium;
  color: $color-title;
  margin-bottom: $space-card;
}

// 标签网格
.filter-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.filter-tag {
  padding: 12rpx 24rpx;
  border-radius: $radius-card;
  background: $color-divider;
  transition: background 0.2s, color 0.2s;

  &--active {
    background: $color-primary-light;
  }
}

.filter-tag-text {
  font-size: $text-sm;
  color: $color-body;
  line-height: 1.5;

  .filter-tag--active & {
    color: $color-primary;
    font-weight: $weight-medium;
  }
}

// 价格区间
.filter-price-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.filter-price-input {
  flex: 1;
  height: 72rpx;
  padding: 0 20rpx;
  border-radius: $radius-card;
  background: $color-divider;
  font-size: $text-sm;
  color: $color-title;
  text-align: center;
}

.filter-price-dash {
  font-size: $text-base;
  color: $color-muted;
  flex-shrink: 0;
}

// 底部按钮
.filter-footer {
  display: flex;
  gap: 20rpx;
  padding: $space-card $space-page;
  padding-bottom: calc($space-card + $safe-area-bottom);
  border-top: 1rpx solid $color-divider;
}

.filter-btn {
  flex: 1;
  height: $btn-height-md;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-card;
  font-size: $text-base;

  &--reset {
    background: $color-divider;
  }

  &--apply {
    background: $color-primary;
  }
}

.filter-btn-text {
  font-size: $text-base;
  font-weight: $weight-medium;

  .filter-btn--reset & {
    color: $color-body;
  }

  .filter-btn--apply & {
    color: #FFFFFF;
  }
}
</style>

<template>
  <view class="step-body">
    <view class="step-title">填写商品信息</view>

    <view class="form-group">
      <text class="form-label">商品名称 <text class="required">*</text></text>
      <input
        v-model="localForm.title"
        class="form-input"
        placeholder="例：高等数学第七版（上册）"
        maxlength="200"
        @input="emitChange"
      >
    </view>

    <view class="form-group">
      <text class="form-label">分类 <text class="required">*</text></text>
      <view class="category-grid">
        <view
          v-for="cat in categories"
          :key="cat.value"
          class="category-item"
          :class="{ selected: localForm.category === cat.value }"
          @click="selectCategory(cat.value)"
        >
          <text class="category-icon">{{ cat.icon }}</text>
          <text class="category-text">{{ cat.label }}</text>
        </view>
      </view>
    </view>

    <view class="form-group">
      <text class="form-label">成色 <text class="required">*</text></text>
      <view class="condition-row">
        <view
          v-for="c in conditions"
          :key="c"
          class="condition-item"
          :class="{ selected: localForm.condition === c }"
          @click="selectCondition(c)"
        >
          {{ c }}
        </view>
      </view>
    </view>

    <view class="form-row">
      <view class="form-group form-half">
        <text class="form-label">原价（元）<text class="required">*</text></text>
        <input
          v-model="localForm.original_price"
          class="form-input"
          placeholder="0.00"
          type="digit"
          @input="emitChange"
        >
      </view>
      <view class="form-group form-half">
        <text class="form-label">售价（元）<text class="required">*</text></text>
        <input
          v-model="localForm.price"
          class="form-input"
          placeholder="0.00"
          type="digit"
          @input="emitChange"
        >
      </view>
    </view>

    <view class="form-group">
      <text class="form-label">交易地点 <text class="required">*</text></text>
      <input
        v-model="localForm.trade_location"
        class="form-input"
        placeholder="例：图书馆门前 / 1栋宿舍楼下"
        maxlength="200"
        @input="emitChange"
      >
    </view>

    <view class="form-group">
      <view class="form-switch-row">
        <text class="form-label">支持议价</text>
        <switch
          :checked="localForm.negotiable"
          color="#4A90D9"
          @change="(e) => { localForm.negotiable = e.detail.value; emitChange(); }"
        />
      </view>
    </view>

    <view class="form-group">
      <text class="form-label">商品描述</text>
      <textarea
        v-model="localForm.description"
        class="form-textarea"
        placeholder="描述商品的使用情况、购买时间、瑕疵等（选填）"
        maxlength="2000"
        :auto-height="true"
        @input="emitChange"
      />
      <text class="char-count">{{ localForm.description.length }}/2000</text>
    </view>
  </view>
</template>

<script setup>
import { reactive, watch, onMounted } from 'vue';

const props = defineProps({
  form: { type: Object, required: true },
});

const emit = defineEmits(['update']);

/** 分类选项 */
const categories = [
  { value: '电子产品', label: '电子产品', icon: '📱' },
  { value: '书籍教材', label: '书籍教材', icon: '📚' },
  { value: '生活用品', label: '生活用品', icon: '🏠' },
  { value: '服饰鞋包', label: '服饰鞋包', icon: '👗' },
  { value: '运动户外', label: '运动户外', icon: '⚽' },
  { value: '其他', label: '其他', icon: '📦' },
];

/** 成色选项 */
const conditions = ['全新', '95新', '9成新', '8成新', '7成新及以下'];

/** 本地表单副本（避免直接修改 prop） */
const localForm = reactive({
  title: '',
  description: '',
  category: '',
  condition: '',
  original_price: '',
  price: '',
  trade_location: '',
  negotiable: true,
});

// 初始化：从 prop 同步到本地副本
watch(() => props.form, (newVal) => {
  Object.assign(localForm, {
    title: newVal.title || '',
    description: newVal.description || '',
    category: newVal.category || '',
    condition: newVal.condition || '',
    original_price: newVal.original_price || '',
    price: newVal.price || '',
    trade_location: newVal.trade_location || '',
    negotiable: newVal.negotiable !== false,
  });
}, { immediate: true, deep: true });

function selectCategory(value) {
  localForm.category = value;
  emitChange();
}

function selectCondition(value) {
  localForm.condition = value;
  emitChange();
}

function emitChange() {
  emit('update', { ...localForm });
}

onMounted(() => {
  Object.assign(localForm, {
    title: props.form.title || '',
    description: props.form.description || '',
    category: props.form.category || '',
    condition: props.form.condition || '',
    original_price: props.form.original_price || '',
    price: props.form.price || '',
    trade_location: props.form.trade_location || '',
    negotiable: props.form.negotiable !== false,
  });
});
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.step-body {
  flex: 1;
  padding: $space-card $space-page;
  overflow-y: auto;
}

.step-title {
  font-size: $text-xl;
  font-weight: $weight-bold;
  color: $color-title;
  margin-bottom: 8rpx;
}

.form-group {
  margin-bottom: $space-card;
}

.form-label {
  display: block;
  font-size: $text-sm;
  font-weight: $weight-medium;
  color: $color-title;
  margin-bottom: 12rpx;
}

.required {
  color: $color-error;
}

.form-input {
  width: 100%;
  height: $input-height;
  border: 1px solid $color-divider;
  border-radius: $radius-card;
  padding: 0 24rpx;
  font-size: $text-base;
  color: $color-title;
  background: $color-surface;
  box-sizing: border-box;

  &:focus {
    border-color: $color-primary;
  }
}

.form-textarea {
  width: 100%;
  min-height: 160rpx;
  border: 1px solid $color-divider;
  border-radius: $radius-card;
  padding: 16rpx 24rpx;
  font-size: $text-base;
  color: $color-title;
  background: $color-surface;
  box-sizing: border-box;

  &:focus {
    border-color: $color-primary;
  }
}

.char-count {
  display: block;
  text-align: right;
  font-size: $text-xs;
  color: $color-muted;
  margin-top: 4rpx;
}

.form-row {
  display: flex;
  gap: 24rpx;
}

.form-half {
  flex: 1;
}

.form-switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.category-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.category-item {
  width: calc((100% - 32rpx) / 3);
  padding: 20rpx 0;
  background: $color-surface;
  border: 2rpx solid $color-divider;
  border-radius: $radius-card;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;

  &.selected {
    border-color: $color-primary;
    background: $color-primary-light;
  }
}

.category-icon {
  font-size: 40rpx;
}

.category-text {
  font-size: $text-xs;
  color: $color-body;

  .selected & {
    color: $color-primary;
    font-weight: $weight-medium;
  }
}

.condition-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.condition-item {
  padding: 12rpx 24rpx;
  background: $color-surface;
  border: 2rpx solid $color-divider;
  border-radius: $radius-card;
  font-size: $text-sm;
  color: $color-body;

  &.selected {
    border-color: $color-primary;
    background: $color-primary-light;
    color: $color-primary;
    font-weight: $weight-medium;
  }
}
</style>

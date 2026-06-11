<template>
  <view class="step-body">
    <view class="step-title">确认发布</view>

    <!-- 图片预览 -->
    <view v-if="form.images && form.images.length > 0" class="preview-images">
      <image
        v-for="(url, i) in form.images"
        :key="i"
        class="preview-image"
        :src="url"
        mode="aspectFill"
        @click="previewImages(i)"
      />
    </view>

    <!-- 信息预览 -->
    <view class="preview-info">
      <view class="preview-title">{{ form.title || '（未填写）' }}</view>

      <view class="preview-row">
        <text class="preview-label">分类</text>
        <text class="preview-value">{{ form.category ? getCategoryLabel(form.category) : '（未选择）' }}</text>
      </view>

      <view class="preview-row">
        <text class="preview-label">成色</text>
        <text class="preview-value">{{ form.condition || '（未选择）' }}</text>
      </view>

      <view class="preview-row">
        <text class="preview-label">价格</text>
        <view>
          <text class="preview-price">¥{{ form.price || '0' }}</text>
          <text v-if="form.original_price" class="preview-original">原价 ¥{{ form.original_price }}</text>
        </view>
      </view>

      <view class="preview-row">
        <text class="preview-label">交易地点</text>
        <text class="preview-value">{{ form.trade_location || '（未填写）' }}</text>
      </view>

      <view class="preview-row">
        <text class="preview-label">议价</text>
        <text class="preview-value">{{ form.negotiable ? '支持议价' : '不议价' }}</text>
      </view>

      <view v-if="form.description" class="preview-desc">
        <text class="preview-label">描述</text>
        <text class="preview-desc-text">{{ form.description }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
const props = defineProps({
  form: { type: Object, required: true },
});

const categories = [
  { value: '电子产品', label: '电子产品', icon: '📱' },
  { value: '书籍教材', label: '书籍教材', icon: '📚' },
  { value: '生活用品', label: '生活用品', icon: '🏠' },
  { value: '服饰鞋包', label: '服饰鞋包', icon: '👗' },
  { value: '运动户外', label: '运动户外', icon: '⚽' },
  { value: '其他', label: '其他', icon: '📦' },
];

function getCategoryLabel(value) {
  const cat = categories.find((c) => c.value === value);
  return cat ? `${cat.icon} ${cat.label}` : value;
}

function previewImages(index) {
  if (props.form.images && props.form.images.length > 0) {
    uni.previewImage({
      current: props.form.images[index],
      urls: props.form.images,
    });
  }
}
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

.preview-images {
  display: flex;
  gap: 12rpx;
  margin-bottom: $space-card;
  overflow-x: auto;
}

.preview-image {
  width: 160rpx;
  height: 160rpx;
  border-radius: $radius-card;
  flex-shrink: 0;
  background: $color-divider;
}

.preview-info {
  background: $color-surface;
  border-radius: $radius-card;
  padding: $space-card;
}

.preview-title {
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: $color-title;
  margin-bottom: $space-card;
  padding-bottom: $space-card;
  border-bottom: 1px solid $color-divider;
}

.preview-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12rpx 0;
}

.preview-label {
  font-size: $text-sm;
  color: $color-muted;
  flex-shrink: 0;
  margin-right: 24rpx;
}

.preview-value {
  font-size: $text-sm;
  color: $color-title;
  text-align: right;
}

.preview-price {
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: $color-error;
  font-family: $font-mono;
}

.preview-original {
  font-size: $text-xs;
  color: $color-muted;
  text-decoration: line-through;
  margin-left: 12rpx;
}

.preview-desc {
  padding-top: 16rpx;
  border-top: 1px solid $color-divider;
  margin-top: 12rpx;
}

.preview-desc-text {
  display: block;
  font-size: $text-sm;
  color: $color-body;
  margin-top: 8rpx;
  line-height: 1.6;
}
</style>

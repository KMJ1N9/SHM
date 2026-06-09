<template>
  <view class="publish-page">
    <!-- 进度条 -->
    <view class="progress-bar">
      <view class="progress-steps">
        <view
          v-for="(step, i) in steps"
          :key="i"
          class="progress-step"
          :class="{ active: currentStep >= i, done: currentStep > i }"
        >
          <view class="step-dot">
            {{ currentStep > i ? '✓' : i + 1 }}
          </view>
          <text class="step-label">
            {{ step }}
          </text>
          <view v-if="i < steps.length - 1" class="step-line" :class="{ filled: currentStep > i }" />
        </view>
      </view>
    </view>

    <!-- ============================================================ -->
    <!-- Step 1: 上传图片 -->
    <!-- ============================================================ -->
    <view v-if="currentStep === 0" class="step-body">
      <view class="step-title">
        上传商品图片
      </view>
      <text class="step-hint">
        清晰的图片能让商品更快卖出（1-6 张）
      </text>

      <ImageUploader ref="uploaderRef" :max-count="6" @update:files="onFilesChange" />
    </view>

    <!-- ============================================================ -->
    <!-- Step 2: 填写商品信息 -->
    <!-- ============================================================ -->
    <view v-if="currentStep === 1" class="step-body">
      <view class="step-title">
        填写商品信息
      </view>

      <view class="form-group">
        <text class="form-label">
          商品名称 <text class="required">
            *
          </text>
        </text>
        <input
          v-model="form.title"
          class="form-input"
          placeholder="例：高等数学第七版（上册）"
          maxlength="200"
        >
      </view>

      <view class="form-group">
        <text class="form-label">
          分类 <text class="required">
            *
          </text>
        </text>
        <view class="category-grid">
          <view
            v-for="cat in categories"
            :key="cat.value"
            class="category-item"
            :class="{ selected: form.category === cat.value }"
            @click="form.category = cat.value"
          >
            <text class="category-icon">
              {{ cat.icon }}
            </text>
            <text class="category-text">
              {{ cat.label }}
            </text>
          </view>
        </view>
      </view>

      <view class="form-group">
        <text class="form-label">
          成色 <text class="required">
            *
          </text>
        </text>
        <view class="condition-row">
          <view
            v-for="c in conditions"
            :key="c"
            class="condition-item"
            :class="{ selected: form.condition === c }"
            @click="form.condition = c"
          >
            {{ c }}
          </view>
        </view>
      </view>

      <view class="form-row">
        <view class="form-group form-half">
          <text class="form-label">
            原价（元）<text class="required">
              *
            </text>
          </text>
          <input
            v-model="form.original_price"
            class="form-input"
            placeholder="0.00"
            type="digit"
          >
        </view>
        <view class="form-group form-half">
          <text class="form-label">
            售价（元）<text class="required">
              *
            </text>
          </text>
          <input
            v-model="form.price"
            class="form-input"
            placeholder="0.00"
            type="digit"
          >
        </view>
      </view>

      <view class="form-group">
        <text class="form-label">
          交易地点 <text class="required">
            *
          </text>
        </text>
        <input
          v-model="form.trade_location"
          class="form-input"
          placeholder="例：图书馆门前 / 1栋宿舍楼下"
          maxlength="200"
        >
      </view>

      <view class="form-group">
        <view class="form-switch-row">
          <text class="form-label">
            支持议价
          </text>
          <switch
            :checked="form.negotiable"
            color="#4A90D9"
            @change="(e) => (form.negotiable = e.detail.value)"
          />
        </view>
      </view>

      <view class="form-group">
        <text class="form-label">
          商品描述
        </text>
        <textarea
          v-model="form.description"
          class="form-textarea"
          placeholder="描述商品的使用情况、购买时间、瑕疵等（选填）"
          maxlength="2000"
          :auto-height="true"
        />
        <text class="char-count">
          {{ form.description.length }}/2000
        </text>
      </view>
    </view>

    <!-- ============================================================ -->
    <!-- Step 3: 确认发布 -->
    <!-- ============================================================ -->
    <view v-if="currentStep === 2" class="step-body">
      <view class="step-title">
        确认发布
      </view>

      <!-- 图片预览 -->
      <view v-if="form.images.length > 0" class="preview-images">
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
        <view class="preview-title">
          {{ form.title || '（未填写）' }}
        </view>

        <view class="preview-row">
          <text class="preview-label">
            分类
          </text>
          <text class="preview-value">
            {{ form.category ? (getCategoryLabel(form.category)) : '（未选择）' }}
          </text>
        </view>

        <view class="preview-row">
          <text class="preview-label">
            成色
          </text>
          <text class="preview-value">
            {{ form.condition || '（未选择）' }}
          </text>
        </view>

        <view class="preview-row">
          <text class="preview-label">
            价格
          </text>
          <view>
            <text class="preview-price">
              ¥{{ form.price || '0' }}
            </text>
            <text v-if="form.original_price" class="preview-original">
              原价 ¥{{ form.original_price }}
            </text>
          </view>
        </view>

        <view class="preview-row">
          <text class="preview-label">
            交易地点
          </text>
          <text class="preview-value">
            {{ form.trade_location || '（未填写）' }}
          </text>
        </view>

        <view class="preview-row">
          <text class="preview-label">
            议价
          </text>
          <text class="preview-value">
            {{ form.negotiable ? '支持议价' : '不议价' }}
          </text>
        </view>

        <view v-if="form.description" class="preview-desc">
          <text class="preview-label">
            描述
          </text>
          <text class="preview-desc-text">
            {{ form.description }}
          </text>
        </view>
      </view>
    </view>

    <!-- ============================================================ -->
    <!-- 底部操作栏 -->
    <!-- ============================================================ -->
    <view class="bottom-bar">
      <!-- Step 0/1: 上一步 + 下一步 -->
      <template v-if="currentStep < 2">
        <button v-if="currentStep > 0" class="btn btn-prev" @click="prevStep">
          上一步
        </button>
        <button
          class="btn btn-next"
          :class="{ 'btn-full': currentStep === 0 }"
          :disabled="loading"
          :loading="loading"
          @click="nextStep"
        >
          {{ currentStep === 1 ? '预览' : '下一步' }}
        </button>
      </template>

      <!-- Step 2: 确认发布 -->
      <template v-if="currentStep === 2">
        <button class="btn btn-prev" :disabled="loading" @click="prevStep">
          返回修改
        </button>
        <button
          class="btn btn-submit"
          :disabled="loading || submitting"
          :loading="submitting"
          @click="submitPublish"
        >
          确认发布
        </button>
      </template>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { create as createProduct } from '@/api/product';
import { chooseAndUpload } from '@/utils/cos';
import ImageUploader from '@/components/ImageUploader.vue';

/** 步骤定义 */
const steps = ['上传图片', '填写信息', '确认发布'];
const currentStep = ref(0);
const loading = ref(false);
const submitting = ref(false);

/** ImageUploader 组件引用 */
const uploaderRef = ref(null);

/** 选中的临时文件 */
const tempFiles = ref([]);

/** 表单数据 */
const form = reactive({
  title: '',
  description: '',
  category: '',
  condition: '',
  original_price: '',
  price: '',
  trade_location: '',
  negotiable: true,
  images: [],       // COS URL 列表（上传完成后填充）
});

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

/**
 * 图片文件变更回调
 */
function onFilesChange(files) {
  tempFiles.value = files;
}

/**
 * 上一步
 */
function prevStep() {
  if (currentStep.value > 0) {
    currentStep.value--;
  }
}

/**
 * 下一步（含校验 + 上传）
 */
async function nextStep() {
  // Step 0 → 1: 检查图片 + 上传
  if (currentStep.value === 0) {
    if (tempFiles.value.length === 0) {
      uni.showToast({ title: '请至少上传一张图片', icon: 'none', duration: 1500 });
      return;
    }
    if (tempFiles.value.length > 6) {
      uni.showToast({ title: '最多上传 6 张图片', icon: 'none', duration: 1500 });
      return;
    }

    // 防重复点击
    if (loading.value) return;

    // 上传图片到 COS（传入 ImageUploader 已选文件，避免重复选图）
    loading.value = true;
    uni.showLoading({ title: '上传图片中...', mask: true });
    try {
      const urls = await chooseAndUpload(tempFiles.value);
      // hideLoading 必须在 showToast 之前（微信强制要求）
      uni.hideLoading();
      if (urls.length === 0) {
        uni.showToast({ title: '图片上传失败，请重试', icon: 'none', duration: 1500 });
        return;
      }
      form.images = urls;
      currentStep.value = 1;
    } catch (err) {
      uni.hideLoading();
      uni.showToast({ title: err.message || '上传失败，请重试', icon: 'none', duration: 2000 });
      return;
    } finally {
      loading.value = false;
    }
    return;
  }

  // Step 1 → 2: 校验表单字段
  if (currentStep.value === 1) {
    const err = validateForm();
    if (err) {
      uni.showToast({ title: err, icon: 'none', duration: 1500 });
      return;
    }
    currentStep.value = 2;
    return;
  }
}

/**
 * 表单字段校验
 * @returns {string|null} 错误信息
 */
function validateForm() {
  if (!form.title || !form.title.trim()) {
    return '请输入商品名称';
  }
  if (form.title.trim().length < 1) {
    return '商品名称至少 1 个字符';
  }
  if (!form.category) {
    return '请选择分类';
  }
  if (!form.condition) {
    return '请选择成色';
  }
  if (!form.original_price || parseFloat(form.original_price) < 0) {
    return '请输入有效的原价';
  }
  if (!form.price || parseFloat(form.price) <= 0) {
    return '请输入有效的售价';
  }
  if (parseFloat(form.price) > parseFloat(form.original_price)) {
    return '售价不能高于原价';
  }
  if (!form.trade_location || !form.trade_location.trim()) {
    return '请输入交易地点';
  }
  return null;
}

/**
 * 提交发布
 */
async function submitPublish() {
  submitting.value = true;
  try {
    await createProduct({
      title: form.title.trim(),
      description: form.description.trim() || undefined,
      category: form.category,
      condition: form.condition,
      original_price: parseFloat(form.original_price),
      price: parseFloat(form.price),
      trade_location: form.trade_location.trim(),
      negotiable: form.negotiable,
      images: form.images,
    });

    uni.showToast({ title: '发布成功', icon: 'success', duration: 1500 });
    setTimeout(() => {
      // 跳转到首页
      uni.switchTab({ url: '/pages/index/index' });
    }, 1200);
  } catch (err) {
    const msg = err.message || '发布失败，请稍后重试';
    if (msg.includes('信誉分')) {
      uni.showModal({
        title: '发布受限',
        content: msg,
        showCancel: false,
        confirmText: '知道了',
      });
    } else if (msg.includes('违规') || msg.includes('敏感')) {
      uni.showToast({ title: '内容包含违规信息，请修改后重试', icon: 'none', duration: 2000 });
    } else {
      uni.showToast({ title: msg, icon: 'none', duration: 2000 });
    }
  } finally {
    submitting.value = false;
  }
}

/**
 * 预览大图
 */
function previewImages(index) {
  uni.previewImage({
    current: form.images[index],
    urls: form.images,
  });
}

/**
 * 获取分类标签文本
 */
function getCategoryLabel(value) {
  const cat = categories.find((c) => c.value === value);
  return cat ? `${cat.icon} ${cat.label}` : value;
}
</script>

<style lang="scss">
@import '@/styles/tokens.scss';

.publish-page {
  min-height: 100vh;
  background: $color-bg;
  display: flex;
  flex-direction: column;
  padding-bottom: calc(128rpx + env(safe-area-inset-bottom));
}

// ── 进度条 ──────────────────────────────────────────────
.progress-bar {
  background: $color-surface;
  padding: 32rpx $space-page 24rpx;
}

.progress-steps {
  display: flex;
  align-items: center;
  justify-content: center;
}

.progress-step {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.step-dot {
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  background: #E0E0E0;
  color: $color-muted;
  font-size: $text-xs;
  font-weight: $weight-bold;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  .active & {
    background: $color-primary-gradient;
    color: $color-surface;
  }

  .done & {
    background: $color-success;
    color: $color-surface;
  }
}

.step-label {
  position: absolute;
  top: -32rpx;
  font-size: 20rpx;
  color: $color-muted;
  white-space: nowrap;

  .active & {
    color: $color-primary;
  }

  .done & {
    color: $color-success;
  }
}

.progress-step {
  position: relative;
  padding-top: 36rpx;
}

.step-line {
  width: 80rpx;
  height: 4rpx;
  background: #E0E0E0;
  margin: 0 8rpx;
  flex-shrink: 0;

  &.filled {
    background: $color-success;
  }
}

// ── 步骤内容区 ──────────────────────────────────────────
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

.step-hint {
  display: block;
  font-size: $text-sm;
  color: $color-muted;
  margin-bottom: $space-card;
}

// ── 表单 ────────────────────────────────────────────────
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

// ── 分类选择 ────────────────────────────────────────────
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

// ── 成色选择 ────────────────────────────────────────────
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

// ── 预览区（Step 3）──────────────────────────────────────
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

// ── 底部操作栏 ──────────────────────────────────────────
.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 16rpx $space-page calc(24rpx + env(safe-area-inset-bottom));
  background: $color-surface;
  display: flex;
  gap: 16rpx;
  box-shadow: 0 -2rpx 16rpx rgba(0, 0, 0, 0.04);
}

.btn {
  height: $btn-height-md;
  border-radius: $radius-card;
  border: none;
  font-size: $text-base;
  font-weight: $weight-medium;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;

  &::after {
    border: none;
  }
}

.btn-prev {
  background: $color-bg;
  color: $color-body;
  flex: 1;
}

.btn-next {
  background: $color-primary-gradient;
  color: $color-surface;
  flex: 1;
}

.btn-full {
  flex: 2;
}

.btn-submit {
  background: $color-primary-gradient;
  color: $color-surface;
  flex: 2;
  box-shadow: $shadow-button;
}

.btn[disabled] {
  opacity: $opacity-disabled;
}
</style>

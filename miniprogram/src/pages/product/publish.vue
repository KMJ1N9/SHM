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
          <view class="step-dot">{{ currentStep > i ? '✓' : i + 1 }}</view>
          <text class="step-label">{{ step }}</text>
          <view v-if="i < steps.length - 1" class="step-line" :class="{ filled: currentStep > i }" />
        </view>
      </view>
    </view>

    <!-- Step 0: 上传图片 -->
    <view v-if="currentStep === 0" class="step-body">
      <view class="step-title">上传商品图片</view>
      <text class="step-hint">清晰的图片能让商品更快卖出（1-6 张）</text>
      <ImageUploader ref="uploaderRef" :max-count="6" @update:files="onFilesChange" />
    </view>

    <!-- Step 1: 填写商品信息 -->
    <ProductForm
      v-if="currentStep === 1"
      :form="form"
      @update="onFormUpdate"
    />

    <!-- Step 2: 确认发布 -->
    <ProductPreview v-if="currentStep === 2" :form="form" />

    <!-- 底部操作栏 -->
    <view class="bottom-bar">
      <template v-if="currentStep < 2">
        <button v-if="currentStep > 0 && !isEditMode" class="btn btn-prev" @click="prevStep">
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

      <template v-if="currentStep === 2">
        <button class="btn btn-prev" :disabled="loading" @click="prevStep">返回修改</button>
        <button
          class="btn btn-submit"
          :disabled="loading || submitting"
          :loading="submitting"
          @click="submitPublish"
        >
          {{ isEditMode ? '保存修改' : '确认发布' }}
        </button>
      </template>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, computed } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import { create as createProduct, update as updateProduct, detail as getDetail } from '@/api/product';
import { chooseAndUpload } from '@/utils/cos';
import { useUserStore } from '@/store/user';
import { useAppStore } from '@/store/app';
import ImageUploader from '@/components/ImageUploader.vue';
import ProductForm from './components/ProductForm.vue';
import ProductPreview from './components/ProductPreview.vue';

const userStore = useUserStore();
const appStore = useAppStore();

/** 编辑模式 */
const editId = ref(null);
const isEditMode = computed(() => editId.value !== null);

/** 步骤定义 */
const steps = ['上传图片', '填写信息', '确认发布'];
const currentStep = ref(0);
const loading = ref(false);
const submitting = ref(false);

const uploaderRef = ref(null);
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
  images: [],
});

/** ProductForm 数据变更回调 → 同步到父级 form */
function onFormUpdate(data) {
  Object.assign(form, data);
}

function onFilesChange(files) {
  tempFiles.value = files;
}

/** 重置表单到创建模式 */
function resetForm() {
  editId.value = null;
  currentStep.value = 0;
  tempFiles.value = [];
  Object.assign(form, {
    title: '', description: '', category: '', condition: '',
    original_price: '', price: '', trade_location: '',
    negotiable: true, images: [],
  });
  if (uploaderRef.value) {
    uploaderRef.value.clearAll();
  }
}

onLoad((options) => {
  if (options && options.id) {
    editId.value = parseInt(options.id, 10);
      uni.setNavigationBarTitle({ title: '编辑商品' });
    loadExistingProduct(editId.value);
  }
});

onShow(() => {
  const pendingId = appStore.consumePendingEditProductId();
  if (pendingId !== null && pendingId !== undefined) {
      resetForm();
    editId.value = pendingId;
      uni.setNavigationBarTitle({ title: '编辑商品' });
    loadExistingProduct(pendingId);
  } else if (editId.value) {
      resetForm();
      uni.setNavigationBarTitle({ title: '发布商品' });
  }
});

async function loadExistingProduct(id) {
  uni.showLoading({ title: '加载中...', mask: true });
  try {
    const product = await getDetail(id);
    if (product.seller_id !== userStore.user?.id) {
      uni.hideLoading();
      uni.showToast({ title: '无权编辑此商品', icon: 'none', duration: 1500 });
      setTimeout(() => uni.switchTab({ url: '/pages/index/index' }), 800);
      return;
    }
    if (!['active', 'off_shelf'].includes(product.status)) {
      uni.hideLoading();
      uni.showToast({ title: '该状态商品不可编辑', icon: 'none', duration: 1500 });
      setTimeout(() => uni.switchTab({ url: '/pages/index/index' }), 800);
      return;
    }
    Object.assign(form, {
      title: product.title || '',
      category: product.category || '',
      condition: product.condition || '',
      original_price: product.original_price != null ? String(product.original_price) : '',
      price: product.price != null ? String(product.price) : '',
      trade_location: product.trade_location || '',
      negotiable: product.negotiable !== false,
      description: product.description || '',
      images: product.images || [],
    });
    currentStep.value = 1;
    uni.hideLoading();
  } catch (err) {
    uni.hideLoading();
    uni.showToast({ title: err.message || '加载失败', icon: 'none', duration: 1500 });
    setTimeout(() => uni.switchTab({ url: '/pages/index/index' }), 800);
  }
}

function prevStep() {
  if (currentStep.value > 0) {
    currentStep.value--;
  }
}

async function nextStep() {
  if (currentStep.value === 0) {
    if (tempFiles.value.length === 0) {
      uni.showToast({ title: '请至少上传一张图片', icon: 'none', duration: 1500 });
      return;
    }
    if (tempFiles.value.length > 6) {
      uni.showToast({ title: '最多上传 6 张图片', icon: 'none', duration: 1500 });
      return;
    }
    if (loading.value) return;
    loading.value = true;
    uni.showLoading({ title: '上传图片中...', mask: true });
    try {
      const urls = await chooseAndUpload(tempFiles.value);
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

function validateForm() {
  if (!form.title || !form.title.trim()) return '请输入商品名称';
  if (form.title.trim().length < 1) return '商品名称至少 1 个字符';
  if (!form.category) return '请选择分类';
  if (!form.condition) return '请选择成色';
  if (!form.original_price || parseFloat(form.original_price) < 0) return '请输入有效的原价';
  if (!form.price || parseFloat(form.price) <= 0) return '请输入有效的售价';
  if (parseFloat(form.price) > parseFloat(form.original_price)) return '售价不能高于原价';
  if (!form.trade_location || !form.trade_location.trim()) return '请输入交易地点';
  return null;
}

async function submitPublish() {
  if (!userStore.canPublish) {
    uni.showModal({
      title: '信誉分不足',
      content: '你的信誉分低于 60，无法发布商品。请通过完成交易来恢复信誉分。',
      showCancel: false,
      confirmText: '我知道了',
    });
    return;
  }

  submitting.value = true;
  try {
    const payload = {
      title: form.title.trim(),
      description: form.description.trim() || undefined,
      category: form.category,
      condition: form.condition,
      original_price: parseFloat(form.original_price),
      price: parseFloat(form.price),
      trade_location: form.trade_location.trim(),
      negotiable: form.negotiable,
      images: form.images,
    };

    if (isEditMode.value) {
      await updateProduct(editId.value, payload);
      uni.showToast({ title: '保存成功', icon: 'success', duration: 1500 });
    } else {
      await createProduct(payload);
      uni.showToast({ title: '发布成功', icon: 'success', duration: 1500 });
    }

    setTimeout(() => {
      const wasEdit = isEditMode.value;
      const targetId = editId.value;
      resetForm();
      uni.setNavigationBarTitle({ title: '发布商品' });
      if (wasEdit) {
        uni.navigateTo({ url: `/pages/product/detail?id=${targetId}` });
      } else {
        uni.switchTab({ url: '/pages/index/index' });
      }
    }, 800);
  } catch (err) {
    const msg = err.message || '发布失败，请稍后重试';
    if (msg.includes('信誉分')) {
      uni.showModal({ title: '发布受限', content: msg, showCancel: false, confirmText: '知道了' });
    } else if (msg.includes('违规') || msg.includes('敏感')) {
      uni.showToast({ title: '内容包含违规信息，请修改后重试', icon: 'none', duration: 2000 });
    } else {
      uni.showToast({ title: msg, icon: 'none', duration: 2000 });
    }
  } finally {
    submitting.value = false;
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.publish-page {
  min-height: 100vh;
  background: $color-bg;
  display: flex;
  flex-direction: column;
  padding-bottom: calc(128rpx + env(safe-area-inset-bottom));
}

// ── 进度条 ──
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
  position: relative;
  padding-top: 36rpx;
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

// ── 步骤内容区 ──
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

// ── 底部操作栏 ──
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

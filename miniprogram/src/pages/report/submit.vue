<template>
  <view class="report-submit-page">
    <!-- ============================================================ -->
    <!-- 举报类型选择 -->
    <!-- ============================================================ -->
    <view class="section-card">
      <text class="section-title">
        举报类型 <text class="required">*</text>
      </text>
      <view class="type-grid">
        <view
          v-for="t in reportTypes"
          :key="t.value"
          :class="['type-item', form.type === t.value ? 'type-item--selected' : '']"
          @click="form.type = t.value"
        >
          <text class="type-icon">{{ t.icon }}</text>
          <text class="type-label">{{ t.label }}</text>
        </view>
      </view>
    </view>

    <!-- ============================================================ -->
    <!-- 关联对象（自动带入，不可编辑） -->
    <!-- ============================================================ -->
    <view class="section-card">
      <text class="section-title">关联对象</text>

      <view v-if="productTitleLoading" class="info-row">
        <text class="info-label">关联商品</text>
        <text class="info-value info-value--loading">加载中...</text>
      </view>

      <view v-else-if="productTitle" class="info-row">
        <text class="info-label">关联商品</text>
        <text class="info-value">{{ productTitle }}</text>
      </view>

      <view v-if="orderId" class="info-row">
        <text class="info-label">关联订单</text>
        <text class="info-value">#{{ orderId }}</text>
      </view>

      <view class="info-row">
        <text class="info-label">被举报人 ID</text>
        <text class="info-value">{{ reportedUserId }}</text>
      </view>
    </view>

    <!-- ============================================================ -->
    <!-- 问题描述 -->
    <!-- ============================================================ -->
    <view class="section-card">
      <text class="section-title">
        问题描述 <text class="required">*</text>
      </text>
      <textarea
        v-model="form.description"
        class="desc-textarea"
        placeholder="请详细描述遇到的问题，包括时间、地点、沟通经过等，以便客服准确判断…"
        :maxlength="1000"
        :auto-height="true"
      />
      <view class="char-count">
        <text :class="form.description.length < 10 ? 'char-count--warn' : ''">
          {{ form.description.length }}
        </text>
        /1000（至少 10 字）
      </view>
    </view>

    <!-- ============================================================ -->
    <!-- 上传证据截图 -->
    <!-- ============================================================ -->
    <view class="section-card">
      <text class="section-title">上传证据截图（选填）</text>
      <text class="section-hint">清晰的截图有助于客服更快处理（最多 6 张）</text>
      <ImageUploader
        ref="uploaderRef"
        :max-count="6"
        @update:files="onFilesChange"
      />
    </view>

    <!-- ============================================================ -->
    <!-- 提示文字 -->
    <!-- ============================================================ -->
    <view class="notice-text">
      <text>提交后客服将在 3 小时内处理，请留意通知</text>
    </view>

    <!-- ============================================================ -->
    <!-- 提交按钮 -->
    <!-- ============================================================ -->
    <view class="bottom-bar">
      <button
        class="btn-submit"
        :disabled="!canSubmit || submitting"
        :loading="submitting"
        @click="handleSubmit"
      >
        提交举报
      </button>
    </view>
  </view>
</template>

<script setup>
/**
 * 举报表单页 — 选择举报类型 + 填写描述 + 上传证据 → 提交工单
 *
 * 入口来源：
 *   - 商品详情页 → ?product_id=X&reported_user_id=Y
 *   - 订单详情页 → ?order_id=X&product_id=Y&reported_user_id=Z
 *   - 聊天详情页 → ?reported_user_id=Y
 */
import { ref, reactive, computed } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { createReport } from '@/api/report';
import { detail as getProductDetail } from '@/api/product';
import { chooseAndUpload } from '@/utils/cos';
import ImageUploader from '@/components/ImageUploader.vue';

// ============================================================
// 举报类型配置
// ============================================================
const reportTypes = [
  { value: '描述不符', label: '商品与描述严重不符', icon: '📋' },
  { value: '辱骂骚扰', label: '对方辱骂/骚扰', icon: '💬' },
  { value: '疑似骗子', label: '疑似骗子', icon: '⚠️' },
  { value: '其他', label: '其他', icon: '📌' },
];

// ============================================================
// 页面参数
// ============================================================
const reportedUserId = ref(0);
const productId = ref(0);
const orderId = ref(0);
const productTitle = ref('');
const productTitleLoading = ref(false);

// ============================================================
// 表单数据
// ============================================================
const form = reactive({
  type: '',
  description: '',
});

const uploaderRef = ref(null);
const tempFiles = ref([]);
const submitting = ref(false);

// ============================================================
// 表单校验
// ============================================================
const canSubmit = computed(() => {
  return form.type && form.description.length >= 10 && !submitting.value;
});

// ============================================================
// ImageUploader 回调 — 缓存已选文件列表
// ============================================================
function onFilesChange(files) {
  tempFiles.value = files;
}

// ============================================================
// 提交举报
// ============================================================
async function handleSubmit() {
  if (!canSubmit.value) return;
  if (submitting.value) return;

  // 校验
  if (!form.type) {
    uni.showToast({ title: '请选择举报类型', icon: 'none' });
    return;
  }
  if (form.description.length < 10) {
    uni.showToast({ title: '请至少输入 10 个字描述问题', icon: 'none' });
    return;
  }

  submitting.value = true;
  uni.showLoading({ title: '提交中...', mask: true });

  try {
    // 上传证据图片到 COS
    let evidenceImages = [];
    if (tempFiles.value.length > 0) {
      evidenceImages = await chooseAndUpload(tempFiles.value);
    }

    await createReport({
      reported_user_id: reportedUserId.value,
      product_id: productId.value || undefined,
      order_id: orderId.value || undefined,
      type: form.type,
      description: form.description,
      evidence_images: evidenceImages,
    });

    uni.hideLoading();
    uni.showToast({ title: '举报已提交，客服将在 3 小时内处理', icon: 'success', duration: 2000 });
    setTimeout(() => {
      uni.navigateBack();
    }, 2000);
  } catch (err) {
    uni.hideLoading();
    // 3006: 已有进行中的举报
    if (err.code === 3006) {
      uni.showToast({ title: '你已对该订单提交过举报，请等待处理', icon: 'none', duration: 2500 });
    } else {
      uni.showToast({ title: err.message || '提交失败，请稍后重试', icon: 'none' });
    }
  } finally {
    submitting.value = false;
  }
}

// ============================================================
// 生命周期 — 解析参数 + 加载关联信息
// ============================================================
onLoad((options) => {
  const rUserId = parseInt(options.reported_user_id, 10);
  if (!rUserId) {
    uni.showToast({ title: '缺少被举报人信息', icon: 'none' });
    setTimeout(() => uni.navigateBack(), 1500);
    return;
  }
  reportedUserId.value = rUserId;

  if (options.product_id) {
    productId.value = parseInt(options.product_id, 10);
    // 异步加载商品标题用于展示
    productTitleLoading.value = true;
    getProductDetail(productId.value)
      .then((product) => {
        productTitle.value = product.title || '';
      })
      .catch(() => {
        // 加载失败不阻塞，仅不显示商品标题
      })
      .finally(() => {
        productTitleLoading.value = false;
      });
  }

  if (options.order_id) {
    orderId.value = parseInt(options.order_id, 10);
  }
});
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.report-submit-page {
  min-height: 100vh;
  background: $color-bg;
  padding-bottom: calc(120rpx + $safe-area-bottom);
}

// ── 分区卡片 ──
.section-card {
  background: $color-surface;
  margin: $space-card $space-page 0;
  border-radius: $radius-card;
  padding: $space-card;
}

.section-title {
  font-size: $text-base;
  font-weight: $weight-bold;
  color: $color-title;
  display: block;
  margin-bottom: $space-content;
}

.section-hint {
  font-size: $text-sm;
  color: $color-muted;
  display: block;
  margin-bottom: $space-content;
}

.required {
  color: $color-error;
}

// ── 举报类型网格 ──
.type-grid {
  display: flex;
  flex-wrap: wrap;
  gap: $space-content;
}

.type-item {
  width: calc(50% - 8rpx);
  padding: $space-card;
  border-radius: $radius-card;
  background: $color-bg;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  border: 2rpx solid transparent;
  transition: border-color 0.2s;

  &--selected {
    border-color: $color-primary;
    background: $color-primary-light;
  }
}

.type-icon {
  font-size: 40rpx;
}

.type-label {
  font-size: $text-sm;
  color: $color-body;
}

// ── 关联信息行 ──
.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: $space-content 0;

  & + & {
    border-top: 1rpx solid $color-divider;
  }
}

.info-label {
  font-size: $text-sm;
  color: $color-muted;
}

.info-value {
  font-size: $text-sm;
  color: $color-title;
  font-weight: $weight-medium;

  &--loading {
    color: $color-muted;
    font-weight: $weight-regular;
  }
}

// ── 问题描述 ──
.desc-textarea {
  width: 100%;
  min-height: 200rpx;
  font-size: $text-base;
  color: $color-title;
  line-height: $line-height;
  padding: $space-content;
  background: $color-bg;
  border-radius: $radius-card;
  box-sizing: border-box;
}

.char-count {
  text-align: right;
  font-size: $text-xs;
  color: $color-muted;
  margin-top: 8rpx;

  &--warn {
    color: $color-warning;
  }
}

// ── 提示文字 ──
.notice-text {
  padding: $space-card $space-page;
  text-align: center;
  font-size: $text-xs;
  color: $color-muted;
}

// ── 底部提交栏 ──
.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: $space-card $space-page calc($space-card + $safe-area-bottom);
  background: $color-surface;
  box-shadow: 0 -2rpx 8rpx rgba(0, 0, 0, 0.04);
}

.btn-submit {
  width: 100%;
  height: $btn-height-lg;
  line-height: $btn-height-lg;
  background: $color-primary-gradient;
  color: #fff;
  font-size: $text-base;
  font-weight: $weight-bold;
  border-radius: $radius-card;
  border: none;
  text-align: center;

  &[disabled] {
    opacity: $opacity-disabled;
  }
}
</style>

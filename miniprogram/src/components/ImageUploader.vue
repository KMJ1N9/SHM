<template>
  <view class="image-uploader">
    <view class="upload-grid">
      <!-- 已选图片缩略图 -->
      <view
        v-for="(file, index) in files"
        :key="index"
        class="upload-item"
        @click="previewImage(index)"
      >
        <image
          class="upload-thumb"
          :src="file.path || file"
          mode="aspectFill"
        />
        <!-- 删除按钮 -->
        <view class="upload-remove" @click.stop="removeImage(index)">
          <text class="upload-remove-icon">
            ✕
          </text>
        </view>
        <!-- 上传中遮罩 -->
        <view v-if="file.uploading" class="upload-mask">
          <text class="upload-progress">
            {{ file.progress || 0 }}%
          </text>
        </view>
      </view>

      <!-- 添加按钮（未达上限时显示） -->
      <view
        v-if="files.length < maxCount"
        class="upload-item upload-add"
        @click="handleChoose"
      >
        <text class="upload-add-icon">
          +
        </text>
        <text class="upload-add-text">
          {{ files.length === 0 ? '上传图片' : `${files.length}/${maxCount}` }}
        </text>
      </view>
    </view>

    <!-- 提示文字 -->
    <text class="upload-tip">
      支持 JPG/PNG/WebP 格式，单张不超过 5MB，最多 {{ maxCount }} 张
    </text>
  </view>
</template>

<script setup>
import { ref } from 'vue';

/**
 * ImageUploader — 图片选择与预览组件
 *
 * 职责：选择图片 → 展示缩略图 → 允许删除 → 暴露文件列表。
 * 实际上传由调用方（发布页）调用 cos.js 的 chooseAndUpload 完成。
 *
 * Props:
 *   modelValue: string[]  — 已上传的 COS URL 列表（编辑模式回显）
 *   maxCount:   number    — 最大图片数（默认 6）
 *
 * Emits:
 *   update:files — 选中文件列表变更时触发（File[] 格式）
 */

const props = defineProps({
  modelValue: {
    type: Array,
    default: () => [],
  },
  maxCount: {
    type: Number,
    default: 6,
  },
});

const emit = defineEmits(['update:files']);

/** 已选择的临时文件列表 */
const files = ref([]);

/** 允许的 MIME 类型列表（含 image/jpg——微信 Android 端对 .jpg 返回的非标准类型） */
const ALLOWED_TYPES = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp'];
/** 允许的扩展名（用于 MIME 类型不可靠时的回退校验） */
const ALLOWED_EXTENSIONS = ['.jpg', '.jpeg', '.png', '.webp'];
const MAX_SIZE = 5 * 1024 * 1024; // 5MB

/**
 * 判断文件是否合法（先查 MIME 类型，再降级查扩展名——微信各平台返回的 type 不一致）
 * @param {Object} file - uni.chooseImage 返回的 tempFile
 * @returns {boolean}
 */
function isValidImage(file) {
  if (ALLOWED_TYPES.includes(file.type)) return true;
  // MIME 类型不匹配时降级检查文件扩展名（如 Android 端 .jpg 可能返回 image/jpg 甚至空字符串）
  const ext = (file.path || file.name || '').split('.').pop()?.toLowerCase();
  if (ext && ALLOWED_EXTENSIONS.includes('.' + ext)) return true;
  return false;
}

/**
 * 选择图片
 */
function handleChoose() {
  const remaining = props.maxCount - files.value.length;
  if (remaining <= 0) return;

  uni.chooseImage({
    count: remaining,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: (res) => {
      // 过滤不支持的文件（格式和大小），而非全量拒绝
      const valid = res.tempFiles.filter(
        (f) => isValidImage(f) && f.size <= MAX_SIZE
      );
      const rejected = res.tempFiles.length - valid.length;
      if (rejected > 0) {
        uni.showToast({
          title: `已自动过滤 ${rejected} 张不支持的图片`,
          icon: 'none',
          duration: 2000,
        });
      }
      if (valid.length === 0) return;
      files.value = [...files.value, ...valid];
      emit('update:files', files.value);
    },
    fail: (err) => {
      if (err.errMsg && !err.errMsg.includes('cancel')) {
        uni.showToast({ title: '选择图片失败', icon: 'none', duration: 1500 });
      }
    },
  });
}

/**
 * 预览图片
 */
function previewImage(index) {
  const urls = files.value.map((f) => f.path || f);
  uni.previewImage({
    current: urls[index],
    urls,
  });
}

/**
 * 删除图片
 */
function removeImage(index) {
  files.value.splice(index, 1);
  emit('update:files', files.value);
}

/**
 * 清空所有图片（供外部调用）
 */
function clearAll() {
  files.value = [];
  emit('update:files', []);
}

/**
 * 设置上传进度（供外部调用）
 */
function setProgress(index, progress) {
  if (files.value[index]) {
    files.value[index].uploading = progress < 100;
    files.value[index].progress = progress;
  }
}

defineExpose({ files, clearAll, setProgress });
</script>

<style lang="scss">
@import '@/styles/tokens.scss';

.image-uploader {
  width: 100%;
}

.upload-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.upload-item {
  position: relative;
  width: calc((100% - 32rpx) / 3);
  aspect-ratio: 1;
  border-radius: $radius-card;
  overflow: hidden;
  background: $color-divider;
}

.upload-thumb {
  width: 100%;
  height: 100%;
}

.upload-remove {
  position: absolute;
  top: 0;
  right: 0;
  width: 44rpx;
  height: 44rpx;
  background: rgba(0, 0, 0, 0.55);
  border-radius: 0 $radius-card 0 $radius-card;
  display: flex;
  align-items: center;
  justify-content: center;
}

.upload-remove-icon {
  color: #FFFFFF;
  font-size: $text-xs;
  line-height: 1;
}

// 上传中遮罩
.upload-mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
}

.upload-progress {
  color: #FFFFFF;
  font-size: $text-lg;
  font-weight: $weight-bold;
}

// 添加按钮
.upload-add {
  border: 2rpx dashed #CCCCCC;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
}

.upload-add-icon {
  font-size: 64rpx;
  color: #CCCCCC;
  line-height: 1;
}

.upload-add-text {
  font-size: $text-xs;
  color: $color-muted;
}

// 提示文字
.upload-tip {
  display: block;
  margin-top: $space-content;
  font-size: $text-xs;
  color: $color-muted;
  text-align: center;
}
</style>

<template>
  <image
    class="safe-image"
    :src="displaySrc"
    :mode="mode"
    :lazy-load="lazyLoad"
    :show-menu-by-longpress="showMenuByLongpress"
    :style="customStyle"
    @click="$emit('click', $event)"
    @load="$emit('load', $event)"
    @error="onError"
  />
</template>

<script setup>
/**
 * SafeImage — 安全的图片组件
 *
 * 微信小程序真机上 <image> 组件可能阻止 HTTP 图片加载（即使 urlCheck: false），
 * 本组件自动将 HTTP URL 通过 uni.downloadFile 下载到本地临时文件后显示。
 *
 * Props:
 *   src        — 图片 URL（支持 http/https/data URI/本地路径）
 *   mode       — 同 <image> mode
 *   lazyLoad   — 同 <image> lazy-load
 *   placeholder — 加载失败时的占位图
 *
 * Events:
 *   click / load / error — 透传 <image> 事件
 */

import { ref, watch, onMounted } from 'vue';

const props = defineProps({
  src: { type: String, default: '' },
  mode: { type: String, default: 'aspectFill' },
  lazyLoad: { type: Boolean, default: false },
  showMenuByLongpress: { type: Boolean, default: false },
  placeholder: { type: String, default: '' },
  customStyle: { type: String, default: '' },
});

const emit = defineEmits(['click', 'load', 'error']);

const displaySrc = ref('');
const errorCount = ref(0);

/**
 * 判断是否为 HTTP（非 HTTPS）URL
 */
function isHttpUrl(url) {
  return /^http:\/\//.test(url);
}

/**
 * 下载 HTTP 图片到本地临时文件
 * @param {string} url
 * @returns {Promise<string>} 本地临时文件路径
 */
function downloadImage(url) {
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url,
      success: (res) => {
        if (res.statusCode === 200) {
          resolve(res.tempFilePath);
        } else {
          reject(new Error(`HTTP ${res.statusCode}`));
        }
      },
      fail: (err) => reject(err),
    });
  });
}

/**
 * 加载图片：HTTP URL → 下载到本地；其他 URL → 直接使用
 */
async function loadImage(url) {
  if (!url) {
    displaySrc.value = props.placeholder || '';
    return;
  }

  // data URI、本地路径、HTTPS — 直接使用
  if (!isHttpUrl(url)) {
    displaySrc.value = url;
    return;
  }

  // HTTP URL — 需要下载到本地临时文件
  try {
    const tempPath = await downloadImage(url);
    displaySrc.value = tempPath;
  } catch (err) {
    console.warn('[SafeImage] 下载失败:', url, err.message || err);
    // 兜底：尝试直接加载（DevTools 模拟器可能支持 HTTP）
    if (errorCount.value === 0) {
      displaySrc.value = url;
    } else {
      displaySrc.value = props.placeholder || '';
    }
  }
}

function onError(e) {
  errorCount.value += 1;
  // 如果直接加载 HTTP URL 失败（且之前没尝试过占位图），回退到占位图
  if (displaySrc.value && isHttpUrl(displaySrc.value) && props.placeholder) {
    displaySrc.value = props.placeholder;
  }
  emit('error', e);
}

// 监听 src 变化
watch(() => props.src, (newVal) => {
  errorCount.value = 0;
  loadImage(newVal);
});

onMounted(() => {
  loadImage(props.src);
});
</script>

<style lang="scss" scoped>
.safe-image {
  width: 100%;
  height: 100%;
}
</style>

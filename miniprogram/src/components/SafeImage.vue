<template>
  <image
    class="safe-image"
    :src="displaySrc"
    :mode="mode"
    :lazy-load="lazyLoad"
    :show-menu-by-longpress="showMenuByLongpress"
    :style="customStyle"
    @click="$emit('click', $event)"
    @load="onLoad"
    @error="onError"
  />
</template>

<script setup>
/**
 * SafeImage — 安全的图片组件（下载队列版）
 *
 * 微信小程序真机上 <image> 可能阻止 HTTP 图片加载，且 uni.downloadFile
 * 有 ~10 并发限制。本组件使用模块级下载队列（最多 5 并发）统一处理所有
 * HTTP 图片，避免并发超限问题。
 *
 * 策略：
 *   1. HTTP URL → 走全局下载队列 (downloadFile) → 本地临时文件
 *   2. 非 HTTP URL（本地路径/data URI/HTTPS）→ 直接使用
 *   3. 下载失败 → placeholder
 *
 * Props:
 *   src         — 图片 URL
 *   mode        — 同 <image> mode
 *   lazyLoad    — 同 <image> lazy-load（控制 image 组件的懒加载行为）
 *   placeholder — 加载失败时的占位图
 */

import { ref, watch, onMounted, onBeforeUnmount } from 'vue';
import { downloadImage } from '@/utils/download-queue';

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
let activeDownload = null;
let lastRawUrl = '';

function isHttpUrl(url) {
  return /^https?:\/\//.test(url);
}

function loadImage(url) {
  if (!url) {
    displaySrc.value = props.placeholder || '';
    activeDownload = null;
    return;
  }

  // 非网络 URL → 直接使用
  if (!isHttpUrl(url)) {
    displaySrc.value = url;
    activeDownload = null;
    return;
  }

  // 网络 URL → 走全局下载队列（自动控制并发 ≤ 5）
  const thisDownload = {};
  activeDownload = thisDownload;

  downloadImage(url, 15000)
    .then((tempPath) => {
      if (activeDownload !== thisDownload) return;
      displaySrc.value = tempPath;
      activeDownload = null;
    })
    .catch((err) => {
      if (activeDownload !== thisDownload) return;
      activeDownload = null;
      console.warn('[SafeImage] 下载失败:', url.substring(0, 80), err.message || err);
      displaySrc.value = props.placeholder || '';
    });
}

function onLoad(e) {
  errorCount.value = 0;
  emit('load', e);
}

function onError(e) {
  errorCount.value += 1;
  // 如果当前显示的是 HTTP URL 且加载失败 → 清空让 CSS 背景透出
  if (displaySrc.value && isHttpUrl(displaySrc.value)) {
    displaySrc.value = props.placeholder || '';
  }
  emit('error', e);
}

onBeforeUnmount(() => {
  // 组件销毁时，阻止 pending 下载的回调影响已卸载组件
  activeDownload = null;
});

watch(
  () => props.src,
  (newVal) => {
    if (newVal === lastRawUrl && errorCount.value === 0) return;
    lastRawUrl = newVal;
    errorCount.value = 0;
    loadImage(newVal);
  }
);

onMounted(() => {
  lastRawUrl = props.src;
  loadImage(props.src);
});
</script>

<style lang="scss" scoped>
.safe-image {
  width: 100%;
  height: 100%;
}
</style>

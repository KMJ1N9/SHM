<template>
  <view class="image-swiper">
    <swiper
      class="swiper"
      :indicator-dots="images && images.length > 1"
      indicator-color="rgba(255,255,255,0.5)"
      indicator-active-color="#FFFFFF"
      :autoplay="false"
      circular
    >
      <swiper-item v-for="(url, i) in images" :key="i">
        <SafeImage
          class="swiper-image"
          :src="resolveUrl(url)"
          mode="aspectFill"
          @click="previewImages(i)"
        />
      </swiper-item>
    </swiper>
    <!-- 图片计数 -->
    <view v-if="images && images.length > 1" class="image-count">
      <text>{{ images.length }} 张图片</text>
    </view>
  </view>
</template>

<script setup>
import SafeImage from '@/components/SafeImage.vue';

const props = defineProps({
  images: { type: Array, default: () => [] },
  resolveUrl: { type: Function, required: true },
});

function previewImages(index) {
  if (props.images && props.images.length > 0) {
    const resolved = props.images.map(props.resolveUrl);
    uni.previewImage({
      current: resolved[index],
      urls: resolved,
    });
  }
}
</script>

<style lang="scss" scoped>
.image-swiper {
  position: relative;
  background: #000000;
}

.swiper {
  width: 100%;
  height: 660rpx;
}

.swiper-image {
  width: 100%;
  height: 100%;
}

.image-count {
  position: absolute;
  bottom: 16rpx;
  right: 16rpx;
  padding: 4rpx 16rpx;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 9999rpx;
}

.image-count text {
  font-size: 24rpx;
  color: #FFFFFF;
}
</style>

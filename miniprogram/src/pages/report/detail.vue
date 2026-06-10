<template>
  <view class="report-detail-page">
    <!-- ============================================================ -->
    <!-- 加载 / 错误状态 -->
    <!-- ============================================================ -->
    <view v-if="loading" class="status-center">
      <text class="status-text">加载中...</text>
    </view>

    <view v-else-if="errorMsg" class="status-center">
      <text class="status-emoji">😵</text>
      <text class="status-text">{{ errorMsg }}</text>
      <button class="btn-retry" @click="loadReport">重试</button>
    </view>

    <template v-else-if="report">
      <!-- ============================================================ -->
      <!-- 处理进度 — 3 节点时间线 -->
      <!-- ============================================================ -->
      <view class="section-card">
        <text class="section-title">处理进度</text>
        <view class="timeline">
          <!-- 节点 1: 已提交 -->
          <view class="tl-node" :class="{ 'tl-node--active': timelineActive(0) }">
            <view class="tl-dot" />
            <view class="tl-content">
              <text class="tl-label">已提交</text>
              <text class="tl-time">{{ formatTime(report.created_at) }}</text>
            </view>
          </view>

          <!-- 节点 2: 客服处理中 -->
          <view class="tl-node" :class="{ 'tl-node--active': timelineActive(1) }">
            <view class="tl-dot" />
            <view class="tl-content">
              <text class="tl-label">客服处理中</text>
              <text v-if="timelineActive(1) && !timelineActive(2)" class="tl-time tl-time--pending">
                请耐心等待...
              </text>
            </view>
          </view>

          <!-- 节点 3: 已处理 -->
          <view class="tl-node" :class="{ 'tl-node--active': timelineActive(2) }">
            <view class="tl-dot" />
            <view class="tl-content">
              <text class="tl-label">已处理</text>
              <text v-if="timelineActive(2)" class="tl-time">
                {{ formatTime(report.resolved_at) }}
              </text>
              <text v-else class="tl-time tl-time--pending">待完成</text>
            </view>
          </view>
        </view>
      </view>

      <!-- ============================================================ -->
      <!-- 举报内容 -->
      <!-- ============================================================ -->
      <view class="section-card">
        <text class="section-title">举报内容</text>

        <view class="info-row">
          <text class="info-label">举报类型</text>
          <text class="info-value type-tag">{{ report.type }}</text>
        </view>

        <view v-if="report.product_id" class="info-row">
          <text class="info-label">关联商品</text>
          <text class="info-value info-value--link" @click="goProduct">
            查看商品详情 →
          </text>
        </view>

        <view v-if="report.order_id" class="info-row">
          <text class="info-label">关联订单</text>
          <text class="info-value info-value--link" @click="goOrder">
            #{{ report.order_id }} →
          </text>
        </view>

        <view class="info-row">
          <text class="info-label">举报人</text>
          <text class="info-value">{{ report.reporter_nickname || '用户' + report.reporter_id }}</text>
        </view>

        <view class="info-row">
          <text class="info-label">被举报人</text>
          <text class="info-value">{{ report.reported_nickname || '用户' + report.reported_user_id }}</text>
        </view>

        <!-- 问题描述 -->
        <view class="desc-block">
          <text class="desc-label">问题描述</text>
          <text class="desc-text">{{ report.description }}</text>
        </view>

        <!-- 证据截图 -->
        <view v-if="evidenceImages.length > 0" class="evidence-block">
          <text class="desc-label">证据截图</text>
          <view class="evidence-grid">
            <image
              v-for="(url, i) in evidenceImages"
              :key="i"
              class="evidence-image"
              :src="resolveImageUrl(url)"
              mode="aspectFill"
              @click="previewEvidence(i)"
            />
          </view>
        </view>
      </view>

      <!-- ============================================================ -->
      <!-- 处理结果 -->
      <!-- ============================================================ -->
      <view class="section-card">
        <text class="section-title">处理结果</text>

        <view v-if="report.status === 'pending'" class="result-placeholder">
          <text class="result-placeholder-text">等待客服受理中，请耐心等待...</text>
        </view>

        <view v-else-if="report.status === 'processing'" class="result-placeholder">
          <text class="result-placeholder-text">客服正在处理中，请耐心等待...</text>
        </view>

        <view v-else-if="report.status === 'resolved'" class="result-block">
          <text class="result-label">处理结论</text>
          <text class="result-text">{{ report.resolution || '处理完成' }}</text>
          <text class="result-time">处理时间：{{ formatTime(report.resolved_at) }}</text>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
/**
 * 举报详情页 — 显示举报内容 + 3 节点处理时间线 + 处理结果
 *
 * 状态时间线：
 *   节点 1 (已提交)    — 始终激活
 *   节点 2 (客服处理中) — status ∈ {processing, resolved} 时激活
 *   节点 3 (已处理)    — status === 'resolved' 时激活
 */
import { ref, computed } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import { getReportDetail } from '@/api/report';
import { resolveImageUrl } from '@/api/index';

// ============================================================
// 数据状态
// ============================================================
const report = ref(null);
const loading = ref(true);
const errorMsg = ref('');
let reportId = 0;

// ============================================================
// 证据图片列表（repository 层已统一解析为数组）
// ============================================================
const evidenceImages = computed(() => {
  if (!report.value) return [];
  return Array.isArray(report.value.evidence_images) ? report.value.evidence_images : [];
});

// ============================================================
// 时间线激活判断
// ============================================================
function timelineActive(nodeIndex) {
  if (!report.value) return false;
  const status = report.value.status;
  if (nodeIndex === 0) return true; // 已提交 — 始终激活
  if (nodeIndex === 1) return status === 'processing' || status === 'resolved';
  if (nodeIndex === 2) return status === 'resolved';
  return false;
}

// ============================================================
// 格式化时间
// ============================================================
function formatTime(isoString) {
  if (!isoString) return '';
  const d = new Date(isoString);
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  const hour = String(d.getHours()).padStart(2, '0');
  const min = String(d.getMinutes()).padStart(2, '0');
  return `${year}-${month}-${day} ${hour}:${min}`;
}

// ============================================================
// 数据加载
// ============================================================
async function loadReport() {
  if (!reportId) return;
  loading.value = true;
  errorMsg.value = '';
  try {
    report.value = await getReportDetail(reportId);
  } catch (err) {
    errorMsg.value = err.message || '加载失败';
    report.value = null;
  } finally {
    loading.value = false;
  }
}

// ============================================================
// 跳转关联对象
// ============================================================
function goProduct() {
  if (report.value?.product_id) {
    uni.navigateTo({ url: `/pages/product/detail?id=${report.value.product_id}` });
  }
}

function goOrder() {
  if (report.value?.order_id) {
    uni.navigateTo({ url: `/pages/order/detail?id=${report.value.order_id}` });
  }
}

// ============================================================
// 预览证据图片
// ============================================================
function previewEvidence(index) {
  const urls = evidenceImages.value.map(url => resolveImageUrl(url));
  uni.previewImage({ current: index, urls });
}

// ============================================================
// 生命周期
// ============================================================
onLoad((options) => {
  reportId = parseInt(options.id, 10);
  if (!reportId) {
    errorMsg.value = '缺少举报 ID';
    loading.value = false;
    return;
  }
  loadReport();
});

// 从工单处理页返回时刷新（状态可能已变更）
onShow(() => {
  if (reportId && !loading.value) {
    loadReport();
  }
});
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.report-detail-page {
  min-height: 100vh;
  background: $color-bg;
  padding-bottom: $safe-area-bottom;
}

// ── 加载/错误居中 ──
.status-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 200rpx $space-page;
}

.status-emoji {
  font-size: 80rpx;
  margin-bottom: $space-card;
}

.status-text {
  font-size: $text-base;
  color: $color-muted;
}

.btn-retry {
  margin-top: $space-card;
  padding: 12rpx 48rpx;
  font-size: $text-sm;
  color: $color-primary;
  background: $color-primary-light;
  border-radius: $radius-full;
  border: none;
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

// ── 时间线 ──
.timeline {
  padding-left: 8rpx;
}

.tl-node {
  display: flex;
  gap: $space-content;
  padding-bottom: $space-card;
  position: relative;

  &:not(:last-child)::after {
    content: '';
    position: absolute;
    left: 7rpx;
    top: 24rpx;
    bottom: 0;
    width: 2rpx;
    background: $color-divider;
  }

  &--active {
    .tl-dot {
      background: $color-primary;
      box-shadow: 0 0 0 4rpx $color-primary-light;
    }

    .tl-label {
      color: $color-title;
      font-weight: $weight-bold;
    }

    &:not(:last-child)::after {
      background: $color-primary;
    }
  }
}

.tl-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: $color-divider;
  flex-shrink: 0;
  margin-top: 4rpx;
}

.tl-content {
  flex: 1;
}

.tl-label {
  font-size: $text-sm;
  color: $color-muted;
  display: block;
}

.tl-time {
  font-size: $text-xs;
  color: $color-muted;

  &--pending {
    color: $color-warning;
  }
}

// ── 信息行 ──
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
  flex-shrink: 0;
}

.info-value {
  font-size: $text-sm;
  color: $color-title;

  &--link {
    color: $color-primary;
  }
}

.type-tag {
  background: $color-primary-light;
  color: $color-primary;
  padding: 4rpx 16rpx;
  border-radius: $radius-full;
}

// ── 问题描述 ──
.desc-block {
  margin-top: $space-content;
  padding-top: $space-content;
  border-top: 1rpx solid $color-divider;
}

.desc-label {
  font-size: $text-sm;
  color: $color-muted;
  display: block;
  margin-bottom: 8rpx;
}

.desc-text {
  font-size: $text-base;
  color: $color-body;
  line-height: $line-height;
}

// ── 证据截图 ──
.evidence-block {
  margin-top: $space-content;
  padding-top: $space-content;
  border-top: 1rpx solid $color-divider;
}

.evidence-grid {
  display: flex;
  flex-wrap: wrap;
  gap: $space-content;
  margin-top: 8rpx;
}

.evidence-image {
  width: 160rpx;
  height: 160rpx;
  border-radius: $radius-card;
  background: $color-divider;
}

// ── 处理结果 ──
.result-placeholder {
  padding: $space-card 0;
}

.result-placeholder-text {
  font-size: $text-sm;
  color: $color-muted;
}

.result-block {
  padding-top: 8rpx;
}

.result-label {
  font-size: $text-xs;
  color: $color-muted;
  display: block;
  margin-bottom: 8rpx;
}

.result-text {
  font-size: $text-base;
  color: $color-title;
  font-weight: $weight-medium;
  line-height: $line-height;
  display: block;
}

.result-time {
  font-size: $text-xs;
  color: $color-muted;
  display: block;
  margin-top: $space-content;
}
</style>

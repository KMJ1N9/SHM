<template>
  <view class="credit-page">
    <!-- 加载中 -->
    <view v-if="loading" class="state-center">
      <text class="state-text">
        加载中...
      </text>
    </view>

    <!-- 加载失败 -->
    <view v-else-if="errorMsg" class="state-center">
      <text class="error-icon">
        😕
      </text>
      <text class="error-text">
        {{ errorMsg }}
      </text>
      <button class="retry-btn" @click="loadCredit">
        重新加载
      </button>
    </view>

    <template v-else>
      <!-- ============================================================ -->
      <!-- 头部：信誉分大数字 -->
      <!-- ============================================================ -->
      <view class="score-hero">
        <view class="score-ring">
          <text class="score-number">
            {{ creditData.score }}
          </text>
          <text class="score-max">
            / {{ creditData.max || 200 }}
          </text>
        </view>
        <text class="score-level">
          信誉等级：{{ creditLevel }}
        </text>
      </view>

      <!-- ============================================================ -->
      <!-- 权限说明卡片 -->
      <!-- ============================================================ -->
      <view class="section-card">
        <text class="section-title">
          权限说明
        </text>
        <view class="perm-table">
          <view class="perm-row perm-row--header">
            <text class="perm-cell perm-cell--range">
              分数段
            </text>
            <text class="perm-cell perm-cell--level">
              等级
            </text>
            <text class="perm-cell perm-cell--publish">
              发布商品
            </text>
            <text class="perm-cell perm-cell--trade">
              发起交易
            </text>
          </view>
          <view class="perm-row" :class="{ 'perm-row--current': currentScore >= 60 }">
            <text class="perm-cell perm-cell--range">
              ≥ 60
            </text>
            <text class="perm-cell perm-cell--level color-success">
              良好
            </text>
            <text class="perm-cell perm-cell--publish">
              ✅
            </text>
            <text class="perm-cell perm-cell--trade">
              ✅
            </text>
          </view>
          <view class="perm-row" :class="{ 'perm-row--current': currentScore >= 30 && currentScore < 60 }">
            <text class="perm-cell perm-cell--range">
              30 ~ 59
            </text>
            <text class="perm-cell perm-cell--level color-warning">
              受限
            </text>
            <text class="perm-cell perm-cell--publish">
              ❌
            </text>
            <text class="perm-cell perm-cell--trade">
              ✅
            </text>
          </view>
          <view class="perm-row" :class="{ 'perm-row--current': currentScore < 30 }">
            <text class="perm-cell perm-cell--range">
              &lt; 30
            </text>
            <text class="perm-cell perm-cell--level color-error">
              严重
            </text>
            <text class="perm-cell perm-cell--publish">
              ❌
            </text>
            <text class="perm-cell perm-cell--trade">
              ❌
            </text>
          </view>
        </view>
      </view>

      <!-- ============================================================ -->
      <!-- 变动记录 -->
      <!-- ============================================================ -->
      <view class="section-card">
        <text class="section-title">
          分数变动记录
        </text>

        <view v-if="changeLogs.length === 0" class="empty-log">
          <text class="empty-log-text">
            暂无变动记录
          </text>
        </view>

        <view
          v-for="(log, i) in changeLogs"
          :key="i"
          class="log-item"
        >
          <view class="log-left">
            <text class="log-reason">
              {{ log.reason || '系统变动' }}
            </text>
            <text class="log-time">
              {{ formatTime(log.created_at) }}
            </text>
          </view>
          <text class="log-delta" :class="log.delta > 0 ? 'log-delta--up' : 'log-delta--down'">
            {{ log.delta > 0 ? '+' : '' }}{{ log.delta }}
          </text>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
/**
 * 信誉分页面 — 大数字展示 + 权限阈值说明 + 变动记录
 *
 * 数据源：getMyCredit() → { score, change_log }
 */

import { ref, computed, onMounted } from 'vue';
import { getMyCredit } from '@/api/credit';

// ============================================================
// 数据状态
// ============================================================
const creditData = ref({ score: 0, max: 200, change_log: [] });
const loading = ref(true);
const errorMsg = ref('');

// ============================================================
// 计算属性
// ============================================================
const currentScore = computed(() => creditData.value.score || 0);

const creditLevel = computed(() => {
  const s = currentScore.value;
  if (s >= 60) return '良好';
  if (s >= 30) return '受限';
  return '严重';
});

const changeLogs = computed(() => creditData.value.change_log || []);

// ============================================================
// 数据加载
// ============================================================
onMounted(() => {
  loadCredit();
});

async function loadCredit() {
  loading.value = true;
  errorMsg.value = '';
  try {
    creditData.value = await getMyCredit();
  } catch (err) {
    errorMsg.value = err.message || '加载失败';
  } finally {
    loading.value = false;
  }
}

// ============================================================
// 辅助函数
// ============================================================
function formatTime(isoString) {
  if (!isoString) return '';
  const d = new Date(isoString);
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  const hour = String(d.getHours()).padStart(2, '0');
  const min = String(d.getMinutes()).padStart(2, '0');
  return `${month}-${day} ${hour}:${min}`;
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.credit-page {
  min-height: 100vh;
  background: $color-bg;
  padding-bottom: $space-page;
}

// ── 通用状态 ──
.state-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 80vh;
  padding: $space-page;
}

.error-icon {
  font-size: 96rpx;
  margin-bottom: $space-card;
}

.error-text {
  font-size: $text-base;
  color: $color-muted;
  margin-bottom: $space-card;
}

.state-text {
  font-size: $text-base;
  color: $color-muted;
}

.retry-btn {
  padding: 12rpx 48rpx;
  font-size: $text-base;
  color: $color-primary;
  background: $color-surface;
  border: 1rpx solid $color-primary;
  border-radius: $radius-full;
}

// ── 头部 ──
.score-hero {
  background: $color-primary-gradient;
  padding: 64rpx $space-page 48rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.score-ring {
  display: flex;
  align-items: baseline;
  gap: 4rpx;
}

.score-number {
  font-size: 96rpx;
  font-weight: $weight-bold;
  color: #FFFFFF;
  font-family: $font-mono;
  line-height: 1;
}

.score-max {
  font-size: $text-lg;
  color: rgba(255, 255, 255, 0.7);
}

.score-level {
  font-size: $text-base;
  color: rgba(255, 255, 255, 0.85);
  margin-top: $space-content;
}

// ── 区块卡片 ──
.section-card {
  background: $color-surface;
  margin: $space-card $space-page;
  padding: $space-card;
  border-radius: $radius-card;
  box-shadow: $shadow-card;
}

.section-title {
  font-size: $text-base;
  font-weight: $weight-medium;
  color: $color-title;
  margin-bottom: $space-content;
  display: block;
}

// ── 权限表格 ──
.perm-table {
  border: 1rpx solid $color-divider;
  border-radius: $radius-card;
  overflow: hidden;
}

.perm-row {
  display: flex;
  text-align: center;

  & + & {
    border-top: 1rpx solid $color-divider;
  }

  &--header {
    background: $color-bg;

    .perm-cell {
      font-weight: $weight-medium;
      color: $color-body;
    }
  }

  &--current {
    background: $color-primary-light;
  }
}

.perm-cell {
  flex: 1;
  padding: $space-content $space-content;
  font-size: $text-sm;
  color: $color-body;

  &--range { flex: 1.2; }
  &--level { flex: 0.8; }
  &--publish { flex: 1.2; }
  &--trade { flex: 1.2; }
}

.color-success { color: $color-success; }
.color-warning { color: $color-warning; }
.color-error { color: $color-error; }

// ── 变动记录 ──
.empty-log {
  text-align: center;
  padding: $space-card 0;
}

.empty-log-text {
  font-size: $text-sm;
  color: $color-muted;
}

.log-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: $space-content 0;

  & + & {
    border-top: 1rpx solid $color-divider;
  }
}

.log-left {
  flex: 1;
  overflow: hidden;
}

.log-reason {
  font-size: $text-sm;
  color: $color-body;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.log-time {
  font-size: $text-xs;
  color: $color-muted;
  margin-top: 4rpx;
  display: block;
}

.log-delta {
  font-size: $text-lg;
  font-weight: $weight-bold;
  font-family: $font-mono;
  flex-shrink: 0;
  margin-left: $space-content;

  &--up {
    color: $color-success;
  }

  &--down {
    color: $color-error;
  }
}
</style>

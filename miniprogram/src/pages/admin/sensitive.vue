<template>
  <view class="sensitive-page">
    <!-- 权限守卫 -->
    <view v-if="!userStore.isAdmin" class="empty-state">
      <EmptyState icon="🔒" title="仅管理员可访问" />
    </view>

    <template v-else>
      <!-- 词库统计 -->
      <view class="stats-card">
        <text class="stats-icon">📚</text>
        <text class="stats-label">当前词库</text>
        <template v-if="statsError">
          <view class="stats-error-box">
            <text class="stats-error-text">加载失败</text>
          </view>
          <text class="stats-error-hint">下拉刷新重试</text>
        </template>
        <template v-else>
          <view class="stats-count-box">
            <text class="stats-count">{{ stats.word_count }}</text>
          </view>
          <text class="stats-unit">个敏感词</text>
        </template>
      </view>

      <!-- 文本检查 -->
      <view class="section-card">
        <text class="section-title">文本检查</text>
        <textarea
          v-model="checkText"
          class="check-textarea"
          placeholder="输入文本进行检查..."
          :maxlength="5000"
          :disabled="checking"
          @confirm="handleCheck"
        />
        <view class="check-btn" @click="handleCheck">
          <text v-if="checking" class="check-btn-text">检查中...</text>
          <text v-else class="check-btn-text">检查文本</text>
        </view>

        <!-- 检查结果 -->
        <view v-if="checkResult !== null" class="check-result">
          <view v-if="!checkResult.has_sensitive" class="result-pass">
            <text class="result-icon">✅</text>
            <text class="result-text">未检测到敏感词</text>
          </view>
          <view v-else class="result-warn">
            <text class="result-icon">⚠️</text>
            <text class="result-text">检测到敏感词：</text>
            <view class="result-words">
              <text
                v-for="w in checkResult.words"
                :key="w"
                class="result-word-tag"
              >{{ w }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 重载词库 -->
      <view class="section-card">
        <view class="reload-row" @click="handleReload">
          <view class="reload-left">
            <text class="reload-icon">🔄</text>
            <view class="reload-info">
              <text class="reload-title">重新加载词库</text>
              <text class="reload-desc">更新敏感词库文件后点击重载，无需重启服务</text>
            </view>
          </view>
          <text class="reload-arrow">›</text>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
/**
 * 敏感词库管理页 — 查看词库统计、检查文本、重新加载词库。
 *
 * 权限：仅 admin 角色可访问。
 */
import { ref } from 'vue';
import { onShow, onUnload } from '@dcloudio/uni-app';
import { useUserStore } from '@/store/user';
import { getSensitiveStats, checkSensitiveText, reloadSensitiveWords } from '@/api/admin';
import EmptyState from '@/components/EmptyState.vue';

const userStore = useUserStore();

// ── 状态 ──────────────────────────────────────────────
const stats = ref({ word_count: 0 });
const statsError = ref(false);
const checkText = ref('');
const checkResult = ref(null);
const checking = ref(false);
const reloading = ref(false);

// ── 生命周期 ──────────────────────────────────────────
let guardTimer = null;

onShow(() => {
  if (!userStore.isAdmin) {
    uni.showToast({ title: '仅管理员可访问', icon: 'none' });
    guardTimer = setTimeout(() => uni.navigateBack({ delta: 1 }), 1500);
    return;
  }
  loadStats();
});

onUnload(() => {
  if (guardTimer) {
    clearTimeout(guardTimer);
    guardTimer = null;
  }
});

// ── 加载统计 ──────────────────────────────────────────
async function loadStats() {
  statsError.value = false;
  try {
    const data = await getSensitiveStats();
    stats.value = data;
  } catch (err) {
    statsError.value = true;
    uni.showToast({ title: '加载失败', icon: 'none' });
  }
}

// ── 文本检查 ──────────────────────────────────────────
async function handleCheck() {
  const text = checkText.value.trim();
  if (!text) {
    uni.showToast({ title: '请输入文本', icon: 'none' });
    return;
  }

  checking.value = true;
  checkResult.value = null;
  try {
    checkResult.value = await checkSensitiveText(text);
  } catch (err) {
    uni.showToast({ title: err.message || '检查失败', icon: 'error' });
  } finally {
    checking.value = false;
  }
}

// ── 重载词库 ──────────────────────────────────────────
async function handleReload() {
  if (reloading.value) return;

  const { confirm } = await uni.showModal({
    title: '重新加载词库',
    content: '将从敏感词文件重新加载，确定继续？',
    confirmText: '确定',
  });
  if (!confirm) return;

  reloading.value = true;
  try {
    const result = await reloadSensitiveWords();
    stats.value.word_count = result.word_count;
    statsError.value = false;
    uni.showToast({ title: `已重载，共 ${result.word_count} 个词`, icon: 'success' });
  } catch (err) {
    uni.showToast({ title: err.message || '重载失败', icon: 'error' });
  } finally {
    reloading.value = false;
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.sensitive-page {
  min-height: 100vh;
  background: $color-bg;
}

// ── 词库统计卡片 ──────────────────────────────────────────
.stats-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48rpx $space-page;
  background: $color-surface;
  margin-bottom: 16rpx;
}

.stats-icon {
  font-size: 64rpx;
  margin-bottom: 12rpx;
}

.stats-label {
  font-size: $text-base;
  color: $color-muted;
  margin-bottom: 20rpx;
}

.stats-count-box {
  background: $color-primary;
  border-radius: $radius-card;
  padding: 20rpx 56rpx;
  margin-bottom: 8rpx;
}

.stats-count {
  font-size: 72rpx;
  font-weight: $weight-bold;
  color: #FFFFFF;
  font-family: $font-mono;
  line-height: 1;
}

.stats-unit {
  font-size: $text-sm;
  color: $color-muted;
}

.stats-error-box {
  background: rgba(255, 77, 79, 0.08);
  border-radius: $radius-card;
  padding: 20rpx 56rpx;
  margin-bottom: 8rpx;
}

.stats-error-text {
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: $color-error;
}

.stats-error-hint {
  font-size: $text-xs;
  color: $color-muted;
}

// ── 分区卡片 ──────────────────────────────────────────────
.section-card {
  background: $color-surface;
  padding: 32rpx $space-page;
  margin-bottom: 16rpx;
}

.section-title {
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: $color-title;
  margin-bottom: 20rpx;
  display: block;
}

// ── 文本检查 ──────────────────────────────────────────────
.check-textarea {
  width: 100%;
  height: 240rpx;
  border-radius: $radius-card;
  background: $color-bg;
  padding: 20rpx 24rpx;
  font-size: $text-base;
  box-sizing: border-box;
}

.check-btn {
  margin-top: 20rpx;
  height: $btn-height-md;
  border-radius: $radius-card;
  background: $color-primary;
  display: flex;
  align-items: center;
  justify-content: center;
}

.check-btn-text {
  font-size: $text-base;
  color: #FFFFFF;
}

// ── 检查结果 ──────────────────────────────────────────────
.check-result {
  margin-top: 24rpx;
  padding: 24rpx;
  border-radius: $radius-card;
}

.result-pass {
  display: flex;
  align-items: center;
  gap: 12rpx;
  background: rgba(82, 196, 26, 0.06);
  padding: 20rpx 24rpx;
  border-radius: $radius-card;
}

.result-warn {
  background: rgba(250, 173, 20, 0.06);
  padding: 20rpx 24rpx;
  border-radius: $radius-card;
}

.result-icon {
  font-size: 28rpx;
}

.result-text {
  font-size: $text-base;
  color: $color-body;
}

.result-words {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 16rpx;
}

.result-word-tag {
  font-size: $text-sm;
  color: $color-error;
  background: rgba(255, 77, 79, 0.08);
  padding: 6rpx 20rpx;
  border-radius: $radius-full;
}

// ── 重载词库 ──────────────────────────────────────────────
.reload-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.reload-left {
  display: flex;
  align-items: center;
  gap: 20rpx;
  flex: 1;
}

.reload-icon {
  font-size: 40rpx;
}

.reload-info {
  flex: 1;
}

.reload-title {
  font-size: $text-base;
  color: $color-title;
  display: block;
}

.reload-desc {
  font-size: $text-sm;
  color: $color-muted;
  margin-top: 4rpx;
}

.reload-arrow {
  font-size: 36rpx;
  color: $color-muted;
}
</style>

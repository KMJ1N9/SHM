<template>
  <view v-if="visible" class="review-modal-mask" @click="closeModal">
    <view class="review-modal" @click.stop>
      <text class="review-modal-title">评价 {{ partnerName }}</text>

      <!-- 沟通态度 -->
      <view class="review-dim">
        <text class="review-dim-label">沟通态度</text>
        <StarRating v-model="form.communicationScore" size="lg" />
      </view>

      <!-- 守时程度 -->
      <view class="review-dim">
        <text class="review-dim-label">守时程度</text>
        <StarRating v-model="form.punctualityScore" size="lg" />
      </view>

      <!-- 描述一致度 -->
      <view class="review-dim">
        <text class="review-dim-label">描述一致度</text>
        <StarRating v-model="form.accuracyScore" size="lg" />
      </view>

      <!-- 文字评价 -->
      <view class="review-comment-box">
        <textarea
          v-model="form.comment"
          class="review-textarea"
          placeholder="说说这次交易的感受吧（选填）"
          :maxlength="500"
          auto-height
        />
        <text class="review-comment-count">{{ form.comment.length }}/500</text>
      </view>

      <!-- 操作按钮 -->
      <view class="review-modal-actions">
        <button class="review-btn review-btn--cancel" @click="closeModal">取消</button>
        <button
          class="review-btn review-btn--submit"
          :disabled="submitting"
          @click="handleSubmit"
        >
          {{ submitting ? '提交中...' : '提交评价' }}
        </button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { createReview } from '@/api/review';
import StarRating from '@/components/StarRating.vue';

const props = defineProps({
  visible: { type: Boolean, default: false },
  order: { type: Object, required: true },
  partnerName: { type: String, default: '' },
  isBuyer: { type: Boolean, default: false },
  myId: { type: Number, default: 0 },
  userNickname: { type: String, default: '' },
  userAvatar: { type: String, default: '' },
});

const emit = defineEmits(['close', 'submitted']);

const submitting = ref(false);
const form = reactive({
  communicationScore: 5,
  punctualityScore: 5,
  accuracyScore: 5,
  comment: '',
});

function resetForm() {
  form.communicationScore = 5;
  form.punctualityScore = 5;
  form.accuracyScore = 5;
  form.comment = '';
}

function closeModal() {
  resetForm();
  emit('close');
}

async function handleSubmit() {
  if (submitting.value) return;
  submitting.value = true;
  try {
    await createReview({
      order_id: props.order.id,
      reviewee_id: props.isBuyer ? props.order.seller_id : props.order.buyer_id,
      communication_score: form.communicationScore,
      punctuality_score: form.punctualityScore,
      accuracy_score: form.accuracyScore,
      comment: form.comment || undefined,
    });
    // 构造本地评价对象（临时 ID，父组件刷新后覆盖）
    const localReview = {
      id: 'temp_' + Date.now(),
      order_id: props.order.id,
      reviewer_id: props.myId,
      reviewee_id: props.isBuyer ? props.order.seller_id : props.order.buyer_id,
      communication_score: form.communicationScore,
      punctuality_score: form.punctualityScore,
      accuracy_score: form.accuracyScore,
      comment: form.comment || '',
      reviewer_nickname: props.userNickname || '我',
      reviewer_avatar: props.userAvatar || '',
      created_at: new Date().toISOString(),
    };
    uni.showToast({ title: '评价成功', icon: 'success' });
    emit('submitted', localReview);
    closeModal();
  } catch (err) {
    const msg = err.message || '评价失败';
    if (msg.includes('3006') || msg.includes('重复') || msg.includes('已评价')) {
      uni.showToast({ title: '你已经评价过该订单了', icon: 'none', duration: 2000 });
      closeModal();
    } else if (msg.includes('3001') || msg.includes('状态')) {
      uni.showToast({ title: '仅可评价已完成的订单', icon: 'none', duration: 2000 });
      closeModal();
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

.review-modal-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 1000;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.review-modal {
  width: 100%;
  max-height: 85vh;
  background: $color-surface;
  border-radius: $radius-card $radius-card 0 0;
  padding: 40rpx $space-page calc(40rpx + $safe-area-bottom);
  overflow-y: auto;
}

.review-modal-title {
  font-size: $text-lg;
  font-weight: $weight-bold;
  color: $color-title;
  text-align: center;
  display: block;
  margin-bottom: 40rpx;
}

.review-dim {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: $space-content 0;

  & + & {
    border-top: 1rpx solid $color-divider;
  }
}

.review-dim-label {
  font-size: $text-base;
  color: $color-body;
}

.review-comment-box {
  margin-top: $space-card;
  padding: $space-content;
  background: $color-bg;
  border-radius: $radius-card;
  position: relative;
}

.review-textarea {
  width: 100%;
  min-height: 120rpx;
  font-size: $text-sm;
  color: $color-body;
  line-height: $line-height;
}

.review-comment-count {
  text-align: right;
  font-size: $text-xs;
  color: $color-muted;
  display: block;
  margin-top: 4rpx;
}

.review-modal-actions {
  display: flex;
  gap: $space-card;
  margin-top: 32rpx;
}

.review-btn {
  flex: 1;
  height: $btn-height-md;
  border-radius: $radius-card;
  font-size: $text-base;
  font-weight: $weight-medium;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;

  &::after {
    border: none;
  }

  &--cancel {
    background: $color-bg;
    color: $color-body;
  }

  &--submit {
    background: $color-primary-gradient;
    color: #FFFFFF;

    &[disabled] {
      opacity: 0.5;
    }
  }
}
</style>

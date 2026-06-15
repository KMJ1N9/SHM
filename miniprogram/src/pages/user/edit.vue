<template>
  <view class="edit-page">
    <!-- 头像 -->
    <view class="edit-section">
      <view class="edit-section-title">头像</view>
      <view class="avatar-row" @click="onChangeAvatar">
        <SafeImage
          v-if="form.avatar"
          class="avatar-img"
          :src="resolveImageUrl(form.avatar)"
          mode="aspectFill"
        />
        <view v-else class="avatar-placeholder">
          <text class="avatar-placeholder-icon">👤</text>
        </view>
        <text class="avatar-hint">点击更换头像</text>
      </view>
    </view>

    <!-- 昵称 -->
    <view class="edit-section">
      <view class="edit-section-title">昵称</view>
      <input
        v-model="form.nickname"
        class="edit-input"
        placeholder="请输入昵称（最多 50 字）"
        :maxlength="50"
      />
    </view>

    <!-- 班级 -->
    <view class="edit-section">
      <view class="edit-section-title">班级</view>
      <input
        v-model="form.class_name"
        class="edit-input"
        placeholder="如：21 计算机科学与技术 1 班"
        :maxlength="100"
      />
    </view>

    <!-- 宿舍楼栋 -->
    <view class="edit-section">
      <view class="edit-section-title">宿舍楼栋</view>
      <input
        v-model="form.dorm_building"
        class="edit-input"
        placeholder="如：北区 3 栋"
        :maxlength="100"
      />
    </view>

    <!-- 保存按钮 -->
    <view class="btn-area">
      <button
        class="save-btn"
        :disabled="saving"
        @click="onSave"
      >
        {{ saving ? '保存中...' : '保存' }}
      </button>
    </view>
  </view>
</template>

<script setup>
/**
 * 编辑资料页 — 修改头像、昵称、班级、宿舍楼栋
 *
 * 数据流：
 *   - 页面加载时从 userStore 读取当前用户信息填充表单
 *   - 头像上传通过 chooseAndUpload → COS 直传
 *   - 保存调用 updateProfile → 后端 PUT /api/users/me
 *   - 保存成功后刷新 userStore.getMeAction()
 */
import { ref, reactive } from 'vue';
import { useUserStore } from '@/store/user';
import { updateProfile } from '@/api/user';
import { resolveImageUrl } from '@/api/index';
import { chooseAndUpload } from '@/utils/cos';
import SafeImage from '@/components/SafeImage.vue';

const userStore = useUserStore();

const form = reactive({
  avatar: userStore.user?.avatar || '',
  nickname: userStore.user?.nickname || '',
  class_name: userStore.user?.class_name || '',
  dorm_building: userStore.user?.dorm_building || '',
});

const saving = ref(false);

async function onChangeAvatar() {
  try {
    const urls = await chooseAndUpload();
    if (urls && urls.length > 0) {
      form.avatar = urls[0];
    }
  } catch (err) {
    uni.showToast({ title: err.message || '头像上传失败', icon: 'none' });
  }
}

async function onSave() {
  if (saving.value) return;
  if (!form.nickname.trim()) {
    uni.showToast({ title: '昵称不能为空', icon: 'none' });
    return;
  }

  saving.value = true;
  try {
    const payload = {};
    if (form.avatar !== (userStore.user?.avatar || '')) payload.avatar = form.avatar;
    if (form.nickname !== (userStore.user?.nickname || '')) payload.nickname = form.nickname.trim();
    if (form.class_name !== (userStore.user?.class_name || '')) payload.class_name = form.class_name.trim() || '';
    if (form.dorm_building !== (userStore.user?.dorm_building || '')) payload.dorm_building = form.dorm_building.trim() || '';

    if (Object.keys(payload).length === 0) {
      uni.showToast({ title: '没有改动', icon: 'none' });
      return;
    }

    await updateProfile(payload);
    await userStore.getMeAction();
    uni.showToast({
      title: '保存成功',
      icon: 'success',
      duration: 800,
    });
    // toast 结束后返回，避免页面已不在栈顶（用户手动返回）导致导航失败
    setTimeout(() => {
      const pages = getCurrentPages();
      if (pages.length > 1) {
        uni.navigateBack({ delta: 1 });
      }
    }, 800);
  } catch (err) {
    uni.showToast({ title: err.message || '保存失败', icon: 'none' });
  } finally {
    saving.value = false;
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.edit-page {
  min-height: 100vh;
  background: $color-bg;
  padding-bottom: $safe-area-bottom;
}

.edit-section {
  background: $color-surface;
  padding: 24rpx $space-page;
  margin-top: 16rpx;
}

.edit-section-title {
  font-size: $text-xs;
  color: $color-muted;
  font-weight: $weight-medium;
  margin-bottom: 16rpx;
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.avatar-img {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background: $color-divider;
}

.avatar-placeholder {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background: $color-divider;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-placeholder-icon {
  font-size: 48rpx;
}

.avatar-hint {
  font-size: $text-sm;
  color: $color-primary;
}

.edit-input {
  width: 100%;
  height: $input-height;
  border-radius: $radius-card;
  background: $color-bg;
  padding: 0 24rpx;
  font-size: $text-base;
  color: $color-title;
  box-sizing: border-box;
}

.btn-area {
  padding: 48rpx $space-page;
}

.save-btn {
  width: 100%;
  height: $btn-height-lg;
  line-height: $btn-height-lg;
  background: $color-primary;
  color: #FFFFFF;
  font-size: $text-lg;
  font-weight: $weight-bold;
  border-radius: $radius-card;
  border: none;

  &[disabled] {
    background: $color-divider;
    color: $color-muted;
  }
}
</style>

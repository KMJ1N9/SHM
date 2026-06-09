<script setup>
import { watch } from 'vue';
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app';
import { useUserStore } from '@/store/user';
import { useAppStore } from '@/store/app';
import { initIM } from '@/utils/im';
import { unreadCount } from '@/api/notification';

// ============================================================
// 常量
// ============================================================

/** 通知未读轮询间隔（ms） */
const POLL_INTERVAL = 30000;

/** TabBar 消息页签索引（pages.json tabBar.list 中第 3 项，0-based = 2） */
const TAB_MSG_INDEX = 2;

// ============================================================
// 定时器引用
// ============================================================

let notifyTimer = null;

// ============================================================
// 状态
// ============================================================

const userStore = useUserStore();
const appStore = useAppStore();

// ============================================================
// 生命周期
// ============================================================

onLaunch(async () => {
  console.log('校园二手交易小程序启动');

  // 恢复用户登录态
  try {
    await userStore.initAuth();
  } catch {
    // initAuth 内部已做异常处理，此处兜底
  }

  // 未登录 → 跳转登录页
  if (!userStore.isLoggedIn) {
    uni.reLaunch({ url: '/pages/auth/login' });
    return;
  }

  // 登录后初始化 IM（异步，不阻塞 onLaunch；失败在聊天页处理）
  initIM().catch((err) => {
    console.warn('[App] IM 初始化失败 — 点击聊一聊时将自动重试:', err.message || err);
  });

  // 启动通知未读轮询
  startNotifyPolling();

  // 监听网络状态
  uni.onNetworkStatusChange((res) => {
    appStore.setNetworkStatus(res.isConnected ? 'online' : 'offline');
  });
});

onShow(() => {
  console.log('App Show');

  // 前台恢复时刷新通知未读
  if (userStore.isLoggedIn) {
    fetchNotifyUnread();
    startNotifyPolling();
  }
});

onHide(() => {
  console.log('App Hide');

  // 后台时停止轮询
  stopNotifyPolling();
});

// ============================================================
// TabBar 角标
// ============================================================

/**
 * 监听未读总数 → 更新 TabBar 消息页签角标
 */
watch(
  () => appStore.totalUnread,
  (count) => {
    if (count > 0) {
      uni.setTabBarBadge({
        index: TAB_MSG_INDEX,
        text: count > 99 ? '99+' : String(count),
      });
    } else {
      uni.removeTabBarBadge({ index: TAB_MSG_INDEX });
    }
  },
  { immediate: true }
);

// ============================================================
// 通知轮询
// ============================================================

/** 启动通知未读轮询 */
function startNotifyPolling() {
  if (notifyTimer) return; // 已在运行
  fetchNotifyUnread();
  notifyTimer = setInterval(fetchNotifyUnread, POLL_INTERVAL);
}

/** 停止通知未读轮询 */
function stopNotifyPolling() {
  if (notifyTimer) {
    clearInterval(notifyTimer);
    notifyTimer = null;
  }
}

/** 拉取未读通知数 */
async function fetchNotifyUnread() {
  try {
    const result = await unreadCount();
    appStore.setUnreadNotifyCount(result.count || 0);
  } catch (err) {
    console.debug('[App] 通知未读轮询失败:', err.message || err);
  }
}
</script>

<style lang="scss">
@import '@/styles/common.scss';
</style>

<script setup>
import { watch, nextTick } from 'vue';
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
// 登录态监听 — 冷启动登录后自动初始化 IM
// ============================================================
//
// 冷启动时序：
//   onLaunch → initAuth() 无 Token → isLoggedIn=false → 跳转登录页 → return
//   → onShow 触发时 isLoggedIn 仍为 false → 无法初始化 IM
//   → 用户登录成功 → loginAction() 写入 Token + user → isLoggedIn 变为 true
//
// watch 在此刻触发 initIM()，IM 在用户点击消息页之前就已开始初始化。
// onLaunch 的 initIM() 保留（热启动路径），onShow 的 initIM() 保留（后台恢复路径）。
// initIM() 内部有 isReady / isInitializing 守卫，多条路径并发安全。
watch(
  () => userStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      initIM().catch((err) => {
        console.warn('[App] IM 初始化失败:', err.message || err);
      });
    }
  }
);

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
  // 注意：微信小程序渲染层在首次渲染时可能尚未初始化 listener 管道，
  // 导致 "Cannot read property 'addListener' of undefined" 错误。
  // 用 try/catch 包裹并降级为静默跳过——网络状态监听非核心功能。
  try {
    uni.onNetworkStatusChange((res) => {
      appStore.setNetworkStatus(res.isConnected ? 'online' : 'offline');
    });
  } catch (err) {
    console.warn('[App] 网络状态监听注册失败（非关键功能，忽略）:', err.message || err);
  }
});

onShow(() => {
  console.log('App Show');

  // 前台恢复时刷新通知未读
  if (userStore.isLoggedIn) {
    // 确保 IM 已初始化（覆盖冷启动登录场景：
    // onLaunch 检测未登录 → 提前 return 跳过了 initIM()，
    // 用户登录后 switchTab 到这里时 IM 仍未初始化）
    initIM().catch((err) => {
      console.warn('[App] IM 初始化失败:', err.message || err);
    });

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
 *
 * 注意：不使用 { immediate: true }，因为它在首次渲染时立即调用
 * setTabBarBadge 可能与微信渲染层初始化时序冲突，导致
 * "Expected updated data but get first rendering data" 错误。
 * 改用 nextTick 在首个渲染周期结束后设置初始角标。
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
  }
);

// 首个渲染周期结束后再设置 TabBar 角标（避免与渲染层初始化时序冲突）
nextTick(() => {
  const count = appStore.totalUnread;
  if (count > 0) {
    uni.setTabBarBadge({
      index: TAB_MSG_INDEX,
      text: count > 99 ? '99+' : String(count),
    });
  } else {
    uni.removeTabBarBadge({ index: TAB_MSG_INDEX });
  }
});

// ============================================================
// 通知轮询
// ============================================================

/** 启动通知未读轮询 */
function startNotifyPolling() {
  if (notifyTimer) return; // 已在运行
  appStore.setShouldPoll(true);
  fetchNotifyUnread();
  notifyTimer = setInterval(fetchNotifyUnread, POLL_INTERVAL);
}

/** 停止通知未读轮询 */
function stopNotifyPolling() {
  appStore.setShouldPoll(false);
  if (notifyTimer) {
    clearInterval(notifyTimer);
    notifyTimer = null;
  }
}

/** 拉取未读通知数 */
async function fetchNotifyUnread() {
  // 双重守卫：已登出 或 已停止轮询 → 跳过
  if (!userStore.isLoggedIn || !appStore.shouldPoll) return;
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

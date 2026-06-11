/**
 * 聊天入口处理 — composable
 *
 * 从 product/detail.vue 提取 goChat 逻辑，减少主文件行数。
 * 包含 IM SDK 就绪检测、账号确保、缓存写入、页面跳转。
 */
import { isIMReady, reInitIM, getLastError, cachePeerProfile } from '@/utils/im';
import { ensureAccount } from '@/api/im';

export function useChatHandler() {
  /**
   * 发起聊天
   *
   * @param {Object} product — 商品详情对象（含 seller 嵌套）
   * @param {Object} userStore — Pinia user store
   */
  async function goChat(product, userStore) {
    if (!product || !product.seller) {
      uni.showToast({ title: '卖家信息不可用', icon: 'none', duration: 1500 });
      return;
    }

    const myId = userStore.user?.id;
    const sellerId = product.seller.id;

    if (!myId) {
      uni.showToast({ title: '请先登录', icon: 'none', duration: 1500 });
      return;
    }

    if (myId === sellerId) {
      uni.showToast({ title: '这是你自己发布的商品', icon: 'none', duration: 1500 });
      return;
    }

    if (!isIMReady()) {
      uni.showToast({ title: '消息服务连接中…', icon: 'none', duration: 2000 });
      try {
        await reInitIM();
      } catch (err) {
        const reason = getLastError() || err.message || '未知错误';
        console.error('[goChat] IM 初始化失败:', reason);
        uni.showModal({
          title: '消息服务连接失败',
          content: `无法连接到聊天服务。\n\n原因: ${reason}\n\n请检查网络后重试。如持续失败，请截图联系开发者。`,
          showCancel: false,
        });
        return;
      }
    }

    if (!isIMReady()) {
      uni.showToast({ title: '消息服务未就绪，请稍后再试', icon: 'none', duration: 2000 });
      return;
    }

    const conversationID = `C2C${sellerId}`;
    const sellerNickname = product.seller.nickname || '用户';
    const sellerAvatar = product.seller.avatar || '';

    cachePeerProfile(sellerId, sellerNickname, sellerAvatar);
    ensureAccount(sellerId, sellerNickname, sellerAvatar).catch(() => {});

    uni.navigateTo({
      url: `/pages/chat/detail?conversationId=${conversationID}&nickname=${encodeURIComponent(sellerNickname)}&avatar=${encodeURIComponent(sellerAvatar)}`,
    });
  }

  return { goChat };
}

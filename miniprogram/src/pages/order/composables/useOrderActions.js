/**
 * 订单操作处理 — composable
 *
 * 从 order/detail.vue 提取，减少主文件行数。
 * 所有操作函数通过参数接收运行时状态，避免闭包耦合。
 */
export function useOrderActions() {
  /**
   * 标记面交
   * @param {number} orderId
   * @param {Function} markAsMet
   * @param {Function} loadOrder
   */
  async function handleMarkMet(orderId, markAsMet, loadOrder) {
    const { confirm } = await uni.showModal({
      title: '确认面交',
      content: '确认已与对方完成面交？',
    });
    if (!confirm) return;
    uni.showLoading({ title: '操作中...', mask: true });
    try {
      await markAsMet(orderId);
      uni.hideLoading();
      uni.showToast({ title: '已确认面交', icon: 'success' });
      await loadOrder();
    } catch (err) {
      uni.hideLoading();
      uni.showToast({ title: err.message || '操作失败', icon: 'error' });
    }
  }

  /**
   * 确认收货
   * @param {number} orderId
   * @param {Function} confirmOrder
   * @param {Function} loadOrder
   */
  async function handleConfirm(orderId, confirmOrder, loadOrder) {
    const { confirm } = await uni.showModal({
      title: '确认收货',
      content: '确认已收到商品？确认后订单将完成，并开放双方互评。',
    });
    if (!confirm) return;
    uni.showLoading({ title: '操作中...', mask: true });
    try {
      await confirmOrder(orderId);
      uni.hideLoading();
      uni.showToast({ title: '已确认收货，请评价', icon: 'success' });
      await loadOrder();
    } catch (err) {
      uni.hideLoading();
      uni.showToast({ title: err.message || '操作失败', icon: 'error' });
    }
  }

  /**
   * 取消订单
   * @param {number} orderId
   * @param {Function} cancelOrder
   * @param {Function} loadOrder
   */
  async function handleCancel(orderId, cancelOrder, loadOrder) {
    const { confirm } = await uni.showModal({
      title: '取消订单',
      content: '确认取消该订单？',
    });
    if (!confirm) return;
    uni.showLoading({ title: '操作中...', mask: true });
    try {
      await cancelOrder(orderId);
      uni.hideLoading();
      uni.showToast({ title: '订单已取消', icon: 'success' });
      await loadOrder();
    } catch (err) {
      uni.hideLoading();
      uni.showToast({ title: err.message || '操作失败', icon: 'error' });
    }
  }

  /**
   * 举报交易对方 — 构造参数并跳转
   * @param {Object} order
   * @param {boolean} isBuyer
   * @param {Function} navigateTo
   */
  function goReport(order, isBuyer) {
    if (!order) return;
    const reportedUserId = isBuyer ? order.seller_id : order.buyer_id;
    uni.navigateTo({
      url: `/pages/report/submit?product_id=${order.product_id || ''}&order_id=${order.id}&reported_user_id=${reportedUserId}`,
    });
  }

  return { handleMarkMet, handleConfirm, handleCancel, goReport };
}

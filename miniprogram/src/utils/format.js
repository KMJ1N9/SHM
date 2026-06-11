/**
 * 通用格式化工具
 *
 * 项目内统一使用以下函数进行价格/日期格式化，
 * 避免在各页面中重复定义。
 */

/**
 * 格式化价格（去掉 .00 后缀，非法值返回 '0'）
 * @param {number|string} price
 * @returns {string}
 */
export function formatPrice(price) {
  const num = parseFloat(price);
  if (Number.isNaN(num)) return '0';
  return num % 1 === 0 ? String(num) : num.toFixed(2);
}

/**
 * 格式化 ISO 日期 → MM-DD HH:mm
 * @param {string} isoString
 * @returns {string}
 */
export function formatDateTime(isoString) {
  if (!isoString) return '';
  try {
    const d = new Date(isoString);
    if (Number.isNaN(d.getTime())) return '';
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    const hour = String(d.getHours()).padStart(2, '0');
    const min = String(d.getMinutes()).padStart(2, '0');
    return `${month}-${day} ${hour}:${min}`;
  } catch {
    return '';
  }
}

/**
 * 格式化 ISO 日期 → 相对时间（刚刚 / N 分钟前 / N 小时前 / M 月 D 日）
 * @param {string} dateStr
 * @returns {string}
 */
export function formatTime(dateStr) {
  if (!dateStr) return '';
  try {
    const d = new Date(dateStr);
    const now = new Date();
    const diff = now - d;
    if (diff < 60 * 1000) return '刚刚';
    if (diff < 60 * 60 * 1000) return `${Math.floor(diff / (60 * 1000))} 分钟前`;
    if (diff < 24 * 60 * 60 * 1000) return `${Math.floor(diff / (60 * 60 * 1000))} 小时前`;
    const month = d.getMonth() + 1;
    const day = d.getDate();
    return `${month} 月 ${day} 日`;
  } catch {
    return '';
  }
}

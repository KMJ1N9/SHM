/**
 * 图片下载队列 — 全局并发控制
 *
 * uni.downloadFile 在微信小程序真机上有 ~10 并发限制，超限后后续请求全部失败。
 * 本模块维护一个模块级单例队列，全局最多 5 个并发下载，超出排队等待。
 *
 * 用法：
 *   import { downloadImage } from '@/utils/download-queue';
 *   const localPath = await downloadImage('http://192.168.1.1:3000/images/xxx.jpg');
 *
 * @module download-queue
 */

const MAX_CONCURRENT = 5;

let activeCount = 0;
const pendingQueue = [];

/**
 * 下载单张图片到本地临时文件
 *
 * @param {string} url - 图片网络 URL
 * @param {number} [timeout=15000] - 超时毫秒数
 * @returns {Promise<string>} 本地临时文件路径
 */
export function downloadImage(url, timeout = 15000) {
  return new Promise((resolve, reject) => {
    const execute = () => {
      activeCount++;
      let settled = false;
      const timer = setTimeout(() => {
        if (settled) return;
        settled = true;
        reject(new Error('下载超时'));
        onDone();
      }, timeout);

      uni.downloadFile({
        url,
        success: (res) => {
          if (settled) return;
          settled = true;
          clearTimeout(timer);
          if (res.statusCode === 200) {
            resolve(res.tempFilePath);
          } else {
            reject(new Error('HTTP ' + res.statusCode));
          }
          onDone();
        },
        fail: (err) => {
          if (settled) return;
          settled = true;
          clearTimeout(timer);
          reject(err);
          onDone();
        },
      });

      function onDone() {
        activeCount--;
        // 出队下一个
        if (pendingQueue.length > 0) {
          const next = pendingQueue.shift();
          next();
        }
      }
    };

    if (activeCount < MAX_CONCURRENT) {
      execute();
    } else {
      pendingQueue.push(execute);
    }
  });
}

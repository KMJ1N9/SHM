/**
 * uni-app API mock — 供 vitest 测试使用
 *
 * 在 vitest.config.js 中通过 setupFiles 注入：
 *   vi.mock('uni-app', () => ({ default: mockUni }))
 *   或在测试文件中手动 mock。
 *
 * 当前 mock 范围：
 *   - Storage: getStorageSync / setStorageSync / removeStorageSync
 *
 * 扩展方式：按需追加 mock 方法即可。
 */

let storage = {};

function resetStorage() {
  storage = {};
}

const mockUni = {
  getStorageSync(key) {
    return storage[key] ?? '';
  },

  setStorageSync(key, data) {
    storage[key] = data;
  },

  removeStorageSync(key) {
    delete storage[key];
  },

  // 测试辅助：重置 storage 状态
  _reset: resetStorage,
};

export { mockUni, resetStorage };

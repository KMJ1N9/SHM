/**
 * 内存 LRU 缓存
 *
 * MVP 阶段使用进程内 LRU 缓存，无需 Redis。
 * 后续可扩展为 Redis 缓存（保持相同接口）。
 *
 * 缓存策略：
 * - 首页商品列表（第一页）：5 min TTL
 * - 用户公开信息：10 min TTL
 * - COS STS 凭证：25 min TTL（提前 5 分钟过期）
 * - 热门分类/搜索词：30 min TTL
 */

const DEFAULT_MAX_SIZE = 500;
const DEFAULT_TTL = 5 * 60 * 1000; // 5 min

class LRUCache {
  constructor(maxSize = DEFAULT_MAX_SIZE) {
    this.maxSize = maxSize;
    this.cache = new Map();
  }

  /**
   * 获取缓存值
   * @param {string} key
   * @returns {*|null}
   */
  get(key) {
    const entry = this.cache.get(key);
    if (!entry) return null;

    // 检查过期
    if (Date.now() > entry.expiresAt) {
      this.cache.delete(key);
      return null;
    }

    // LRU：移到末尾（最近使用）
    this.cache.delete(key);
    this.cache.set(key, entry);
    return entry.value;
  }

  /**
   * 设置缓存值
   * @param {string} key
   * @param {*} value
   * @param {number} [ttlMs] - 过期时间（毫秒），默认 5 分钟
   */
  set(key, value, ttlMs = DEFAULT_TTL) {
    // 清理过期条目
    if (this.cache.size >= this.maxSize) {
      this._evict();
    }

    // 删除旧值（LRU 重排）
    this.cache.delete(key);

    this.cache.set(key, {
      value,
      expiresAt: Date.now() + ttlMs,
    });
  }

  /**
   * 删除缓存
   * @param {string} key
   */
  delete(key) {
    this.cache.delete(key);
  }

  /**
   * 按前缀批量删除（用于失效：如新商品发布清理 category 缓存）
   * @param {string} prefix
   */
  deleteByPrefix(prefix) {
    for (const key of this.cache.keys()) {
      if (key.startsWith(prefix)) {
        this.cache.delete(key);
      }
    }
  }

  /**
   * 获取或设置（Cache-Aside 模式）
   *
   * 注意：fetchFn 不应返回 null/undefined —— 若返回 null，
   * 会写入缓存（{value: null}），下次 get() 返回 null 触发
   * `cached !== null` 判 false，导致每次调用都重新执行 fetchFn，
   * 缓存永不命中。fetchFn 应返回有效值或抛出异常。
   *
   * @param {string} key
   * @param {function(): Promise<*>} fetchFn - 不应返回 null（返回有效值或 throw）
   * @param {number} [ttlMs]
   * @returns {Promise<*>}
   */
  async getOrSet(key, fetchFn, ttlMs) {
    const cached = this.get(key);
    if (cached !== null) return cached;

    const value = await fetchFn();
    this.set(key, value, ttlMs);
    return value;
  }

  /** 清理过期条目（写入时触发） */
  _evict() {
    const now = Date.now();
    for (const [key, entry] of this.cache) {
      if (now > entry.expiresAt) {
        this.cache.delete(key);
      }
    }

    // 如果清理后仍然满，删除最旧的条目
    if (this.cache.size >= this.maxSize) {
      const oldest = this.cache.keys().next().value;
      this.cache.delete(oldest);
    }
  }

  /** 获取缓存大小 */
  get size() {
    return this.cache.size;
  }

  /** 清空所有缓存 */
  clear() {
    this.cache.clear();
  }
}

// 单例导出
const cache = new LRUCache();

module.exports = { LRUCache, cache };

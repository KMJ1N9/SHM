/**
 * 用户服务
 *
 * 处理用户资料查询和编辑。
 */

const userRepo = require('../repository/user');
const { cache } = require('../utils/cache');
const { notFound } = require('../utils/errors');

const userService = {
  /**
   * 查看用户公开信息（个人主页，含 LRU 缓存）
   *
   * 缓存策略：用户公开信息极少变更，TTL 300s（5 分钟）。
   * 更新个人资料时缓存失效。
   *
   * @param {number} userId
   * @returns {Promise<Object>}
   */
  async getById(userId) {
    const cacheKey = `user:public:${userId}`;
    return cache.getOrSet(cacheKey, async () => {
      const user = await userRepo.findPublicById(userId);
      if (!user) {
        throw notFound('用户');
      }
      return user;
    }, 300 * 1000); // 300s TTL
  },

  /**
   * 编辑个人资料
   * @param {number} userId
   * @param {Object} updates - { nickname?, avatar?, class_name?, dorm_building? }
   * @returns {Promise<Object>}
   */
  async updateProfile(userId, updates) {
    const allowed = ['nickname', 'avatar', 'class_name', 'dorm_building'];
    const filtered = {};
    for (const key of allowed) {
      if (updates[key] !== undefined && updates[key] !== null) {
        filtered[key] = updates[key];
      }
    }

    const user = await userRepo.updateProfile(userId, filtered);

    // 缓存失效：用户公开信息已变更
    cache.delete(`user:public:${userId}`);

    return {
      id: user.id,
      nickname: user.nickname,
      avatar: user.avatar,
      class_name: user.class_name,
      dorm_building: user.dorm_building,
    };
  },
};

module.exports = userService;

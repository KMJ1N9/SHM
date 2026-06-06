/**
 * 用户服务
 *
 * 处理用户资料查询和编辑。
 */

const userRepo = require('../repository/user');
const { notFound } = require('../utils/errors');

const userService = {
  /**
   * 查看用户公开信息（个人主页）
   * @param {number} userId
   * @returns {Promise<Object>}
   */
  async getById(userId) {
    const user = await userRepo.findPublicById(userId);
    if (!user) {
      throw notFound('用户');
    }
    return user;
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

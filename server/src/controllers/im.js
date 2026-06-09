/**
 * IM 控制器
 *
 * 提供 IM SDK 初始化所需的 UserSig 签发端点。
 * 聊天消息通过 IM SDK WebSocket 直连腾讯云，不经过本服务。
 */

const imProvider = require('../services/im/provider');
const config = require('../config');

const imController = {
  /**
   * GET /api/im/user-sig — 获取当前用户的 IM UserSig
   *
   * 前端 IM SDK 初始化时需要 userId + userSig + sdkAppId 三要素。
   * UserSig 由服务端 HMAC-SHA256 签发，前端不持有 IM 密钥。
   */
  async getUserSig(req, res, next) {
    try {
      const userId = req.user.id;
      const userSig = imProvider.generateUserSig(userId);

      // 确保当前用户的 IM 账号已导入，同时同步昵称和头像
      // 避免 IM 服务端因缺少资料而生成 "用户X" 占位昵称
      // 导入已有账号是幂等的（不会报错），无需检查结果
      try {
        await imProvider.importAccount(
          userId,
          req.user.nickname,
          req.user.avatar
        );
      } catch {
        // 导入失败不阻塞 UserSig 签发 —— 若用户从未发消息，
        // SDK login() 也会自动创建账号
      }

      res.json({
        code: 0,
        message: 'ok',
        data: {
          userId: String(userId),
          userSig,
          sdkAppId: config.im.sdkAppId,
        },
      });
    } catch (err) {
      next(err);
    }
  },

  /**
   * POST /api/im/ensure-account — 确保指定用户的 IM 账号已导入
   *
   * 前端"聊一聊"前调用，确保接收方（卖家）的 IM 账号存在。
   * 腾讯云 IM 发送消息要求接收方 UserID 已导入，否则返回 20003 错误。
   *
   * 请求体可选 nick/avatar 参数，用于同步用户资料到 IM，
   * 防止 IM 服务端因缺少资料而生成 "用户X" 占位昵称。
   * 若未提供则从数据库查询。
   */
  async ensureAccount(req, res, next) {
    try {
      const { userId, nick, avatar } = req.body;

      if (!userId) {
        return res.status(400).json({
          code: 400,
          message: '请提供 userId',
          data: null,
        });
      }

      // 如果前端未提供昵称，从数据库查询
      let userNick = nick;
      let userAvatar = avatar;
      if (!userNick) {
        const userRepo = require('../repository/user');
        const user = await userRepo.findPublicById(Number(userId));
        if (user) {
          userNick = user.nickname;
          userAvatar = user.avatar;
        }
      }

      const result = await imProvider.importAccount(userId, userNick, userAvatar);

      // 账号已存在/新建成功都视为 ok
      res.json({
        code: 0,
        message: 'ok',
        data: { imported: result.success },
      });
    } catch (err) {
      next(err);
    }
  },
};

module.exports = imController;

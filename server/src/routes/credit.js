/**
 * 信誉分路由
 *
 * GET /api/credit — 我的信誉分 + 变动记录
 */

const { Router } = require('express');
const creditController = require('../controllers/credit');

const router = Router();

router.get('/', creditController.my);

module.exports = router;

/**
 * 校园二手交易小程序 — Express 服务端入口
 *
 * 职责：
 * - 初始化 Express 应用
 * - 注册全局中间件（安全/CORS/日志/限流/JWT）
 * - 挂载路由模块
 * - 全局错误处理
 * - 启动 HTTP 服务
 *
 * 启动方式：
 *   node src/app.js                    # 开发模式
 *   NODE_ENV=production node src/app.js # 生产模式
 */

const path = require('path');
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const config = require('./config');
const logger = require('./utils/logger');
const { healthCheck } = require('./models/db');

// ---- 中间件 ----
const auth = require('./middleware/auth');
const { globalLimiter } = require('./middleware/rate-limiter');
const errorHandler = require('./middleware/error-handler');
const accessLog = require('./middleware/access-log');
const { requestTimer } = require('./utils/perf');

// ---- 路由 ----
const authRoutes = require('./routes/auth');
const userRoutes = require('./routes/user');
const productRoutes = require('./routes/product');
const orderRoutes = require('./routes/order');
const reviewRoutes = require('./routes/review');
const reportRoutes = require('./routes/report');
const notificationRoutes = require('./routes/notification');
const creditRoutes = require('./routes/credit');
const uploadRoutes = require('./routes/upload');
const imRoutes = require('./routes/im');
const adminRoutes = require('./routes/admin');

// ---- 初始化 Express ----
const app = express();

// ============================================================
// 全局中间件（按执行顺序）
// ============================================================

// 安全头
app.use(helmet());

// CORS
app.use(cors({
  origin: config.corsOrigin,
  credentials: true,
}));

// Body 解析
app.use(express.json({ limit: '1mb' }));
app.use(express.urlencoded({ extended: false }));

// 请求耗时监控
app.use(requestTimer());

// 访问日志
app.use(accessLog);

// 静态文件（商品图片等公开资源，不经过 JWT 鉴权）
//
// helmet 全局设置了 Cross-Origin-Resource-Policy: same-origin 和
// Cross-Origin-Opener-Policy: same-origin，会阻止微信小程序 webview
// 跨域加载图片（小程序 webview 的 origin 与 localhost:3000 不同源）。
// 此处覆盖为 cross-origin，允许任意来源嵌入图片。
app.use('/images', (req, res, next) => {
  // 覆盖 helmet 的 same-origin 限制，允许微信小程序 webview 跨域嵌入图片
  res.setHeader('Cross-Origin-Resource-Policy', 'cross-origin');
  res.removeHeader('Cross-Origin-Opener-Policy');
  // CSP: 允许所有来源加载图片（小程序 webview origin 与 API server 不同源）
  res.setHeader('Content-Security-Policy', "default-src 'self'; img-src * data:; style-src 'self' 'unsafe-inline'");
  next();
});

app.use('/images', express.static(path.join(__dirname, '..', 'public', 'images'), {
  maxAge: '7d',
}));

// 全局限流
app.use('/api', globalLimiter);

// JWT 鉴权（login 和 health 在白名单中跳过）
app.use('/api', auth);

// ============================================================
// 路由注册
// ============================================================

app.get('/api/health', async (_req, res) => {
  try {
    await healthCheck();
    res.json({ code: 0, message: 'ok', data: { status: 'healthy', timestamp: new Date().toISOString() } });
  } catch (err) {
    logger.error('健康检查失败', { error: err.message, stack: err.stack });
    res.status(503).json({ code: 6999, message: '服务异常', data: null });
  }
});

app.use('/api/auth', authRoutes);
app.use('/api/users', userRoutes);
app.use('/api/products', productRoutes);
app.use('/api/orders', orderRoutes);
app.use('/api/reviews', reviewRoutes);
app.use('/api/reports', reportRoutes);
app.use('/api/notifications', notificationRoutes);
app.use('/api/credit', creditRoutes);
app.use('/api/upload', uploadRoutes);
app.use('/api/im', imRoutes);
app.use('/api/admin', adminRoutes);

// ============================================================
// 全局错误处理（必须在所有路由之后）
// ============================================================
app.use(errorHandler);

// ============================================================
// 启动服务
// ============================================================

// 仅在直接运行时启动（测试时不启动）
if (require.main === module) {
  const PORT = config.port;

  app.listen(PORT, () => {
    console.log(`\n🏫 校园二手交易平台 — 服务端`);
    console.log(`   环境: ${config.nodeEnv}`);
    console.log(`   端口: ${PORT}`);
    console.log(`   健康检查: http://localhost:${PORT}/api/health\n`);
  });
}

module.exports = app;

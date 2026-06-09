/**
 * PM2 生产部署配置
 *
 * 重要约束（技术架构文档 §一）：
 *   - 必须使用 fork 模式（instances: 1），不能用 cluster
 *   - 原因：node-cron 定时任务在 cluster 多进程中会重复执行
 *   - 多实例部署时仅主实例设 ENABLE_CRON=true
 *
 * 使用方式：
 *   pm2 start ecosystem.config.js                    # 启动
 *   pm2 reload ecosystem.config.js                    # 零停机重载
 *   pm2 stop campus-market                            # 停止
 *   pm2 logs campus-market                            # 查看日志
 *   pm2 save && pm2 startup                           # 设置开机自启
 */

module.exports = {
  apps: [
    {
      // ---- 基本信息 ----
      name: 'campus-market',
      script: 'src/app.js',

      // ---- 运行模式 ----
      exec_mode: 'fork',
      instances: 1,

      // ---- 环境变量 ----
      env: {
        NODE_ENV: 'production',
      },

      // ---- 日志 ----
      log_date_format: 'YYYY-MM-DD HH:mm:ss.SSS',
      error_file: './logs/pm2-error.log',
      out_file: './logs/pm2-out.log',
      merge_logs: true,
      max_size: '10M',
      retain: 7,

      // ---- 重启策略 ----
      autorestart: true,
      max_restarts: 10,
      restart_delay: 5000,
      min_uptime: '10s',
      max_memory_restart: '256M',
      kill_timeout: 10000,
      listen_timeout: 10000,

      // ---- 优雅退出 ----
      wait_ready: true,
      shutdown_with_message: true,
    },
  ],
};

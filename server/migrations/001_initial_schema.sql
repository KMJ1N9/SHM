-- ============================================================
-- 校园二手交易小程序 — 初始数据库 Schema
-- 版本: v1.0
-- 日期: 2026-06-05
-- 数据库: MySQL 8.0, InnoDB, utf8mb4
-- 表数: 14 张（10 核心业务表 + 2 归档表 + 1 迁移管理表 + 1 IM 重试表）
-- 索引: 28 个（24 BTREE + 1 FULLTEXT + 3 UNIQUE）
-- ============================================================

-- ============================================================
-- 建库（在 Navicat 中分别执行这两行）
-- ============================================================
-- CREATE DATABASE campus_market_dev  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- CREATE DATABASE campus_market_test DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ============================================================
-- UP: 正向迁移
-- ============================================================

-- ----------------------------------------
-- 1. users（用户表）
-- ----------------------------------------
CREATE TABLE users (
  id            INT PRIMARY KEY AUTO_INCREMENT,
  phone         VARCHAR(11) NOT NULL UNIQUE,
  nickname      VARCHAR(50) NOT NULL DEFAULT '',
  avatar        VARCHAR(500) DEFAULT '',
  class_name    VARCHAR(100) DEFAULT '',
  dorm_building VARCHAR(100) DEFAULT '',
  role          ENUM('user', 'cs', 'admin') NOT NULL DEFAULT 'user',
  status        ENUM('active', 'banned') NOT NULL DEFAULT 'active',
  token_version INT NOT NULL DEFAULT 1,
  credit_score  INT NOT NULL DEFAULT 100,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------
-- 2. products（商品表）
-- ----------------------------------------
CREATE TABLE products (
  id              INT PRIMARY KEY AUTO_INCREMENT,
  seller_id       INT NOT NULL,
  title           VARCHAR(200) NOT NULL,
  description     TEXT DEFAULT NULL,
  category        VARCHAR(50) NOT NULL,
  `condition`     VARCHAR(50) NOT NULL,
  original_price  DECIMAL(10,2) NOT NULL,
  price           DECIMAL(10,2) NOT NULL,
  trade_location  VARCHAR(200) NOT NULL,
  negotiable      TINYINT(1) NOT NULL DEFAULT 1,
  images          JSON DEFAULT NULL,
  status          ENUM('active','reserved','sold','off_shelf','deleted','frozen') NOT NULL DEFAULT 'active',
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (seller_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------
-- 3. orders（订单表）
-- ----------------------------------------
CREATE TABLE orders (
  id                INT PRIMARY KEY AUTO_INCREMENT,
  product_id        INT NOT NULL,
  buyer_id          INT NOT NULL,
  seller_id         INT NOT NULL,
  status            ENUM('pending','met','completed','cancelled','disputed','timeout') NOT NULL DEFAULT 'pending',
  cancelled_by      ENUM('buyer', 'seller') DEFAULT NULL,
  idempotent_key    VARCHAR(100) UNIQUE,
  product_snapshot  JSON NOT NULL,
  met_at            DATETIME DEFAULT NULL,
  confirmed_at      DATETIME DEFAULT NULL,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (product_id) REFERENCES products(id),
  FOREIGN KEY (buyer_id) REFERENCES users(id),
  FOREIGN KEY (seller_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------
-- 4. reviews（评价表）
-- ----------------------------------------
CREATE TABLE reviews (
  id                  INT PRIMARY KEY AUTO_INCREMENT,
  order_id            INT NOT NULL,
  reviewer_id         INT NOT NULL,
  reviewee_id         INT NOT NULL,
  communication_score TINYINT NOT NULL DEFAULT 5,
  punctuality_score   TINYINT NOT NULL DEFAULT 5,
  accuracy_score      TINYINT NOT NULL DEFAULT 5,
  comment             TEXT DEFAULT NULL,
  created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY unique_review (order_id, reviewer_id, reviewee_id),
  FOREIGN KEY (order_id) REFERENCES orders(id),
  FOREIGN KEY (reviewer_id) REFERENCES users(id),
  FOREIGN KEY (reviewee_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------
-- 5. reports（举报表）
-- ----------------------------------------
CREATE TABLE reports (
  id                INT PRIMARY KEY AUTO_INCREMENT,
  reporter_id       INT NOT NULL,
  reported_user_id  INT NOT NULL,
  product_id        INT DEFAULT NULL,
  order_id          INT DEFAULT NULL,
  type              VARCHAR(50) NOT NULL,
  description       TEXT NOT NULL,
  evidence_images   JSON DEFAULT NULL,
  status            ENUM('pending','processing','resolved') NOT NULL DEFAULT 'pending',
  resolution        TEXT DEFAULT NULL,
  deleted_at        DATETIME DEFAULT NULL,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  resolved_at       DATETIME DEFAULT NULL,
  FOREIGN KEY (reporter_id) REFERENCES users(id),
  FOREIGN KEY (reported_user_id) REFERENCES users(id),
  FOREIGN KEY (product_id) REFERENCES products(id),
  FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------
-- 6. admin_logs（管理操作审计表）
-- ----------------------------------------
CREATE TABLE admin_logs (
  id          INT PRIMARY KEY AUTO_INCREMENT,
  admin_id    INT NOT NULL,
  action      VARCHAR(50) NOT NULL,
  target_type VARCHAR(50) NOT NULL,
  target_id   INT NOT NULL,
  reason      TEXT DEFAULT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (admin_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------
-- 7. product_images（商品图片拆分表 — Phase 2）
-- ----------------------------------------
CREATE TABLE product_images (
  id          INT PRIMARY KEY AUTO_INCREMENT,
  product_id  INT NOT NULL,
  url         VARCHAR(500) NOT NULL,
  sort_order  INT NOT NULL DEFAULT 0,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------
-- 8. report_evidence（举报证据拆分表 — Phase 2）
-- ----------------------------------------
CREATE TABLE report_evidence (
  id          INT PRIMARY KEY AUTO_INCREMENT,
  report_id   INT NOT NULL,
  url         VARCHAR(500) NOT NULL,
  sort_order  INT NOT NULL DEFAULT 0,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (report_id) REFERENCES reports(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------
-- 9. notifications（通知中心 — Phase 4）
-- ----------------------------------------
CREATE TABLE notifications (
  id          INT PRIMARY KEY AUTO_INCREMENT,
  user_id     INT NOT NULL,
  type        VARCHAR(50) NOT NULL,
  title       VARCHAR(200) NOT NULL,
  content     TEXT NOT NULL,
  is_read     TINYINT(1) NOT NULL DEFAULT 0,
  metadata    JSON DEFAULT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------
-- 10. user_events（用户行为埋点 — Phase 4）
-- ----------------------------------------
CREATE TABLE user_events (
  id          INT PRIMARY KEY AUTO_INCREMENT,
  user_id     INT NOT NULL,
  event       VARCHAR(50) NOT NULL,
  metadata    JSON DEFAULT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------
-- 11. admin_logs_archive（管理日志归档表 — Phase 2）
-- ----------------------------------------
CREATE TABLE admin_logs_archive (
  id          INT PRIMARY KEY,
  admin_id    INT NOT NULL,
  action      VARCHAR(50) NOT NULL,
  target_type VARCHAR(50) NOT NULL,
  target_id   INT NOT NULL,
  reason      TEXT DEFAULT NULL,
  archived_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at  DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------
-- 12. reviews_archive（评价归档表 — Phase 2）
-- ----------------------------------------
CREATE TABLE reviews_archive (
  id                  INT PRIMARY KEY,
  order_id            INT NOT NULL,
  reviewer_id         INT NOT NULL,
  reviewee_id         INT NOT NULL,
  communication_score TINYINT NOT NULL DEFAULT 5,
  punctuality_score   TINYINT NOT NULL DEFAULT 5,
  accuracy_score      TINYINT NOT NULL DEFAULT 5,
  comment             TEXT DEFAULT NULL,
  archived_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at          DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------
-- 13. migrations（Schema 版本管理）
-- ----------------------------------------
CREATE TABLE migrations (
  version     INT PRIMARY KEY,
  name        VARCHAR(100) NOT NULL,
  applied_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------------------
-- 14. failed_system_messages（IM 系统消息失败重试 — Phase 8）
-- ----------------------------------------
CREATE TABLE failed_system_messages (
  id            INT PRIMARY KEY AUTO_INCREMENT,
  message_type  VARCHAR(32) NOT NULL,
  target_uid    VARCHAR(64) NOT NULL,
  payload       JSON NOT NULL,
  retry_count   INT NOT NULL DEFAULT 0,
  max_retries   INT NOT NULL DEFAULT 5,
  last_error    VARCHAR(512) DEFAULT NULL,
  status        VARCHAR(20) NOT NULL DEFAULT 'pending',
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 索引（建表后统一创建，避免外键索引重复定义）
-- ============================================================

-- products 表索引
CREATE INDEX idx_products_seller   ON products(seller_id);
CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_products_status   ON products(status);
CREATE INDEX idx_products_created  ON products(created_at DESC);

-- orders 表索引
CREATE INDEX idx_orders_buyer      ON orders(buyer_id);
CREATE INDEX idx_orders_seller     ON orders(seller_id);
CREATE INDEX idx_orders_product    ON orders(product_id);
CREATE INDEX idx_orders_status     ON orders(status);

-- reviews 表索引
CREATE INDEX idx_reviews_reviewee  ON reviews(reviewee_id);
CREATE INDEX idx_reviews_order     ON reviews(order_id);

-- reports 表索引
CREATE INDEX idx_reports_status    ON reports(status);
CREATE INDEX idx_reports_deleted   ON reports(deleted_at);

-- admin_logs 表索引
CREATE INDEX idx_admin_logs_admin   ON admin_logs(admin_id);
CREATE INDEX idx_admin_logs_target  ON admin_logs(target_type, target_id);
CREATE INDEX idx_admin_logs_created ON admin_logs(created_at DESC);

-- 拆分表索引
CREATE INDEX idx_product_images_pid  ON product_images(product_id);
CREATE INDEX idx_report_evidence_rid ON report_evidence(report_id);

-- notifications 表索引
CREATE INDEX idx_notifications_user    ON notifications(user_id, is_read);
CREATE INDEX idx_notifications_created ON notifications(created_at DESC);

-- user_events 表索引
CREATE INDEX idx_user_events_user  ON user_events(user_id);
CREATE INDEX idx_user_events_event ON user_events(event, created_at DESC);

-- IM 重试表索引
CREATE INDEX idx_fsm_status ON failed_system_messages(status, created_at);
CREATE INDEX idx_fsm_target ON failed_system_messages(target_uid, status);

-- ============================================================
-- 全文索引（Phase 5：MySQL InnoDB FULLTEXT，ngram 中文分词）
-- ============================================================
ALTER TABLE products ADD FULLTEXT INDEX ft_products (title, description) WITH PARSER ngram;

-- ============================================================
-- 迁移记录（标记本次迁移已执行）
-- ============================================================
INSERT INTO migrations (version, name) VALUES (1, 'initial_schema');

-- ============================================================
-- DOWN: 逆向迁移（按外键依赖顺序反向删除）
-- ============================================================
-- DROP TABLE IF EXISTS failed_system_messages;
-- DROP TABLE IF EXISTS user_events;
-- DROP TABLE IF EXISTS notifications;
-- DROP TABLE IF EXISTS report_evidence;
-- DROP TABLE IF EXISTS product_images;
-- DROP TABLE IF EXISTS admin_logs_archive;
-- DROP TABLE IF EXISTS reviews_archive;
-- DROP TABLE IF EXISTS reports;
-- DROP TABLE IF EXISTS reviews;
-- DROP TABLE IF EXISTS orders;
-- DROP TABLE IF EXISTS products;
-- DROP TABLE IF EXISTS migrations;
-- DROP TABLE IF EXISTS users;

/**
 * 初始迁移：创建所有业务表
 *
 * 表清单（14 张）：
 *   users, products, orders, reviews, reports,
 *   admin_logs, product_images, report_evidence,
 *   notifications, user_events,
 *   admin_logs_archive, reviews_archive,
 *   migrations（由 migrate.js 自动创建）
 *
 * 引擎：InnoDB，字符集：utf8mb4
 */

async function up(db) {
  // ---- users ----
  await db.query(`
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
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);

  // ---- products ----
  await db.query(`
    CREATE TABLE products (
      id              INT PRIMARY KEY AUTO_INCREMENT,
      seller_id       INT NOT NULL,
      title           VARCHAR(200) NOT NULL,
      description     TEXT DEFAULT NULL,
      category        VARCHAR(50) NOT NULL,
      \`condition\`     VARCHAR(50) NOT NULL,
      original_price  DECIMAL(10,2) NOT NULL,
      price           DECIMAL(10,2) NOT NULL,
      trade_location  VARCHAR(200) NOT NULL,
      negotiable      TINYINT(1) NOT NULL DEFAULT 1,
      images          JSON DEFAULT NULL,
      status          ENUM('active','reserved','sold','off_shelf','deleted','frozen') NOT NULL DEFAULT 'active',
      created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      FOREIGN KEY (seller_id) REFERENCES users(id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);

  // ---- orders ----
  await db.query(`
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
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);

  // ---- reviews ----
  await db.query(`
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
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);

  // ---- reports ----
  await db.query(`
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
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);

  // ---- admin_logs ----
  await db.query(`
    CREATE TABLE admin_logs (
      id          INT PRIMARY KEY AUTO_INCREMENT,
      admin_id    INT NOT NULL,
      action      VARCHAR(50) NOT NULL,
      target_type VARCHAR(50) NOT NULL,
      target_id   INT NOT NULL,
      reason      TEXT DEFAULT NULL,
      created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (admin_id) REFERENCES users(id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);

  // ---- product_images（Phase 2） ----
  await db.query(`
    CREATE TABLE product_images (
      id          INT PRIMARY KEY AUTO_INCREMENT,
      product_id  INT NOT NULL,
      url         VARCHAR(500) NOT NULL,
      sort_order  INT NOT NULL DEFAULT 0,
      created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);

  // ---- report_evidence（Phase 2） ----
  await db.query(`
    CREATE TABLE report_evidence (
      id          INT PRIMARY KEY AUTO_INCREMENT,
      report_id   INT NOT NULL,
      url         VARCHAR(500) NOT NULL,
      sort_order  INT NOT NULL DEFAULT 0,
      created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (report_id) REFERENCES reports(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);

  // ---- notifications ----
  await db.query(`
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
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);

  // ---- user_events（Phase 4） ----
  await db.query(`
    CREATE TABLE user_events (
      id          INT PRIMARY KEY AUTO_INCREMENT,
      user_id     INT NOT NULL,
      event       VARCHAR(50) NOT NULL,
      metadata    JSON DEFAULT NULL,
      created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (user_id) REFERENCES users(id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);

  // ---- admin_logs_archive（Phase 2） ----
  await db.query(`
    CREATE TABLE admin_logs_archive (
      id          INT PRIMARY KEY,
      admin_id    INT NOT NULL,
      action      VARCHAR(50) NOT NULL,
      target_type VARCHAR(50) NOT NULL,
      target_id   INT NOT NULL,
      reason      TEXT DEFAULT NULL,
      created_at  DATETIME NOT NULL,
      archived_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);

  // ---- reviews_archive（Phase 2） ----
  await db.query(`
    CREATE TABLE reviews_archive (
      id                  INT PRIMARY KEY,
      order_id            INT NOT NULL,
      reviewer_id         INT NOT NULL,
      reviewee_id         INT NOT NULL,
      communication_score TINYINT NOT NULL,
      punctuality_score   TINYINT NOT NULL,
      accuracy_score      TINYINT NOT NULL,
      comment             TEXT DEFAULT NULL,
      created_at          DATETIME NOT NULL,
      archived_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);

  // ---- 全文索引（ngram parser，支持中文） ----
  await db.query(`
    ALTER TABLE products ADD FULLTEXT INDEX ft_products (title, description) WITH PARSER ngram
  `);
}

async function down(db) {
  await db.query('DROP TABLE IF EXISTS user_events');
  await db.query('DROP TABLE IF EXISTS notifications');
  await db.query('DROP TABLE IF EXISTS report_evidence');
  await db.query('DROP TABLE IF EXISTS product_images');
  await db.query('DROP TABLE IF EXISTS admin_logs_archive');
  await db.query('DROP TABLE IF EXISTS reviews_archive');
  await db.query('DROP TABLE IF EXISTS admin_logs');
  await db.query('DROP TABLE IF EXISTS reports');
  await db.query('DROP TABLE IF EXISTS reviews');
  await db.query('DROP TABLE IF EXISTS orders');
  await db.query('DROP TABLE IF EXISTS products');
  await db.query('DROP TABLE IF EXISTS users');
}

module.exports = { up, down };

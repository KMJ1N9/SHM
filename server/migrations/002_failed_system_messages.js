/**
 * 迁移 002：IM 系统消息失败重试表
 *
 * 用于暂存推送失败的系统消息，支持自动重试，保证最终一致性。
 * 触发场景：IM REST API 调用超时、网络故障、对方不在线等。
 */

async function up(db) {
  await db.query(`
    CREATE TABLE failed_system_messages (
      id            INT PRIMARY KEY AUTO_INCREMENT,
      message_type  VARCHAR(32) NOT NULL COMMENT '消息类型: order_update/review_remind/report_result/system_notice',
      target_uid    VARCHAR(64) NOT NULL COMMENT '目标用户 IM UID',
      payload       JSON NOT NULL COMMENT '消息体 JSON',
      retry_count   INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
      max_retries   INT NOT NULL DEFAULT 5 COMMENT '最大重试次数',
      last_error    VARCHAR(512) DEFAULT NULL COMMENT '最近一次失败原因',
      status        VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态: pending/retrying/failed/sent',
      created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  `);

  // 索引：按状态+时间查询待重试消息、按目标用户查询
  await db.query('CREATE INDEX idx_fsm_status ON failed_system_messages(status, created_at)');
  await db.query('CREATE INDEX idx_fsm_target ON failed_system_messages(target_uid, status)');
}

async function down(db) {
  await db.query('DROP TABLE IF EXISTS failed_system_messages');
}

module.exports = { up, down };

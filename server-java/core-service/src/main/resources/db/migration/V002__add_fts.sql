-- ============================================================
-- UP: 正向迁移 — 商品全文索引（中文 ngram parser）
-- 数据来源：docs/技术架构文档.md §4.2 products 表 FULLTEXT 索引
-- 幂等策略：存储过程 + DECLARE CONTINUE HANDLER FOR 1061 (Duplicate key)
--   兼容 MySQL 8.0 任意小版本（IF EXISTS 是 8.0.29+ 才支持的语法）
-- ============================================================
DELIMITER //

CREATE PROCEDURE add_fts_if_needed()
BEGIN
    DECLARE CONTINUE HANDLER FOR 1061 BEGIN END;
    ALTER TABLE products ADD FULLTEXT INDEX ft_products (title, description) WITH PARSER ngram;
END //

DELIMITER ;

CALL add_fts_if_needed();
DROP PROCEDURE add_fts_if_needed;

-- ============================================================
-- DOWN: 逆向迁移
-- ============================================================
-- ALTER TABLE products DROP INDEX ft_products;

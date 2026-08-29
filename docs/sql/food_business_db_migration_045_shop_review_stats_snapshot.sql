USE food_business_db;

DROP PROCEDURE IF EXISTS add_shop_review_stats_snapshot_columns;

DELIMITER //

CREATE PROCEDURE add_shop_review_stats_snapshot_columns()
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'shop_review'
      AND COLUMN_NAME = 'shop_comments_before'
  ) THEN
    ALTER TABLE shop_review
      ADD COLUMN shop_comments_before INT DEFAULT NULL COMMENT 'shop comments before review' AFTER review_status;
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'shop_review'
      AND COLUMN_NAME = 'shop_score_before'
  ) THEN
    ALTER TABLE shop_review
      ADD COLUMN shop_score_before INT DEFAULT NULL COMMENT 'shop score before review' AFTER shop_comments_before;
  END IF;
END//

DELIMITER ;

CALL add_shop_review_stats_snapshot_columns();

DROP PROCEDURE IF EXISTS add_shop_review_stats_snapshot_columns;

ALTER TABLE orders MODIFY COLUMN status
  ENUM('pending','met_pending','met','completed','cancelled','disputed','timeout') NOT NULL DEFAULT 'pending';
ALTER TABLE orders ADD COLUMN met_initiated_by INT DEFAULT NULL;

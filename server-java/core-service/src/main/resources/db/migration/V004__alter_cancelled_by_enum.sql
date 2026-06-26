ALTER TABLE orders MODIFY COLUMN cancelled_by ENUM('buyer', 'seller', 'system') DEFAULT NULL;

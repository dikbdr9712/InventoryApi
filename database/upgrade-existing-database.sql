-- =====================================================================================
-- DP DrukBazaars: bring a database made BEFORE 2 Oct 2026 up to database version 1.
--
-- Who needs it: a database the application created by itself before Flyway was added
-- (for example the current inventorydb on the development computer). A NEW server does
-- NOT need it: Flyway builds everything there (see README.md).
--
-- What it does: adds the 4 new tables and 29 new columns (all optional, so existing rows
-- are untouched). Nothing is deleted or changed. Safe to run more than once: each table
-- and column is only added when it is missing.
--
-- How:   mysql -u root -p inventorydb < upgrade-existing-database.sql
--   or   MySQL Workbench: open this file, choose the inventorydb schema, run all (lightning icon).
-- Then start the application: Flyway marks the database as version 1 and checks every table.
--
-- Made by comparing inventorydb with a fresh version 1 database on 2 Oct 2026, then tested on a
-- copy of inventorydb's structure (the application started and accepted every table).
-- =====================================================================================

SET NAMES utf8mb4;

-- ---------- New tables ----------

CREATE TABLE IF NOT EXISTS `delivery_areas` (
  `active` bit(1) NOT NULL,
  `latitude` double NOT NULL,
  `longitude` double NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(80) NOT NULL,
  `town` varchar(80) NOT NULL,
  `updated_by` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `notifications` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `read_at` datetime(6) DEFAULT NULL,
  `type` varchar(40) NOT NULL,
  `user_email` varchar(120) NOT NULL,
  `title` varchar(160) NOT NULL,
  `link` varchar(200) DEFAULT NULL,
  `body` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notif_user` (`user_email`,`created_at`),
  KEY `idx_notif_unread` (`user_email`,`read_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `password_reset_tokens` (
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `used_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `request_ip` varchar(64) DEFAULT NULL,
  `token_hash` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKajre85ybxavf1tt4omkrs5p6g` (`token_hash`),
  KEY `idx_reset_user` (`user_id`),
  KEY `idx_reset_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `payment_intents` (
  `amount` decimal(12,2) NOT NULL,
  `currency` varchar(3) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `status` varchar(12) NOT NULL,
  `provider` varchar(20) NOT NULL,
  `reference` varchar(40) NOT NULL,
  `provider_reference` varchar(80) DEFAULT NULL,
  `customer_email` varchar(120) NOT NULL,
  `message` varchar(300) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKo1ehkg4yqlntu1aws7whygs8l` (`reference`),
  KEY `idx_intent_order` (`order_id`),
  KEY `idx_intent_status` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------- New columns ----------
-- MySQL 8 has no "ADD COLUMN IF NOT EXISTS", so a small helper checks first.
DROP PROCEDURE IF EXISTS drukbazaars_add_column;
DELIMITER //
CREATE PROCEDURE drukbazaars_add_column(IN tbl VARCHAR(64), IN col VARCHAR(64), IN def VARCHAR(200))
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = tbl AND column_name = col) THEN
    SET @ddl = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN `', col, '` ', def);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END //
DELIMITER ;

-- item_master
CALL drukbazaars_add_column('item_master', 'delivery_size', 'varchar(10) NULL');
-- marketplace_settings
CALL drukbazaars_add_column('marketplace_settings', 'bulky_base_fee', 'decimal(10,2) NULL');
CALL drukbazaars_add_column('marketplace_settings', 'bulky_per_km', 'decimal(10,2) NULL');
CALL drukbazaars_add_column('marketplace_settings', 'included_km', 'decimal(5,1) NULL');
CALL drukbazaars_add_column('marketplace_settings', 'large_base_fee', 'decimal(10,2) NULL');
CALL drukbazaars_add_column('marketplace_settings', 'large_per_km', 'decimal(10,2) NULL');
CALL drukbazaars_add_column('marketplace_settings', 'max_distance_km', 'decimal(6,1) NULL');
CALL drukbazaars_add_column('marketplace_settings', 'medium_base_fee', 'decimal(10,2) NULL');
CALL drukbazaars_add_column('marketplace_settings', 'medium_per_km', 'decimal(10,2) NULL');
CALL drukbazaars_add_column('marketplace_settings', 'rider_share_percent', 'decimal(5,2) NULL');
CALL drukbazaars_add_column('marketplace_settings', 'shop_latitude', 'double NULL');
CALL drukbazaars_add_column('marketplace_settings', 'shop_longitude', 'double NULL');
CALL drukbazaars_add_column('marketplace_settings', 'small_base_fee', 'decimal(10,2) NULL');
CALL drukbazaars_add_column('marketplace_settings', 'small_per_km', 'decimal(10,2) NULL');
CALL drukbazaars_add_column('marketplace_settings', 'unknown_distance_km', 'decimal(5,1) NULL');
CALL drukbazaars_add_column('marketplace_settings', 'shop_address', 'varchar(300) NULL');
-- order_packages
CALL drukbazaars_add_column('order_packages', 'distance_estimated', 'bit(1) NULL');
CALL drukbazaars_add_column('order_packages', 'distance_km', 'decimal(6,1) NULL');
CALL drukbazaars_add_column('order_packages', 'drop_latitude', 'double NULL');
CALL drukbazaars_add_column('order_packages', 'drop_longitude', 'double NULL');
CALL drukbazaars_add_column('order_packages', 'pickup_latitude', 'double NULL');
CALL drukbazaars_add_column('order_packages', 'pickup_longitude', 'double NULL');
CALL drukbazaars_add_column('order_packages', 'delivery_size', 'varchar(10) NULL');
-- orders
CALL drukbazaars_add_column('orders', 'drop_latitude', 'double NULL');
CALL drukbazaars_add_column('orders', 'drop_longitude', 'double NULL');
CALL drukbazaars_add_column('orders', 'drop_location', 'varchar(120) NULL');
-- seller_profiles
CALL drukbazaars_add_column('seller_profiles', 'pickup_latitude', 'double NULL');
CALL drukbazaars_add_column('seller_profiles', 'pickup_longitude', 'double NULL');
-- users
CALL drukbazaars_add_column('users', 'password_changed_at', 'datetime(6) NULL');

DROP PROCEDURE drukbazaars_add_column;

-- Done. Start the application now.

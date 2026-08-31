-- FIKA 外卖配送模块
--
-- 目标：支持顾客维护多个收货地址、登录配送员抢单，以及配送状态流转。
-- 适用：MySQL 5.7+/8.0+；请先 USE coffee_order_pro，再执行本脚本。
-- 本脚本不包含配送费字段，后续接入第三方平台时可向 delivery_order 增加平台与费用字段。

-- 兼容较早 user_order 结构：外卖订单必须能区分履约方式。
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'user_order' AND column_name = 'fulfillment_type') = 0,
    'ALTER TABLE user_order ADD COLUMN fulfillment_type VARCHAR(20) NOT NULL DEFAULT ''PICKUP'' COMMENT ''履约方式：PICKUP/DINE_IN/DELIVERY''',
    'SELECT 1'
);
PREPARE fika_delivery_stmt FROM @ddl;
EXECUTE fika_delivery_stmt;
DEALLOCATE PREPARE fika_delivery_stmt;

CREATE TABLE IF NOT EXISTS delivery_address (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    label VARCHAR(30) NOT NULL COMMENT '地址标签：家/公司/学校等',
    receiver_name VARCHAR(50) NOT NULL,
    receiver_phone VARCHAR(30) NOT NULL,
    detail_address VARCHAR(200) NOT NULL,
    is_default TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_delivery_address_user (user_id, is_default, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='顾客外卖收货地址';

CREATE TABLE IF NOT EXISTS delivery_rider (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    phone VARCHAR(30) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_delivery_rider_username (username),
    KEY idx_delivery_rider_status (status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外卖配送员账号';

CREATE TABLE IF NOT EXISTS delivery_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    store_name VARCHAR(120) NOT NULL,
    amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    item_summary VARCHAR(500) NOT NULL,
    note VARCHAR(500) NULL,
    address_label VARCHAR(30) NOT NULL,
    receiver_name VARCHAR(50) NOT NULL,
    receiver_phone VARCHAR(30) NOT NULL,
    detail_address VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/CLAIMED/PICKED_UP/DELIVERING/DELIVERED/CANCELED',
    rider_id BIGINT NULL,
    rider_name VARCHAR(50) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_at DATETIME NULL,
    picked_up_at DATETIME NULL,
    delivered_at DATETIME NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_delivery_order_order (order_id),
    KEY idx_delivery_order_available (status, created_at, id),
    KEY idx_delivery_order_rider (rider_id, status, updated_at),
    KEY idx_delivery_order_user (user_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外卖配送单';

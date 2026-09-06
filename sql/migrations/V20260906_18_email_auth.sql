-- FIKA 邮箱验证码登录与注册。
-- 依赖：V20260901_16_profile_center.sql（已为 coffee_user 增加 email 字段）。
-- 本脚本可重复执行；不会删除既有用户数据。

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'coffee_user' AND column_name = 'email') = 0,
    'ALTER TABLE coffee_user ADD COLUMN email VARCHAR(120) NULL COMMENT ''邮箱''',
    'SELECT 1'
);
PREPARE fika_email_auth_stmt FROM @ddl;
EXECUTE fika_email_auth_stmt;
DEALLOCATE PREPARE fika_email_auth_stmt;

-- NULL 邮箱可以重复；非空邮箱必须只归属一个账户。
-- 若历史数据中已有重复非空邮箱，这条语句会明确失败，请先人工确认归属后再执行。
SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'coffee_user'
        AND index_name = 'uk_coffee_user_email') = 0,
    'ALTER TABLE coffee_user ADD UNIQUE KEY uk_coffee_user_email (email)',
    'SELECT 1'
);
PREPARE fika_email_auth_stmt FROM @ddl;
EXECUTE fika_email_auth_stmt;
DEALLOCATE PREPARE fika_email_auth_stmt;

CREATE TABLE IF NOT EXISTS email_verification_code (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(120) NOT NULL,
    purpose VARCHAR(16) NOT NULL,
    code_hash VARCHAR(100) NOT NULL,
    expires_at DATETIME NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email_verification_code (email, purpose),
    KEY idx_email_verification_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮箱登录与注册验证码';

-- 限流状态与验证码分表：验证码在使用、过期或错误次数耗尽后会删除；
-- 限流状态只保存短期计数，用于保证 Redis 不可用/丢失时也无法绕过冷却规则。
CREATE TABLE IF NOT EXISTS email_verification_rate_limit (
    email VARCHAR(120) NOT NULL,
    next_send_at DATETIME NOT NULL,
    window_started_at DATETIME NOT NULL,
    send_count INT NOT NULL DEFAULT 0,
    lock_until DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (email),
    KEY idx_email_verification_rate_cleanup (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮箱验证码发送限流短期状态';

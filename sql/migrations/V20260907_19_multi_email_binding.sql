-- FIKA 多邮箱绑定。
-- 一个顾客最多绑定 3 个邮箱；一个邮箱通过唯一索引只能属于一个顾客。
-- 依赖：V20260906_18_email_auth.sql。
-- 本脚本可重复执行；会把旧版 coffee_user.email 迁移为首选邮箱，不删除原字段。

CREATE TABLE IF NOT EXISTS coffee_user_email (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    email VARCHAR(120) NOT NULL,
    is_primary TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为首选邮箱',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_coffee_user_email_address (email),
    UNIQUE KEY uk_coffee_user_email_user_address (user_id, email),
    KEY idx_coffee_user_email_user (user_id),
    CONSTRAINT fk_coffee_user_email_user FOREIGN KEY (user_id)
        REFERENCES coffee_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='顾客邮箱绑定关系';

-- 旧版本只有 coffee_user.email，迁移后把它作为该用户的首选邮箱。
-- INSERT IGNORE 使脚本重复执行时不会产生重复关系。
INSERT IGNORE INTO coffee_user_email (user_id, email, is_primary)
SELECT id, LOWER(TRIM(email)), 1
FROM coffee_user
WHERE email IS NOT NULL AND TRIM(email) <> '';

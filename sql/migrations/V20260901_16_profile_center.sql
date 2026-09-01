-- FIKA 三端个人资料中心
--
-- 顾客、商家、配送员资料分别归属各自账号表；图片只保存站内相对 URL，
-- 文件落盘目录由 Web 层统一管理。脚本可重复执行，不删除也不覆盖历史资料。
-- 适用：MySQL 5.7+/8.0+；请在目标库中执行。

-- ======================== 顾客资料 ========================

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'coffee_user' AND column_name = 'avatar_url') = 0,
    'ALTER TABLE coffee_user ADD COLUMN avatar_url VARCHAR(500) NULL COMMENT ''顾客头像 URL''',
    'SELECT 1'
);
PREPARE fika_profile_stmt FROM @ddl;
EXECUTE fika_profile_stmt;
DEALLOCATE PREPARE fika_profile_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'coffee_user' AND column_name = 'phone') = 0,
    'ALTER TABLE coffee_user ADD COLUMN phone VARCHAR(30) NULL COMMENT ''顾客联系电话''',
    'SELECT 1'
);
PREPARE fika_profile_stmt FROM @ddl;
EXECUTE fika_profile_stmt;
DEALLOCATE PREPARE fika_profile_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'coffee_user' AND column_name = 'birthday') = 0,
    'ALTER TABLE coffee_user ADD COLUMN birthday DATE NULL COMMENT ''顾客生日''',
    'SELECT 1'
);
PREPARE fika_profile_stmt FROM @ddl;
EXECUTE fika_profile_stmt;
DEALLOCATE PREPARE fika_profile_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'coffee_user' AND column_name = 'wechat_id') = 0,
    'ALTER TABLE coffee_user ADD COLUMN wechat_id VARCHAR(80) NULL COMMENT ''微信号''',
    'SELECT 1'
);
PREPARE fika_profile_stmt FROM @ddl;
EXECUTE fika_profile_stmt;
DEALLOCATE PREPARE fika_profile_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'coffee_user' AND column_name = 'qq_number') = 0,
    'ALTER TABLE coffee_user ADD COLUMN qq_number VARCHAR(20) NULL COMMENT ''QQ 号''',
    'SELECT 1'
);
PREPARE fika_profile_stmt FROM @ddl;
EXECUTE fika_profile_stmt;
DEALLOCATE PREPARE fika_profile_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'coffee_user' AND column_name = 'email') = 0,
    'ALTER TABLE coffee_user ADD COLUMN email VARCHAR(120) NULL COMMENT ''邮箱''',
    'SELECT 1'
);
PREPARE fika_profile_stmt FROM @ddl;
EXECUTE fika_profile_stmt;
DEALLOCATE PREPARE fika_profile_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'coffee_user' AND column_name = 'other_info') = 0,
    'ALTER TABLE coffee_user ADD COLUMN other_info VARCHAR(500) NULL COMMENT ''其他个人信息''',
    'SELECT 1'
);
PREPARE fika_profile_stmt FROM @ddl;
EXECUTE fika_profile_stmt;
DEALLOCATE PREPARE fika_profile_stmt;

-- ======================== 商家资料 ========================

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'merchant' AND column_name = 'avatar_url') = 0,
    'ALTER TABLE merchant ADD COLUMN avatar_url VARCHAR(500) NULL COMMENT ''经营者头像 URL''',
    'SELECT 1'
);
PREPARE fika_profile_stmt FROM @ddl;
EXECUTE fika_profile_stmt;
DEALLOCATE PREPARE fika_profile_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'merchant' AND column_name = 'operator_name') = 0,
    'ALTER TABLE merchant ADD COLUMN operator_name VARCHAR(50) NULL COMMENT ''经营者姓名''',
    'SELECT 1'
);
PREPARE fika_profile_stmt FROM @ddl;
EXECUTE fika_profile_stmt;
DEALLOCATE PREPARE fika_profile_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'merchant' AND column_name = 'email') = 0,
    'ALTER TABLE merchant ADD COLUMN email VARCHAR(120) NULL COMMENT ''商家邮箱''',
    'SELECT 1'
);
PREPARE fika_profile_stmt FROM @ddl;
EXECUTE fika_profile_stmt;
DEALLOCATE PREPARE fika_profile_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'merchant' AND column_name = 'business_license_no') = 0,
    'ALTER TABLE merchant ADD COLUMN business_license_no VARCHAR(80) NULL COMMENT ''经营许可证编号''',
    'SELECT 1'
);
PREPARE fika_profile_stmt FROM @ddl;
EXECUTE fika_profile_stmt;
DEALLOCATE PREPARE fika_profile_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'merchant' AND column_name = 'business_license_url') = 0,
    'ALTER TABLE merchant ADD COLUMN business_license_url VARCHAR(500) NULL COMMENT ''经营许可证图片 URL''',
    'SELECT 1'
);
PREPARE fika_profile_stmt FROM @ddl;
EXECUTE fika_profile_stmt;
DEALLOCATE PREPARE fika_profile_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'merchant' AND column_name = 'other_info') = 0,
    'ALTER TABLE merchant ADD COLUMN other_info VARCHAR(500) NULL COMMENT ''商家其他信息''',
    'SELECT 1'
);
PREPARE fika_profile_stmt FROM @ddl;
EXECUTE fika_profile_stmt;
DEALLOCATE PREPARE fika_profile_stmt;

-- ======================== 配送员资料 ========================

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'delivery_rider' AND column_name = 'avatar_url') = 0,
    'ALTER TABLE delivery_rider ADD COLUMN avatar_url VARCHAR(500) NULL COMMENT ''配送员头像 URL''',
    'SELECT 1'
);
PREPARE fika_profile_stmt FROM @ddl;
EXECUTE fika_profile_stmt;
DEALLOCATE PREPARE fika_profile_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'delivery_rider' AND column_name = 'birthday') = 0,
    'ALTER TABLE delivery_rider ADD COLUMN birthday DATE NULL COMMENT ''配送员生日''',
    'SELECT 1'
);
PREPARE fika_profile_stmt FROM @ddl;
EXECUTE fika_profile_stmt;
DEALLOCATE PREPARE fika_profile_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'delivery_rider' AND column_name = 'email') = 0,
    'ALTER TABLE delivery_rider ADD COLUMN email VARCHAR(120) NULL COMMENT ''配送员邮箱''',
    'SELECT 1'
);
PREPARE fika_profile_stmt FROM @ddl;
EXECUTE fika_profile_stmt;
DEALLOCATE PREPARE fika_profile_stmt;

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'delivery_rider' AND column_name = 'other_info') = 0,
    'ALTER TABLE delivery_rider ADD COLUMN other_info VARCHAR(500) NULL COMMENT ''配送员其他信息''',
    'SELECT 1'
);
PREPARE fika_profile_stmt FROM @ddl;
EXECUTE fika_profile_stmt;
DEALLOCATE PREPARE fika_profile_stmt;

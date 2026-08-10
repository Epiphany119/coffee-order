-- coffee-module-location-biz: execute once on existing databases
CREATE TABLE IF NOT EXISTS user_location (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  latitude DECIMAL(10,7) NOT NULL,
  longitude DECIMAL(10,7) NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_user_location_user (user_id),
  KEY idx_user_location_updated (updated_at),
  CONSTRAINT fk_user_location_user FOREIGN KEY (user_id) REFERENCES coffee_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录用户最近一次定位';

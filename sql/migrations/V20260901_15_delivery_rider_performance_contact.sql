-- FIKA 骑手业绩与隐私/虚拟电话框架
--
-- 业绩接口只按 delivery_order.rider_id 统计本人配送单量。
-- receiver_phone 仍作为顾客下单时的地址快照保存在配送单中，但骑手接口不会返回该字段。
-- 虚拟电话当前由应用层占位服务生成一次性 relayId，不落真实号码；后续接入中介服务时替换实现即可。
-- 适用：MySQL 5.7+/8.0+；请在 V20260831_14_delivery_order_lifecycle.sql 后执行。

SET @ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = DATABASE()
        AND table_name = 'delivery_order'
        AND index_name = 'idx_delivery_order_rider_delivered') = 0,
    'ALTER TABLE delivery_order ADD KEY idx_delivery_order_rider_delivered (rider_id, delivered_at, status)',
    'SELECT 1'
);
PREPARE fika_delivery_rider_performance_stmt FROM @ddl;
EXECUTE fika_delivery_rider_performance_stmt;
DEALLOCATE PREPARE fika_delivery_rider_performance_stmt;

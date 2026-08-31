package com.coffee.module.delivery.api.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 配送任务状态变化事件。
 *
 * <p>配送模块只负责配送任务自身的 CAS 更新；由 web 组合层监听该事件，
 * 将状态同步回订单主表，避免配送模块反向依赖订单实现。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryOrderStatusChangedEvent {
    private Long deliveryOrderId;
    private Long orderId;
    private String previousStatus;
    private String status;
    private Long riderId;
    private LocalDateTime occurredOn;

    public static DeliveryOrderStatusChangedEvent changed(Long deliveryOrderId, Long orderId,
                                                           String previousStatus, String status,
                                                           Long riderId) {
        return new DeliveryOrderStatusChangedEvent(deliveryOrderId, orderId, previousStatus, status,
                riderId, LocalDateTime.now());
    }
}

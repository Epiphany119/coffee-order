package com.coffee.web.order;

import com.coffee.module.delivery.api.DeliveryService;
import com.coffee.module.delivery.api.event.DeliveryOrderStatusChangedEvent;
import com.coffee.module.order.api.OrderService;
import com.coffee.module.order.biz.domain.event.OrderDomainEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 订单模块与配送模块之间的组合层状态同步。
 *
 * <p>两个事件监听器都是同步执行的，调用方的数据库事务会覆盖主订单和配送单。
 * 任意一侧的状态校验失败都会抛异常，使另一侧一起回滚。</p>
 */
@Component
public class DeliveryOrderLifecycleListener {

    private final DeliveryService deliveryService;
    private final OrderService orderService;

    public DeliveryOrderLifecycleListener(DeliveryService deliveryService, OrderService orderService) {
        this.deliveryService = deliveryService;
        this.orderService = orderService;
    }

    @EventListener
    public void onOrderStatusChanged(OrderDomainEvent event) {
        if (event == null || !"ORDER_STATUS_CHANGED".equals(event.getEventType())) return;
        if ("READY_FOR_DELIVERY".equals(event.getStatus())) {
            // 主订单只有在商家完成制作后才会到这里，发布失败会回滚商家完成操作。
            deliveryService.publishForRider(event.getOrderId());
        } else if ("CANCELED".equals(event.getStatus())) {
            // 当前产品只允许取消未支付订单，因此不会取消已经被骑手接走的配送单。
            deliveryService.cancelForOrder(event.getOrderId());
        }
    }

    @EventListener
    public void onDeliveryStatusChanged(DeliveryOrderStatusChangedEvent event) {
        if (event == null || event.getOrderId() == null || event.getStatus() == null) return;
        switch (event.getStatus()) {
            case "OPEN", "CLAIMED", "PICKED_UP", "DELIVERING", "DELIVERED" ->
                    orderService.updateDeliveryOrderStatus(event.getOrderId(), event.getStatus());
            default -> {
                // CANCELED 目前只会在未支付主订单取消时由订单事件触发，无需反向推进主订单。
            }
        }
    }
}

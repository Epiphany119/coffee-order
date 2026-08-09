package com.coffee.module.order.api;

/**
 * 订单支付服务（供支付模块跨模块调用：支付成功回写订单状态）
 */
public interface OrderPaymentService {

    /** 校验订单可支付：订单存在且状态为 UNPAID；否则抛出 ServiceException */
    void checkPayable(Long orderId);

    /** 支付成功回调：订单状态 UNPAID → PENDING；幂等（已是 PENDING 或后续状态则不重复变更） */
    void markPaid(Long orderId);
}

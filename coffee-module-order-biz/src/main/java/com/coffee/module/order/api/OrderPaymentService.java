package com.coffee.module.order.api;

/**
 * 订单支付服务（供支付模块跨模块调用：支付成功回写订单状态）
 */
public interface OrderPaymentService {

    /** 校验订单可支付：订单存在且状态为 UNPAID；否则抛出 ServiceException */
    void checkPayable(Long orderId);

    /** 支付成功回调：订单状态 UNPAID → PENDING；返回订单是否仍接受支付结果。 */
    boolean markPaid(Long orderId);
}

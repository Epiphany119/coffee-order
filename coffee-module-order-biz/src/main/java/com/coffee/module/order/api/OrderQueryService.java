package com.coffee.module.order.api;

import com.coffee.module.order.api.dto.OrderBrief;

/**
 * 订单只读查询接口（供其他模块跨模块调用，如售后模块校验订单归属）
 */
public interface OrderQueryService {

    /** 按订单 id 查简要信息；订单不存在返回 null */
    OrderBrief getOrderBrief(Long orderId);

    /** 按订单 id + 下单用户校验归属；不存在或不属于该用户返回 null */
    OrderBrief getOrderBriefForUser(Long orderId, Long userId);
}

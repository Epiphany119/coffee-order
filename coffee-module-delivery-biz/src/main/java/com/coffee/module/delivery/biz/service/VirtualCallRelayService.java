package com.coffee.module.delivery.biz.service;

import com.coffee.module.delivery.api.DeliveryContactParty;
import com.coffee.module.delivery.api.dto.VirtualCallResponse;
import com.coffee.module.delivery.biz.infra.persistence.DeliveryOrderPO;

/**
 * 虚拟电话中介适配边界。
 *
 * <p>后续接入第三方号码池时只替换该接口实现，订单、权限和骑手端按钮不需要改动。</p>
 */
public interface VirtualCallRelayService {

    VirtualCallResponse start(DeliveryOrderPO deliveryOrder, DeliveryContactParty party);
}

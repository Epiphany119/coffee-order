package com.coffee.module.delivery.biz.service;

import com.coffee.module.delivery.api.DeliveryContactParty;
import com.coffee.module.delivery.api.dto.VirtualCallResponse;
import com.coffee.module.delivery.biz.infra.persistence.DeliveryOrderPO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/** 默认占位实现：生成一次性 relayId，但不发起真实电话。 */
@Component
public class PendingVirtualCallRelayService implements VirtualCallRelayService {

    @Override
    public VirtualCallResponse start(DeliveryOrderPO deliveryOrder, DeliveryContactParty party) {
        return VirtualCallResponse.notConfigured(
                "relay-" + UUID.randomUUID(),
                LocalDateTime.now().plusMinutes(5));
    }
}

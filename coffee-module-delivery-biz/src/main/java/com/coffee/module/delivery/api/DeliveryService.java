package com.coffee.module.delivery.api;

import com.coffee.module.delivery.api.dto.DeliveryAddressRequest;
import com.coffee.module.delivery.api.dto.DeliveryAddressResponse;
import com.coffee.module.delivery.api.dto.DeliveryOrderCreateRequest;
import com.coffee.module.delivery.api.dto.DeliveryOrderResponse;
import com.coffee.module.delivery.api.dto.DeliveryRiderLoginRequest;
import com.coffee.module.delivery.api.dto.DeliveryRiderRegisterRequest;
import com.coffee.module.delivery.api.dto.DeliveryRiderResponse;

import java.util.List;

/**
 * 外卖模块对外服务：顾客地址、配送单和配送员抢单。
 *
 * <p>配送费暂不属于当前模型，后续接入第三方平台时可以在配送单上增加
 * provider、fee 和 externalOrderNo，而不影响顾客订单主表。</p>
 */
public interface DeliveryService {

    List<DeliveryAddressResponse> listAddresses(Long userId);

    DeliveryAddressResponse createAddress(Long userId, DeliveryAddressRequest request);

    DeliveryAddressResponse updateAddress(Long userId, Long addressId, DeliveryAddressRequest request);

    void deleteAddress(Long userId, Long addressId);

    DeliveryOrderResponse createDeliveryOrder(DeliveryOrderCreateRequest request);

    List<DeliveryOrderResponse> listAvailableOrders();

    List<DeliveryOrderResponse> listRiderOrders(Long riderId);

    List<DeliveryOrderResponse> listCustomerOrders(Long userId);

    DeliveryOrderResponse claimOrder(Long deliveryOrderId, Long riderId);

    DeliveryOrderResponse action(Long deliveryOrderId, Long riderId, String action);

    DeliveryRiderResponse register(DeliveryRiderRegisterRequest request);

    DeliveryRiderResponse login(DeliveryRiderLoginRequest request);

    DeliveryRiderResponse getRider(Long riderId);
}

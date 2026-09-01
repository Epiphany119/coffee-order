package com.coffee.module.delivery.api;

import com.coffee.module.delivery.api.dto.DeliveryAddressRequest;
import com.coffee.module.delivery.api.dto.DeliveryAddressResponse;
import com.coffee.module.delivery.api.dto.DeliveryOrderCreateRequest;
import com.coffee.module.delivery.api.dto.DeliveryOrderResponse;
import com.coffee.module.delivery.api.dto.DeliveryRiderLoginRequest;
import com.coffee.module.delivery.api.dto.DeliveryRiderPerformanceResponse;
import com.coffee.module.delivery.api.dto.DeliveryRiderProfileUpdateRequest;
import com.coffee.module.delivery.api.dto.DeliveryRiderRegisterRequest;
import com.coffee.module.delivery.api.dto.DeliveryRiderResponse;
import com.coffee.module.delivery.api.dto.VirtualCallResponse;

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

    /** 商家完成制作后，将配送单从等待商家状态发布到骑手待抢列表。 */
    void publishForRider(Long orderId);

    /** 未支付订单取消时，关闭尚未被骑手接走的配送任务。 */
    void cancelForOrder(Long orderId);

    List<DeliveryOrderResponse> listAvailableOrders();

    List<DeliveryOrderResponse> listRiderOrders(Long riderId);

    List<DeliveryOrderResponse> listCustomerOrders(Long userId);

    DeliveryOrderResponse claimOrder(Long deliveryOrderId, Long riderId);

    DeliveryOrderResponse action(Long deliveryOrderId, Long riderId, String action);

    DeliveryRiderResponse register(DeliveryRiderRegisterRequest request);

    DeliveryRiderResponse login(DeliveryRiderLoginRequest request);

    DeliveryRiderResponse getRider(Long riderId);

    /** 更新配送员个人资料。 */
    DeliveryRiderResponse updateRiderProfile(Long riderId, DeliveryRiderProfileUpdateRequest request);

    /** 更新配送员头像地址。 */
    DeliveryRiderResponse updateRiderAvatar(Long riderId, String avatarUrl);

    /** 获取当前配送员自己的业绩看板数据。 */
    DeliveryRiderPerformanceResponse getRiderPerformance(Long riderId);

    /** 获取当前配送员指定范围的业绩看板数据：7d/14d/28d/12w。 */
    DeliveryRiderPerformanceResponse getRiderPerformance(Long riderId, String range);

    /** 顾客通过订单主键请求联系骑手；真实号码由后续虚拟电话服务中介。 */
    VirtualCallResponse requestCustomerCall(Long orderId, Long userId);

    /** 骑手通过配送单主键请求联系顾客；永远不返回顾客真实手机号。 */
    VirtualCallResponse requestRiderCall(Long deliveryOrderId, Long riderId);
}

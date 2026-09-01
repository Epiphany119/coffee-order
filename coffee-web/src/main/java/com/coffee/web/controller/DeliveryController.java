package com.coffee.web.controller;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.common.core.result.Result;
import com.coffee.module.delivery.api.DeliveryService;
import com.coffee.module.delivery.api.dto.DeliveryAddressRequest;
import com.coffee.module.delivery.api.dto.DeliveryAddressResponse;
import com.coffee.module.delivery.api.dto.DeliveryOrderResponse;
import com.coffee.module.delivery.api.dto.DeliveryRiderLoginRequest;
import com.coffee.module.delivery.api.dto.DeliveryRiderPerformanceResponse;
import com.coffee.module.delivery.api.dto.DeliveryRiderRegisterRequest;
import com.coffee.module.delivery.api.dto.DeliveryRiderResponse;
import com.coffee.module.delivery.api.dto.VirtualCallResponse;
import com.coffee.web.security.AccessGuard;
import com.coffee.web.security.RequestIdentity;
import com.coffee.web.security.TokenService;
import com.coffee.web.storage.LocalProfileImageStorage;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 外卖配送 HTTP 接口：顾客地址、配送员登录和抢单窗口。 */
@RestController
@RequestMapping("/api/delivery")
public class DeliveryController {
    private final DeliveryService deliveryService;
    private final TokenService tokenService;
    private final LocalProfileImageStorage profileImageStorage;

    public DeliveryController(DeliveryService deliveryService, TokenService tokenService,
                              LocalProfileImageStorage profileImageStorage) {
        this.deliveryService = deliveryService;
        this.tokenService = tokenService;
        this.profileImageStorage = profileImageStorage;
    }

    // ======================== 顾客地址 ========================

    @GetMapping("/addresses")
    public Result<List<DeliveryAddressResponse>> listAddresses() {
        return Result.success(deliveryService.listAddresses(currentUserId()));
    }

    @PostMapping("/addresses")
    public Result<DeliveryAddressResponse> createAddress(@RequestBody DeliveryAddressRequest request) {
        return Result.success(deliveryService.createAddress(currentUserId(), request));
    }

    @PutMapping("/addresses/{id}")
    public Result<DeliveryAddressResponse> updateAddress(@PathVariable Long id,
                                                         @RequestBody DeliveryAddressRequest request) {
        return Result.success(deliveryService.updateAddress(currentUserId(), id, request));
    }

    @DeleteMapping("/addresses/{id}")
    public Result<Void> deleteAddress(@PathVariable Long id) {
        deliveryService.deleteAddress(currentUserId(), id);
        return Result.success();
    }

    // ======================== 顾客外卖订单 ========================

    @GetMapping("/orders/mine")
    public Result<List<DeliveryOrderResponse>> listCustomerOrders() {
        return Result.success(deliveryService.listCustomerOrders(currentUserId()));
    }

    // ======================== 配送员账号 ========================

    @PostMapping("/riders/register")
    public Result<DeliveryRiderResponse> register(@RequestBody DeliveryRiderRegisterRequest request) {
        DeliveryRiderResponse response = deliveryService.register(request);
        issueRiderToken(response);
        return Result.success(response);
    }

    @PostMapping("/riders/login")
    public Result<DeliveryRiderResponse> login(@RequestBody DeliveryRiderLoginRequest request) {
        DeliveryRiderResponse response = deliveryService.login(request);
        issueRiderToken(response);
        return Result.success(response);
    }

    @GetMapping("/riders/me")
    public Result<DeliveryRiderResponse> me() {
        return Result.success(deliveryService.getRider(AccessGuard.currentRiderId()));
    }

    /** 更新配送员个人资料。 */
    @PutMapping("/riders/me/profile")
    public Result<DeliveryRiderResponse> updateRiderProfile(
            @RequestBody com.coffee.module.delivery.api.dto.DeliveryRiderProfileUpdateRequest request) {
        return Result.success(deliveryService.updateRiderProfile(AccessGuard.currentRiderId(), request));
    }

    /** 上传配送员头像。 */
    @PostMapping("/riders/me/avatar")
    public Result<DeliveryRiderResponse> uploadRiderAvatar(@RequestParam("file") MultipartFile file) {
        Long riderId = AccessGuard.currentRiderId();
        String url = profileImageStorage.store("rider", riderId, file);
        return Result.success(deliveryService.updateRiderAvatar(riderId, url));
    }

    @GetMapping("/rider/performance")
    public Result<DeliveryRiderPerformanceResponse> performance(
            @RequestParam(value = "range", required = false, defaultValue = "7d") String range) {
        return Result.success(deliveryService.getRiderPerformance(AccessGuard.currentRiderId(), range));
    }

    // ======================== 配送员抢单工作台 ========================

    @GetMapping("/rider/orders/available")
    public Result<List<DeliveryOrderResponse>> availableOrders() {
        AccessGuard.currentRiderId();
        return Result.success(deliveryService.listAvailableOrders());
    }

    @GetMapping("/rider/orders/mine")
    public Result<List<DeliveryOrderResponse>> riderOrders() {
        return Result.success(deliveryService.listRiderOrders(AccessGuard.currentRiderId()));
    }

    @PostMapping("/rider/orders/{id}/claim")
    public Result<DeliveryOrderResponse> claimOrder(@PathVariable Long id) {
        return Result.success(deliveryService.claimOrder(id, AccessGuard.currentRiderId()));
    }

    @PostMapping("/rider/orders/{id}/action")
    public Result<DeliveryOrderResponse> action(@PathVariable Long id,
                                                @RequestParam String action) {
        return Result.success(deliveryService.action(id, AccessGuard.currentRiderId(), action));
    }

    /** 骑手联系顾客：只返回虚拟电话中介会话，不返回顾客真实手机号。 */
    @PostMapping("/rider/orders/{id}/contact/customer")
    public Result<VirtualCallResponse> riderContactCustomer(@PathVariable Long id) {
        return Result.success(deliveryService.requestRiderCall(id, AccessGuard.currentRiderId()));
    }

    /** 顾客联系骑手：按订单主键查找配送单，真实电话由中介服务处理。 */
    @PostMapping("/orders/{orderId}/contact/rider")
    public Result<VirtualCallResponse> customerContactRider(@PathVariable Long orderId) {
        return Result.success(deliveryService.requestCustomerCall(orderId, currentUserId()));
    }

    private Long currentUserId() {
        RequestIdentity identity = AccessGuard.currentIdentity();
        if (identity.kind() != RequestIdentity.Kind.USER || identity.id() == null) {
            throw new ServiceException(403, "外卖地址仅支持顾客账号");
        }
        return identity.id();
    }

    private void issueRiderToken(DeliveryRiderResponse response) {
        if (response != null && response.isSuccess() && response.getId() != null) {
            response.setAccessToken(tokenService.issueRider(response.getId()));
        }
    }
}

package com.coffee.web.controller;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.module.order.api.OrderService;
import com.coffee.module.store.api.MerchantService;
import com.coffee.module.store.api.StoreService;
import com.coffee.module.store.api.dto.MerchantLoginRequest;
import com.coffee.module.store.api.dto.MerchantRegisterRequest;
import com.coffee.module.store.api.dto.MerchantResponse;
import com.coffee.module.store.api.dto.MerchantProfileUpdateRequest;
import com.coffee.module.store.api.dto.MerchantPasswordChangeRequest;
import com.coffee.module.store.api.dto.StoreResponse;
import com.coffee.web.security.AccessGuard;
import com.coffee.web.security.TokenService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家控制器（注册 / 登录 / 我的店铺 / 经营数据）
 */
@RestController
@RequestMapping("/api/merchant")
public class MerchantController {

    private final MerchantService merchantService;
    private final StoreService storeService;
    private final OrderService orderService;
    private final TokenService tokenService;

    public MerchantController(MerchantService merchantService, StoreService storeService, OrderService orderService,
                              TokenService tokenService) {
        this.merchantService = merchantService;
        this.storeService = storeService;
        this.orderService = orderService;
        this.tokenService = tokenService;
    }

    /** 商家注册 */
    @PostMapping("/register")
    public MerchantResponse register(@RequestBody MerchantRegisterRequest request) {
        return withToken(merchantService.register(request));
    }

    /** 商家登录 */
    @PostMapping("/login")
    public MerchantResponse login(@RequestBody MerchantLoginRequest request) {
        return withToken(merchantService.login(request));
    }

    /** 商家信息 */
    @GetMapping("/{id}")
    public MerchantResponse detail(@PathVariable("id") Long id) {
        AccessGuard.requireMerchant(id);
        return merchantService.getMerchant(id);
    }

    @PutMapping("/{id}/profile")
    public MerchantResponse updateProfile(@PathVariable("id") Long id,
                                          @RequestBody MerchantProfileUpdateRequest request) {
        AccessGuard.requireMerchant(id);
        return merchantService.updateProfile(id, request);
    }

    @PutMapping("/{id}/password")
    public Map<String, Object> changePassword(@PathVariable("id") Long id,
                                               @RequestBody MerchantPasswordChangeRequest request) {
        AccessGuard.requireMerchant(id);
        merchantService.changePassword(id, request);
        return Map.of("success", true, "message", "密码已修改，请重新登录");
    }

    /** 商家名下店铺（登录后"我的店铺"） */
    @GetMapping("/{id}/stores")
    public List<StoreResponse> myStores(@PathVariable("id") Long id) {
        AccessGuard.requireMerchant(id);
        return storeService.listByMerchant(id);
    }

    /** 经营数据 dashboard：今日营业额/订单数/待处理/营业额柱状图(按范围)/最近订单 */
    @GetMapping("/{merchantId}/dashboard")
    public Map<String, Object> dashboard(@PathVariable("merchantId") Long merchantId,
                                         @RequestParam(value = "range", required = false, defaultValue = "7d") String range) {
        AccessGuard.requireMerchant(merchantId);
        List<StoreResponse> stores = storeService.listByMerchant(merchantId);
        if (stores.isEmpty()) {
            throw new ServiceException(404, "该商家尚未入驻店铺");
        }
        StoreResponse store = stores.get(0);
        Long storeId = store.getStoreId();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("store", store);
        result.putAll(orderService.getStoreStats(storeId));
        result.put("sales", orderService.getSalesStats(storeId, range));
        result.put("hotProducts", orderService.getHotProducts(storeId));
        result.put("recentOrders", orderService.getStoreOrders(storeId, null).stream().limit(5).toList());
        return result;
    }

    private MerchantResponse withToken(MerchantResponse response) {
        if (response != null && response.isSuccess() && response.getId() != null) {
            response.setAccessToken(tokenService.issueMerchant(response.getId()));
        }
        return response;
    }
}

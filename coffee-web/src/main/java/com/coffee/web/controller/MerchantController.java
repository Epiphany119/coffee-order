package com.coffee.web.controller;

import com.coffee.common.core.exception.ServiceException;
import com.coffee.module.order.api.OrderService;
import com.coffee.module.store.api.MerchantService;
import com.coffee.module.store.api.StoreService;
import com.coffee.module.store.api.dto.MerchantLoginRequest;
import com.coffee.module.store.api.dto.MerchantRegisterRequest;
import com.coffee.module.store.api.dto.MerchantResponse;
import com.coffee.module.store.api.dto.StoreResponse;
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

    public MerchantController(MerchantService merchantService, StoreService storeService, OrderService orderService) {
        this.merchantService = merchantService;
        this.storeService = storeService;
        this.orderService = orderService;
    }

    /** 商家注册 */
    @PostMapping("/register")
    public MerchantResponse register(@RequestBody MerchantRegisterRequest request) {
        return merchantService.register(request);
    }

    /** 商家登录 */
    @PostMapping("/login")
    public MerchantResponse login(@RequestBody MerchantLoginRequest request) {
        return merchantService.login(request);
    }

    /** 商家信息 */
    @GetMapping("/{id}")
    public MerchantResponse detail(@PathVariable("id") Long id) {
        return merchantService.getMerchant(id);
    }

    /** 商家名下店铺（登录后"我的店铺"） */
    @GetMapping("/{id}/stores")
    public List<StoreResponse> myStores(@PathVariable("id") Long id) {
        return storeService.listByMerchant(id);
    }

    /** 经营数据 dashboard：今日营业额/订单数/待处理/近7天销售/最近订单 */
    @GetMapping("/{merchantId}/dashboard")
    public Map<String, Object> dashboard(@PathVariable("merchantId") Long merchantId) {
        List<StoreResponse> stores = storeService.listByMerchant(merchantId);
        if (stores.isEmpty()) {
            throw new ServiceException(404, "该商家尚未入驻店铺");
        }
        StoreResponse store = stores.get(0);
        Long storeId = store.getStoreId();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("store", store);
        result.putAll(orderService.getStoreStats(storeId));
        result.put("recentOrders", orderService.getStoreOrders(storeId, null).stream().limit(5).toList());
        return result;
    }
}

package com.coffee.web.controller;

import com.coffee.module.store.api.MerchantService;
import com.coffee.module.store.api.StoreService;
import com.coffee.module.store.api.dto.MerchantLoginRequest;
import com.coffee.module.store.api.dto.MerchantRegisterRequest;
import com.coffee.module.store.api.dto.MerchantResponse;
import com.coffee.module.store.api.dto.StoreResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商家控制器（注册 / 登录 / 我的店铺）
 */
@RestController
@RequestMapping("/api/merchant")
public class MerchantController {

    private final MerchantService merchantService;
    private final StoreService storeService;

    public MerchantController(MerchantService merchantService, StoreService storeService) {
        this.merchantService = merchantService;
        this.storeService = storeService;
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
}

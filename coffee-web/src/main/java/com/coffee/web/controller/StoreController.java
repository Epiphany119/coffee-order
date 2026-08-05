package com.coffee.web.controller;

import com.coffee.module.store.api.StoreService;
import com.coffee.module.store.api.dto.StoreRequest;
import com.coffee.module.store.api.dto.StoreResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 店铺控制器（店铺管理 / 用户端选店）
 */
@RestController
@RequestMapping("/api/store")
public class StoreController {

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    /** 创建店铺（商家建店 / 管理端） */
    @PostMapping
    public StoreResponse create(@RequestBody StoreRequest request) {
        return storeService.createStore(request);
    }

    /** 全部店铺列表（用户端选店） */
    @GetMapping("/list")
    public List<StoreResponse> list() {
        return storeService.listStores();
    }

    /** 可入驻店铺列表（商家入驻选择：21 家种子店中未入驻的） */
    @GetMapping("/available")
    public List<StoreResponse> available() {
        return storeService.listAvailableStores();
    }

    /** 店铺详情 */
    @GetMapping("/{id}")
    public StoreResponse detail(@PathVariable("id") Long id) {
        return storeService.getStore(id);
    }

    /** 更新店铺（店名/地址/电话/营业时间/状态） */
    @PutMapping("/{id}")
    public StoreResponse update(@PathVariable("id") Long id, @RequestBody StoreRequest request) {
        return storeService.updateStore(id, request);
    }

    /** 删除店铺 */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        storeService.deleteStore(id);
    }

    /** 绑定商家到店铺 */
    @PostMapping("/{id}/bind")
    public StoreResponse bind(@PathVariable("id") Long id,
                              @RequestParam("merchantId") Long merchantId) {
        return storeService.bindMerchant(id, merchantId);
    }
}

package com.coffee.web.controller;

import com.coffee.module.aftersales.api.AfterSaleService;
import com.coffee.module.aftersales.api.dto.AfterSaleRequest;
import com.coffee.module.aftersales.api.dto.AfterSaleResponse;
import com.coffee.module.aftersales.api.dto.FeedbackRequest;
import com.coffee.module.aftersales.api.dto.FeedbackResponse;
import com.coffee.common.core.result.Result;
import com.coffee.module.aftersales.api.dto.AfterSaleProcessRequest;
import com.coffee.module.store.api.StoreService;
import com.coffee.web.security.AccessGuard;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 售后控制器（售后单 / 订单反馈）
 */
@RestController
@RequestMapping("/api")
public class AfterSaleController {

    private final AfterSaleService afterSaleService;
    private final StoreService storeService;

    public AfterSaleController(AfterSaleService afterSaleService, StoreService storeService) {
        this.afterSaleService = afterSaleService;
        this.storeService = storeService;
    }

    /** 创建售后单（仅已完成订单） */
    @PostMapping("/after-sale")
    public Result<AfterSaleResponse> create(@RequestBody AfterSaleRequest request) {
        AccessGuard.requireUser(request.getUserId());
        return Result.success(afterSaleService.createAfterSale(request));
    }

    /** 我的售后单列表 */
    @GetMapping("/after-sale/user/{userId}")
    public Result<List<AfterSaleResponse>> list(@PathVariable("userId") Long userId) {
        AccessGuard.requireUser(userId);
        return Result.success(afterSaleService.listAfterSales(userId));
    }

    /** 商家售后工作台：仅查询当前商家店铺的售后单。 */
    @GetMapping("/merchant/after-sales")
    public Result<List<AfterSaleResponse>> listForMerchant(@RequestParam Long storeId,
                                                            @RequestParam(required = false) String status) {
        requireStoreOwner(storeId);
        return Result.success(afterSaleService.listAfterSalesByStore(storeId, status));
    }

    /** 商家处理售后单。 */
    @PutMapping("/merchant/after-sales/{id}")
    public Result<AfterSaleResponse> process(@PathVariable Long id, @RequestParam Long storeId,
                                             @RequestBody AfterSaleProcessRequest request) {
        requireStoreOwner(storeId);
        return Result.success(afterSaleService.processAfterSale(id, storeId, request));
    }

    /** 提交订单反馈（仅已完成订单） */
    @PostMapping("/after-sale/feedback")
    public Result<FeedbackResponse> createFeedback(@RequestBody FeedbackRequest request) {
        AccessGuard.requireUser(request.getUserId());
        return Result.success(afterSaleService.createFeedback(request));
    }

    /** 我的反馈列表 */
    @GetMapping("/after-sale/feedback/user/{userId}")
    public Result<List<FeedbackResponse>> listFeedbacks(@PathVariable("userId") Long userId) {
        AccessGuard.requireUser(userId);
        return Result.success(afterSaleService.listFeedbacks(userId));
    }

    /** 按订单查反馈列表（商家端订单明细展示用） */
    @GetMapping("/after-sale/feedback/order/{orderId}")
    public Result<List<FeedbackResponse>> listFeedbacksByOrder(@PathVariable("orderId") Long orderId) {
        return Result.success(afterSaleService.listFeedbacksByOrder(orderId));
    }

    /** 按商品 id 查反馈列表（点餐界面商品下方展示用） */
    @GetMapping("/after-sale/feedback/product/{productId}")
    public Result<List<FeedbackResponse>> listFeedbacksByProduct(@PathVariable("productId") Long productId) {
        return Result.success(afterSaleService.listFeedbacksByProduct(productId));
    }

    private void requireStoreOwner(Long storeId) {
        Long merchantId = AccessGuard.currentMerchantId();
        boolean ownsStore = storeService.listByMerchant(merchantId).stream()
                .anyMatch(store -> storeId.equals(store.getStoreId()));
        if (!ownsStore) throw new com.coffee.common.core.exception.ServiceException(403, "无权操作其他商家的售后单");
    }
}

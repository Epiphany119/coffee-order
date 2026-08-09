package com.coffee.marketing.controller;

import com.coffee.module.marketing.api.FlashSaleService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/** 内部服务入口；公网仍经 Gateway 统一鉴权后路由。 */
@RestController
@RequestMapping("/internal/flash-sales")
public class InternalFlashSaleController {
    private final FlashSaleService flashSaleService;
    public InternalFlashSaleController(FlashSaleService flashSaleService) { this.flashSaleService = flashSaleService; }
    @GetMapping("/current") public List<Map<String,Object>> current(@RequestParam Long storeId) { return flashSaleService.listCurrent(storeId); }
}

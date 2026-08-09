package com.coffee.web.controller;

import com.coffee.common.core.result.Result;
import com.coffee.module.menu.api.MenuService;
import com.coffee.module.menu.api.dto.MenuItemDTO;
import com.coffee.module.order.api.TopupService;
import com.coffee.module.order.api.dto.TopupProgressDTO;
import com.coffee.web.security.AccessGuard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 凑单控制器（购物袋满减进度条 + 凑单推荐）
 */
@RestController
@RequestMapping("/api/topup")
public class TopupController {

    private final TopupService topupService;
    private final MenuService menuService;

    public TopupController(TopupService topupService, MenuService menuService) {
        this.topupService = topupService;
        this.menuService = menuService;
    }

    /**
     * 凑单进度：购物袋金额距最近满减门槛还差多少
     *
     * @param userId     登录用户 id（游客不传，只看固定权益券）
     * @param amount     购物袋金额（会员折后、券前）
     * @param couponCode 已选优惠券编码（可空）
     */
    @GetMapping("/progress")
    public Result<TopupProgressDTO> progress(@RequestParam(required = false) Long userId,
                                             @RequestParam(required = false) Double amount,
                                             @RequestParam(required = false) String couponCode) {
        if (userId != null) AccessGuard.requireUser(userId);
        return Result.success(topupService.getTopupProgress(userId, amount, couponCode));
    }

    /**
     * 凑单推荐列表：该店可用凑单品（topup=1），仅推最低可买价 ≤ 还差金额的，按最低价升序
     */
    @GetMapping("/products")
    public Result<List<MenuItemDTO>> products(@RequestParam Long storeId,
                                              @RequestParam(required = false) Double maxPrice) {
        return Result.success(menuService.listTopupProducts(storeId, maxPrice != null ? maxPrice : 0));
    }
}

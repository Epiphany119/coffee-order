package com.coffee.web.controller;

import com.coffee.module.marketing.api.FlashSaleService;
import com.coffee.web.security.AccessGuard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/flash-sales")
public class FlashSaleController {
    private final FlashSaleService flashSaleService;
    public FlashSaleController(FlashSaleService flashSaleService) { this.flashSaleService = flashSaleService; }

    @GetMapping("/current")
    public List<Map<String, Object>> current(@RequestParam Long storeId) {
        return flashSaleService.listCurrent(storeId);
    }

    @GetMapping("/claims")
    public List<Map<String, Object>> claims(@RequestParam(required = false) Long userId,
                                             @RequestParam(required = false) String guestId) {
        requireOneIdentity(userId, guestId);
        if (userId != null) AccessGuard.requireUser(userId);
        if (guestId != null && !guestId.isBlank()) AccessGuard.requireGuest(guestId);
        return flashSaleService.listClaims(userId, guestId);
    }

    @PostMapping("/{activityId}/claim")
    public Map<String, Object> claim(@PathVariable Long activityId, @RequestBody ClaimRequest request) {
        if (request == null) throw new com.coffee.common.core.exception.ServiceException(400, "请求不能为空");
        requireOneIdentity(request.userId, request.guestId);
        if (request.userId != null) AccessGuard.requireUser(request.userId);
        if (request.guestId != null && !request.guestId.isBlank()) AccessGuard.requireGuest(request.guestId);
        return flashSaleService.claim(activityId, request.userId, request.guestId);
    }

    private void requireOneIdentity(Long userId, String guestId) {
        boolean hasGuest = guestId != null && !guestId.isBlank();
        if ((userId == null) == !hasGuest) {
            throw new com.coffee.common.core.exception.ServiceException(400, "请提供一种有效的用户或游客身份");
        }
    }

    public static class ClaimRequest { public Long userId; public String guestId; }
}

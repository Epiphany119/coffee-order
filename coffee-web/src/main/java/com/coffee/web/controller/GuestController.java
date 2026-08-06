package com.coffee.web.controller;

import com.coffee.module.auth.api.GuestService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 游客会话控制器（未登录用户身份签发，数据按 guestId 入库隔离）
 */
@RestController
@RequestMapping("/api/guest")
public class GuestController {

    private final GuestService guestService;

    public GuestController(GuestService guestService) {
        this.guestService = guestService;
    }

    /** 签发游客身份（前端内存持有，不落浏览器存储） */
    @PostMapping("/session")
    public Map<String, Object> createSession() {
        String guestId = guestService.createGuestSession();
        return Map.of("success", true, "guestId", guestId);
    }
}

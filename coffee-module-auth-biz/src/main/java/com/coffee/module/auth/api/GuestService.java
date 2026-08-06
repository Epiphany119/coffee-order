package com.coffee.module.auth.api;

/**
 * 游客会话服务 API
 */
public interface GuestService {

    /**
     * 签发游客身份（每次调用生成新 guestId 并落库）
     */
    String createGuestSession();
}

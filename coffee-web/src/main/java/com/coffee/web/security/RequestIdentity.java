package com.coffee.web.security;

/**
 * 已由 {@link TokenAuthenticationInterceptor} 验证的请求身份。
 *
 * <p>身份只在当前 HTTP 请求线程内有效，业务层不得把前端传入的 userId/merchantId
 * 当作授权依据。</p>
 */
public record RequestIdentity(Kind kind, Long id, String guestId) {
    public enum Kind { USER, MERCHANT, GUEST }
}

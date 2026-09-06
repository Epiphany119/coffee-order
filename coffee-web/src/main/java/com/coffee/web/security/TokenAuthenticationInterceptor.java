package com.coffee.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 解析 Authorization: Bearer <token>，将经校验的身份放入请求上下文。 */
@Component
public class TokenAuthenticationInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(TokenAuthenticationInterceptor.class);

    private final TokenService tokenService;

    public TokenAuthenticationInterceptor(TokenService tokenService) { this.tokenService = tokenService; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        RequestIdentityHolder.clear();
        // 认证入口不应因浏览器自动附带的过期 Token 而被阻断；这些接口自身
        // 不依赖请求身份，成功后会签发一张新 Token。受保护的用户资料、订单等
        // 路径仍会走下面的严格校验。
        if (isPublicSessionEndpoint(request)) {
            return true;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null && !authorization.isBlank()) {
            if (!authorization.startsWith("Bearer ")) {
                log.warn("Invalid Authorization header format: {}", request.getRequestURI());
                return true;
            }
            try {
                RequestIdentityHolder.set(tokenService.verify(authorization.substring(7).trim()));
            } catch (Exception e) {
                log.debug("Token verification failed for {}: {}", request.getRequestURI(), e.getMessage());
                if (!isPublicResource(request)) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "无效的登录凭证");
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isPublicResource(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) return false;
        String path = request.getRequestURI();
        return "/api/menu".equals(path)
                || "/api/store/list".equals(path)
                || "/api/store/open".equals(path)
                || "/api/store/available".equals(path)
                || path.matches("/api/store/\\d+")
                || "/api/seat/resolve".equals(path)
                || "/api/flash-sales/current".equals(path)
                || "/api/location/recommend".equals(path)
                || "/api/membership/level-rules".equals(path)
                || "/api/membership/redeem-items".equals(path)
                || "/api/discovery/search".equals(path)
                || "/api/topup/products".equals(path);
    }

    private boolean isPublicSessionEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("/api/auth/login-challenge".equals(path)) return true;
        if (!"POST".equalsIgnoreCase(request.getMethod())) return false;
        if (path.startsWith("/api/pay/callback/")) return true;
        return "/api/auth/login".equals(path)
                || "/api/auth/register".equals(path)
                || "/api/auth/email/send-code".equals(path)
                || "/api/auth/email/login".equals(path)
                || "/api/auth/email/register".equals(path)
                || "/api/auth/forgot-password".equals(path)
                || "/api/auth/reset-password".equals(path)
                || "/api/merchant/login".equals(path)
                || "/api/merchant/register".equals(path)
                || "/api/delivery/riders/login".equals(path)
                || "/api/delivery/riders/register".equals(path)
                || "/api/guest/session".equals(path);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        RequestIdentityHolder.clear();
    }
}

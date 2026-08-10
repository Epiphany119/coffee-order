package com.coffee.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 解析 Authorization: Bearer &lt;token&gt;，将经校验的身份放入请求上下文。 */
@Component
public class TokenAuthenticationInterceptor implements HandlerInterceptor {
    private final TokenService tokenService;

    public TokenAuthenticationInterceptor(TokenService tokenService) { this.tokenService = tokenService; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 认证入口不应因浏览器自动附带的过期 Token 而被阻断；这些接口自身
        // 不依赖请求身份，成功后会签发一张新 Token。受保护的用户资料、订单等
        // 路径仍会走下面的严格校验。
        if (isPublicSessionEndpoint(request)) {
            return true;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null && !authorization.isBlank()) {
            if (!authorization.startsWith("Bearer ")) {
                throw new com.coffee.common.core.exception.ServiceException(401, "登录凭证格式错误");
            }
            RequestIdentityHolder.set(tokenService.verify(authorization.substring(7).trim()));
        }
        return true;
    }

    private boolean isPublicSessionEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("/api/auth/login-challenge".equals(path)) return true;
        if (!"POST".equalsIgnoreCase(request.getMethod())) return false;
        return "/api/auth/login".equals(path)
                || "/api/auth/register".equals(path)
                || "/api/auth/forgot-password".equals(path)
                || "/api/auth/reset-password".equals(path)
                || "/api/merchant/login".equals(path)
                || "/api/merchant/register".equals(path)
                || "/api/guest/session".equals(path);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        RequestIdentityHolder.clear();
    }
}

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
        String authorization = request.getHeader("Authorization");
        if (authorization != null && !authorization.isBlank()) {
            if (!authorization.startsWith("Bearer ")) {
                throw new com.coffee.common.core.exception.ServiceException(401, "登录凭证格式错误");
            }
            RequestIdentityHolder.set(tokenService.verify(authorization.substring(7).trim()));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        RequestIdentityHolder.clear();
    }
}

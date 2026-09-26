package com.coffee.web.config;

import com.coffee.web.security.TokenAuthenticationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final TokenAuthenticationInterceptor tokenAuthenticationInterceptor;
    private final com.coffee.web.security.AiRateLimitInterceptor aiRateLimitInterceptor;

    public WebMvcConfig(TokenAuthenticationInterceptor tokenAuthenticationInterceptor,
                        com.coffee.web.security.AiRateLimitInterceptor aiRateLimitInterceptor) {
        this.tokenAuthenticationInterceptor = tokenAuthenticationInterceptor;
        this.aiRateLimitInterceptor = aiRateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tokenAuthenticationInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(aiRateLimitInterceptor).addPathPatterns("/api/**");
    }
}

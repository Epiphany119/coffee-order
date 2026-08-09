package com.coffee.web.config;

import com.coffee.web.security.TokenAuthenticationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final TokenAuthenticationInterceptor tokenAuthenticationInterceptor;

    public WebMvcConfig(TokenAuthenticationInterceptor tokenAuthenticationInterceptor) {
        this.tokenAuthenticationInterceptor = tokenAuthenticationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tokenAuthenticationInterceptor).addPathPatterns("/api/**");
    }
}

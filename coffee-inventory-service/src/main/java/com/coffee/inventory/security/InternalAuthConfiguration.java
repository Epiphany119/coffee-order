package com.coffee.inventory.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InternalAuthProperties.class)
public class InternalAuthConfiguration {
    @Bean
    public FilterRegistrationBean<InternalAuthenticationFilter> internalAuthenticationFilter(
            InternalAuthProperties properties, InternalAuthReplayStore replayStore) {
        FilterRegistrationBean<InternalAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new InternalAuthenticationFilter(properties, replayStore));
        registration.addUrlPatterns("/internal/*");
        registration.setOrder(1);
        return registration;
    }
}

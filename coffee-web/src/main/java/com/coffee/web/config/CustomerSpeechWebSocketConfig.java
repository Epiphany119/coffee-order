package com.coffee.web.config;

import com.coffee.web.speech.CustomerSpeechHandshakeInterceptor;
import com.coffee.web.speech.CustomerSpeechWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

/** 实时语音识别 WebSocket 配置。 */
@Configuration
@EnableWebSocket
public class CustomerSpeechWebSocketConfig implements WebSocketConfigurer {
    private final CustomerSpeechWebSocketHandler handler;
    private final CustomerSpeechHandshakeInterceptor interceptor;
    private final String allowedOrigins;

    public CustomerSpeechWebSocketConfig(CustomerSpeechWebSocketHandler handler,
                                         CustomerSpeechHandshakeInterceptor interceptor,
                                         @Value("${coffee.web.cors.allowed-origins:http://localhost:5174,http://127.0.0.1:5174}") String allowedOrigins) {
        this.handler = handler;
        this.interceptor = interceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toArray(String[]::new);
        registry.addHandler(handler, "/ws/customer-agent/transcription")
                .addInterceptors(interceptor)
                .setAllowedOrigins(origins);
    }
}

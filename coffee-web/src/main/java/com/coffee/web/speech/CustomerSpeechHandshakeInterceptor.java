package com.coffee.web.speech;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/** 校验实时语音 WebSocket 的短时票据，不把长期登录 Token 放进 WebSocket URL。 */
@Component
public class CustomerSpeechHandshakeInterceptor implements HandshakeInterceptor {
    public static final String SESSION_ATTRIBUTE = "fikaCustomerSpeechSession";

    private final CustomerSpeechSessionService sessions;

    public CustomerSpeechHandshakeInterceptor(CustomerSpeechSessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String ticket = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("ticket");
        CustomerSpeechSessionService.Session speechSession = sessions.consume(ticket);
        if (speechSession == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(SESSION_ATTRIBUTE, speechSession);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // 票据已在 beforeHandshake 中完成消费。
    }
}

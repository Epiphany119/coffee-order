package com.coffee.order.websocket;

import com.coffee.order.service.WebSocketMessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketHandlerDecorator extends TextWebSocketHandler {

    private final WebSocketMessageService messageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> guestSessions = new ConcurrentHashMap<>();

    public WebSocketHandlerDecorator(WebSocketMessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String query = session.getUri() != null ? session.getUri().getQuery() : "";
        Map<String, String> params = parseQuery(query);

        String userId = params.get("userId");
        String guestId = params.get("guestId");

        if (userId != null && !userId.isBlank()) {
            userSessions.put(userId, session);
            System.out.println("[WebSocket] 用户 " + userId + " 已连接");
            sendMessage(session, Map.of(
                    "type", "CONNECTED",
                    "message", "连接成功"
            ));
        } else if (guestId != null && !guestId.isBlank()) {
            guestSessions.put(guestId, session);
            System.out.println("[WebSocket] 游客 " + guestId + " 已连接");
            sendMessage(session, Map.of(
                    "type", "CONNECTED",
                    "message", "连接成功"
            ));
        } else {
            System.out.println("[WebSocket] 未知连接，拒绝");
            session.close(CloseStatus.BAD_DATA);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 目前前端只需要接收消息，暂不处理前端发送的消息
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String query = session.getUri() != null ? session.getUri().getQuery() : "";
        Map<String, String> params = parseQuery(query);

        String userId = params.get("userId");
        String guestId = params.get("guestId");

        if (userId != null) {
            userSessions.remove(userId);
            System.out.println("[WebSocket] 用户 " + userId + " 已断开");
        } else if (guestId != null) {
            guestSessions.remove(guestId);
            System.out.println("[WebSocket] 游客 " + guestId + " 已断开");
        }
    }

    public void sendToUser(Long userId, Map<String, Object> payload) {
        WebSocketSession session = userSessions.get(userId.toString());
        if (session != null && session.isOpen()) {
            try {
                sendMessage(session, payload);
            } catch (IOException e) {
                System.err.println("[WebSocket] 发送给用户 " + userId + " 失败: " + e.getMessage());
            }
        }
    }

    public void sendToGuest(String guestId, Map<String, Object> payload) {
        WebSocketSession session = guestSessions.get(guestId);
        if (session != null && session.isOpen()) {
            try {
                sendMessage(session, payload);
            } catch (IOException e) {
                System.err.println("[WebSocket] 发送给游客 " + guestId + " 失败: " + e.getMessage());
            }
        }
    }

    public boolean isUserConnected(Long userId) {
        WebSocketSession session = userSessions.get(userId.toString());
        return session != null && session.isOpen();
    }

    public boolean isGuestConnected(String guestId) {
        WebSocketSession session = guestSessions.get(guestId);
        return session != null && session.isOpen();
    }

    private void sendMessage(WebSocketSession session, Map<String, Object> payload) throws IOException {
        String json = objectMapper.writeValueAsString(payload);
        session.sendMessage(new TextMessage(json));
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new ConcurrentHashMap<>();
        if (query == null || query.isBlank()) return params;

        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                params.put(kv[0], kv[1]);
            }
        }
        return params;
    }
}

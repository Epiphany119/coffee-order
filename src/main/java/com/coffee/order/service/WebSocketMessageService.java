package com.coffee.order.service;

import com.coffee.order.websocket.WebSocketHandlerDecorator;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class WebSocketMessageService {

    private final WebSocketHandlerDecorator webSocketHandler;

    public WebSocketMessageService(@Lazy WebSocketHandlerDecorator webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    public void sendToUser(Long userId, String message) {
        webSocketHandler.sendToUser(userId, Map.of(
                "type", "ORDER_READY",
                "message", message
        ));
    }

    public void sendToGuest(String guestId, String message) {
        webSocketHandler.sendToGuest(guestId, Map.of(
                "type", "ORDER_READY",
                "message", message
        ));
    }

    public void sendOrderUpdate(Long userId, Long orderId, String beverageName, String status) {
        webSocketHandler.sendToUser(userId, Map.of(
                "type", "ORDER_UPDATE",
                "orderId", orderId,
                "beverageName", beverageName,
                "status", status
        ));
    }

    public void sendCustomMessage(Long userId, String message) {
        webSocketHandler.sendToUser(userId, Map.of(
                "type", "NOTIFICATION",
                "message", message
        ));
    }
}

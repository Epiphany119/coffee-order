package com.coffee.order.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class WebSocketController {

    @GetMapping("/ws-info")
    public Map<String, String> getWebSocketInfo() {
        return Map.of(
                "url", "ws://localhost:8080/ws",
                "userConnection", "ws://localhost:8080/ws?userId={用户ID}",
                "guestConnection", "ws://localhost:8080/ws?guestId={游客ID}",
                "messageFormat", "{ \"type\": \"ORDER_READY\", \"message\": \"您的咖啡已做好\" }",
                "example", "ws://localhost:8080/ws?userId=1"
        );
    }
}

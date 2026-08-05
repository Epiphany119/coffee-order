package com.coffee.order.service;

import com.coffee.order.entity.GuestOrder;
import com.coffee.order.entity.UserOrder;
import com.coffee.order.repository.GuestOrderRepository;
import com.coffee.order.repository.UserOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class BeverageReadyScheduler {

    private static final Logger log = LoggerFactory.getLogger(BeverageReadyScheduler.class);

    private final WebSocketMessageService webSocketMessageService;
    private final UserOrderRepository userOrderRepo;
    private final GuestOrderRepository guestOrderRepo;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public BeverageReadyScheduler(WebSocketMessageService webSocketMessageService,
                                  UserOrderRepository userOrderRepo,
                                  GuestOrderRepository guestOrderRepo) {
        this.webSocketMessageService = webSocketMessageService;
        this.userOrderRepo = userOrderRepo;
        this.guestOrderRepo = guestOrderRepo;
    }

    public void scheduleUserOrder(Long orderId, Long userId, String beverageName, LocalDateTime readyTime) {
        long delayMinutes = java.time.Duration.between(LocalDateTime.now(), readyTime).toMinutes();
        if (delayMinutes < 0) delayMinutes = 0;
        long delayMillis = Math.max(0, delayMinutes * 60 * 1000);

        log.info("[制作调度] 用户订单 #{} 安排在 {} 后完成，共 {} 分钟",
                orderId, delayMinutes + "分钟", delayMinutes);

        scheduler.schedule(() -> {
            notifyUserOrderReady(orderId, userId, beverageName);
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    public void scheduleGuestOrder(Long orderId, String guestId, String beverageName, LocalDateTime readyTime) {
        long delayMinutes = java.time.Duration.between(LocalDateTime.now(), readyTime).toMinutes();
        if (delayMinutes < 0) delayMinutes = 0;
        long delayMillis = Math.max(0, delayMinutes * 60 * 1000);

        log.info("[制作调度] 游客订单 #{} 安排在 {} 后完成，共 {} 分钟",
                orderId, delayMinutes + "分钟", delayMinutes);

        scheduler.schedule(() -> {
            notifyGuestOrderReady(orderId, guestId, beverageName);
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    private void notifyUserOrderReady(Long orderId, Long userId, String beverageName) {
        try {
            UserOrder order = userOrderRepo.findById(orderId).orElse(null);
            if (order == null) {
                log.warn("[制作调度] 订单 #{} 不存在", orderId);
                return;
            }

            if (!"COMPLETED".equals(order.getStatus()) && !"CANCELED".equals(order.getStatus())) {
                order.setStatus("READY");
                userOrderRepo.save(order);

                String message = "您的【" + beverageName + "】已做好，请取餐！";
                webSocketMessageService.sendToUser(userId, message);
                log.info("[制作调度] ✅ 已通知用户 {}：{}", userId, message);
            }
        } catch (Exception e) {
            log.error("[制作调度] 通知用户订单失败: orderId={}, error={}", orderId, e.getMessage());
        }
    }

    private void notifyGuestOrderReady(Long orderId, String guestId, String beverageName) {
        try {
            GuestOrder order = guestOrderRepo.findById(orderId).orElse(null);
            if (order == null) {
                log.warn("[制作调度] 游客订单 #{} 不存在", orderId);
                return;
            }

            if (!"COMPLETED".equals(order.getStatus()) && !"CANCELED".equals(order.getStatus())) {
                order.setStatus("READY");
                guestOrderRepo.save(order);

                String message = "您的【" + beverageName + "】已做好，请取餐！";
                webSocketMessageService.sendToGuest(guestId, message);
                log.info("[制作调度] ✅ 已通知游客 {}：{}", guestId, message);
            }
        } catch (Exception e) {
            log.error("[制作调度] 通知游客订单失败: orderId={}, error={}", orderId, e.getMessage());
        }
    }
}

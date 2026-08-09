package com.coffee.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 独立订单服务；以 order API 为边界，后续由 Gateway/Nacos 统一暴露。 */
@SpringBootApplication(scanBasePackages = {"com.coffee.order", "com.coffee.module"})
public class OrderServiceApplication {
    public static void main(String[] args) { SpringApplication.run(OrderServiceApplication.class, args); }
}

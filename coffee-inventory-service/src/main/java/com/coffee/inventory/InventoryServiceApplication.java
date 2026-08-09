package com.coffee.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 可独立部署的库存服务，后续订单服务可将 InventoryService 替换为远程适配器。 */
@SpringBootApplication(scanBasePackages = {"com.coffee.inventory", "com.coffee.module.inventory"})
public class InventoryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}

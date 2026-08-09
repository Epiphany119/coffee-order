package com.coffee.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** API 统一入口：本地开发直连 coffee-web，nacos profile 下按服务名发现。 */
@SpringBootApplication
public class CoffeeGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(CoffeeGatewayApplication.class, args);
    }
}

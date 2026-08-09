package com.coffee.marketing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 独立营销服务：承载秒杀/券/活动，Nacos profile 下注册为 coffee-marketing-service。 */
@SpringBootApplication(scanBasePackages = {"com.coffee.marketing", "com.coffee.module.marketing"})
@EnableScheduling
public class MarketingServiceApplication {
    public static void main(String[] args) { SpringApplication.run(MarketingServiceApplication.class, args); }
}

package com.coffee.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
@MapperScan({
        "com.coffee.module.*.biz.infra.persistence",
        "com.coffee.module.location.biz.infra"
})
@ComponentScan({"com.coffee.web", "com.coffee.module", "com.coffee.common"})
public class CoffeeWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoffeeWebApplication.class, args);
    }
}

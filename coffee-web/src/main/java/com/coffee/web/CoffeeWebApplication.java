package com.coffee.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@MapperScan({"com.coffee.module.*.biz.infra.persistence"})
@ComponentScan({"com.coffee.web", "com.coffee.module", "com.coffee.common"})
public class CoffeeWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoffeeWebApplication.class, args);
    }
}

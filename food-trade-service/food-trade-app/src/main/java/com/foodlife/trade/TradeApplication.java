package com.foodlife.trade;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.foodlife.trade.infrastructure.dao")
@EnableScheduling
@EnableFeignClients(basePackages = "com.foodlife.trade.infrastructure.feign")
@SpringBootApplication(scanBasePackages = "com.foodlife")
public class TradeApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradeApplication.class, args);
    }
}

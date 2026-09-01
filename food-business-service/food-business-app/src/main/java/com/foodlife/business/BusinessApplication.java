package com.foodlife.business;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@MapperScan("com.foodlife.business.infrastructure.dao")
@EnableFeignClients(basePackages = "com.foodlife.business.infrastructure.feign")
@SpringBootApplication(scanBasePackages = "com.foodlife")
public class BusinessApplication {

    public static void main(String[] args) {
        SpringApplication.run(BusinessApplication.class, args);
    }
}

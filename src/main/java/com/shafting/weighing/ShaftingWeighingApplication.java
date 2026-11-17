package com.shafting.weighing;

import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.SpringApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
/**
 * 轴系顶升称重测量系统启动入口，同时启用设备模拟器定时任务。
 * */
public class ShaftingWeighingApplication {

    /** 启动 Spring Boot Web 应用。 */
    public static void main(String[] args) {
        SpringApplication.run(ShaftingWeighingApplication.class, args);
    }
}


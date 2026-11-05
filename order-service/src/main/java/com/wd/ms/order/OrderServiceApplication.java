package com.wd.ms.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 订单服务启动类。
 *
 * <ul>
 *   <li>{@code @EnableScheduling}：超时关单、消息中继、卡单恢复、workerId 心跳都是定时任务
 *   <li>{@code @EnableResilientMethods}：启用 Spring Framework 7 自带的 {@code @ConcurrencyLimit} / {@code @Retryable}
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
@EnableResilientMethods
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}

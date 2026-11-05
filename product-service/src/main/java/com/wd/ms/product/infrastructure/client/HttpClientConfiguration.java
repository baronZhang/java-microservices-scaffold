package com.wd.ms.product.infrastructure.client;

import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextSnapshotFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.client.circuitbreaker.httpservice.HttpServiceFallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * 声明 HTTP Interface 客户端分组。三件事都是声明式的：
 *
 * <ul>
 *   <li>{@code @ImportHttpServices}：注册客户端；分组名 = 服务名，基础地址自动是 http://inventory-service（负载均衡）
 *   <li>{@code @HttpServiceFallback}：给这个分组配降级类，Spring Cloud 因此给它套上熔断器（熔断器 id = 分组名）
 *   <li>超时：在配置文件里按分组设置 spring.http.serviceclient.inventory-service.read-timeout
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "inventory-service", types = InventoryClient.class)
@HttpServiceFallback(value = InventoryClientFallback.class, group = "inventory-service")
public class HttpClientConfiguration {

    /**
     * Spring Cloud CircuitBreaker（Resilience4j 实现）默认把被保护的调用放到一个线程池里执行。
     * 线程一换，ThreadLocal 里的链路上下文就丢了——实验 8 里，商品服务调库存服务这一跳生成了一个新的 traceId，链路断成两截。
     *
     * <p>用 Micrometer context-propagation 包装这个线程池：提交任务时抓取当前线程的上下文（Observation / span），
     * 在工作线程里恢复。业务线程本身是虚拟线程，工作线程也用虚拟线程。
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> contextPropagatingExecutor() {
        ExecutorService executor = ContextExecutorService.wrap(
                Executors.newVirtualThreadPerTaskExecutor(),
                ContextSnapshotFactory.builder().build()::captureAll);
        return factory -> {
            factory.configureExecutorService(executor);
            factory.configureGroupExecutorService(group -> executor);
        };
    }
}

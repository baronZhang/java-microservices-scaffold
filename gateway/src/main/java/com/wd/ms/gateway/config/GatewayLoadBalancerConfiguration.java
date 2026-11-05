package com.wd.ms.gateway.config;

import com.wd.ms.gateway.gray.GrayServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 网关（响应式）的实例筛选链：服务发现 → 健康检查 → 严格灰度筛选（{@link GrayServiceInstanceListSupplier}）。
 * 不能被组件扫描到，由 {@code @LoadBalancerClients(defaultConfiguration = ...)} 引用。
 */
public class GatewayLoadBalancerConfiguration {

    @Bean
    public ServiceInstanceListSupplier gatewayServiceInstanceListSupplier(ConfigurableApplicationContext context) {
        boolean healthCheck = context.getEnvironment().getProperty("ms.loadbalancer.health-check", Boolean.class, true);
        var builder = ServiceInstanceListSupplier.builder().withDiscoveryClient();
        builder = healthCheck ? builder.withHealthChecks(WebClient.create()) : builder.withCaching();
        return builder.with((ctx, delegate) -> new GrayServiceInstanceListSupplier(delegate))
                .build(context);
    }
}

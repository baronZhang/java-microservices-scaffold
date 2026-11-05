package com.wd.ms.order.infrastructure.client;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * 两个客户端分组，分组名 = 服务名（Spring Cloud 自动接上负载均衡）。
 *
 * <p>和商品服务不同，这里没有用 {@code @HttpServiceFallback}（分组级的熔断 + 降级），而是在适配器里用编程式熔断器：
 * 预占库存需要"重试 + 熔断 + 把失败翻译成 UNKNOWN"的精细控制，而且事件投递有自己的重试机制，不该被熔断器拦下。
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "inventory-service", types = InventoryClient.class)
@ImportHttpServices(group = "product-service", types = ProductClient.class)
public class HttpClientConfiguration {}

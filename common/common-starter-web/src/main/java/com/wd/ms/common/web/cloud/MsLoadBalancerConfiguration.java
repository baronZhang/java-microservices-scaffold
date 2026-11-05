package com.wd.ms.common.web.cloud;

import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * 所有下游服务共用的负载均衡实例筛选链（Servlet 版本；网关有自己的响应式版本）。
 *
 * <p>链条：服务发现 → 健康检查 → 灰度标签过滤，最后由默认的轮询策略挑一个实例。
 *
 * <ul>
 *   <li>健康检查：默认 profile 用的是静态服务列表（没有注册中心替我们剔除挂掉的实例），所以要自己定期探活；
 *       nacos profile 下注册中心已经只返回健康实例，用 {@code ms.loadbalancer.health-check=false} 关掉
 *   <li>灰度标签：实例元数据里的 {@code hint} 和请求头里的提示值匹配，见文档"网关：路由、限流与灰度"
 * </ul>
 *
 * <p>这个类不能被业务服务的组件扫描扫到（否则会变成全局 Bean），所以放在 starter 自己的包里，
 * 通过 {@code @LoadBalancerClients(defaultConfiguration = ...)} 注册给每个下游服务的子容器。
 */
public class MsLoadBalancerConfiguration {

    @Bean
    public ServiceInstanceListSupplier msServiceInstanceListSupplier(ConfigurableApplicationContext context) {
        boolean healthCheck = context.getEnvironment().getProperty("ms.loadbalancer.health-check", Boolean.class, true);
        var builder = ServiceInstanceListSupplier.builder().withBlockingDiscoveryClient();
        if (healthCheck) {
            // 探活请求直接发到实例地址，不能再走负载均衡，所以用一个普通的 RestClient
            builder = builder.withBlockingHealthChecks(RestClient.create());
        } else {
            builder = builder.withCaching();
        }
        return builder.withHints().build(context);
    }
}

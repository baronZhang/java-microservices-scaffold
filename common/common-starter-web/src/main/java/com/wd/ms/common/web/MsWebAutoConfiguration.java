package com.wd.ms.common.web;

import com.wd.ms.common.web.chaos.ChaosEndpoint;
import com.wd.ms.common.web.chaos.ChaosInterceptor;
import com.wd.ms.common.web.chaos.ChaosState;
import com.wd.ms.common.web.cloud.MsLoadBalancerConfiguration;
import io.micrometer.tracing.Tracer;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Servlet 服务的通用约定。业务服务只要引入 common-starter-web（安全 starter 会间接引入），这些就自动生效。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(MsInstanceProperties.class)
@LoadBalancerClients(defaultConfiguration = MsLoadBalancerConfiguration.class)
@Import(GlobalExceptionHandler.class)
public class MsWebAutoConfiguration {

    @Bean
    public FilterRegistrationBean<ResponseHeadersFilter> msResponseHeadersFilter(
            Environment environment, MsInstanceProperties instance, ObjectProvider<Tracer> tracer) {
        String name = environment.getProperty("spring.application.name", "unknown");
        var registration = new FilterRegistrationBean<>(new ResponseHeadersFilter(name, instance.version(), tracer));
        // Spring 的链路追踪过滤器顺序是 HIGHEST_PRECEDENCE + 1，排在它后面才能拿到 traceId
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    /** 访问日志：排在响应头过滤器之后，同样能拿到 traceId */
    @Bean
    public FilterRegistrationBean<AccessLogFilter> msAccessLogFilter() {
        var registration = new FilterRegistrationBean<>(new AccessLogFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 11);
        return registration;
    }

    /** 统一的时钟：所有"取当前时间"的地方都注入它，测试里换成固定时钟就能精确控制时间 */
    @Bean
    @ConditionalOnMissingBean
    public Clock msClock() {
        return Clock.systemUTC();
    }

    /** 故障注入：只在 ms.chaos.enabled=true（demo profile）时装配 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "ms.chaos", name = "enabled", havingValue = "true")
    static class ChaosConfiguration implements WebMvcConfigurer {

        private final ChaosState state = new ChaosState();

        @Bean
        ChaosState chaosState() {
            return state;
        }

        @Bean
        ChaosEndpoint chaosEndpoint() {
            return new ChaosEndpoint(state);
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            // 管理端点本身不注入故障，否则"让服务变慢"之后连恢复命令都会变慢
            registry.addInterceptor(new ChaosInterceptor(state)).excludePathPatterns("/actuator/**");
        }
    }
}

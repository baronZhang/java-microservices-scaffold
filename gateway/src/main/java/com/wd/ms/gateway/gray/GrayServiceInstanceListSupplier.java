package com.wd.ms.gateway.gray;

import java.util.List;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.RequestDataContext;
import org.springframework.cloud.loadbalancer.core.DelegatingServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import reactor.core.publisher.Flux;

/**
 * 严格的灰度实例筛选。没有直接用 Spring Cloud 自带的 HintBasedServiceInstanceListSupplier，因为它有一个对灰度很危险的行为：
 * 按 hint 找不到匹配的实例时，会退回"全部实例"——稳定版实例还没通过健康检查（刚启动）或者全挂了的时候，
 * 普通流量就会被打到灰度版。这是写测试时真实遇到的（见文档"踩坑记录"）。
 *
 * <p>这里的规则：
 *
 * <ul>
 *   <li>带灰度标签（X-Gray: gray）的请求：优先灰度实例；灰度实例都不可用时退回稳定版（灰度用户至少还能用）
 *   <li>其他请求：只用非灰度实例（hint 不是 gray 的，包括没打标签的服务）；一个都没有就返回空 → 503，
 *       宁可失败也不让普通流量进灰度版
 * </ul>
 */
public class GrayServiceInstanceListSupplier extends DelegatingServiceInstanceListSupplier {

    public GrayServiceInstanceListSupplier(ServiceInstanceListSupplier delegate) {
        super(delegate);
    }

    @Override
    public Flux<List<ServiceInstance>> get() {
        return delegate.get().map(instances -> select(instances, null));
    }

    @Override
    @SuppressWarnings("rawtypes") // 接口方法本身声明的就是原始类型 Request，覆盖时签名只能保持一致
    public Flux<List<ServiceInstance>> get(Request request) {
        String hint = hintOf(request);
        return delegate.get(request).map(instances -> select(instances, hint));
    }

    /** Spring 7 用 JSpecify 标注了可空性：getClientRequest() 可能为 null，SpotBugs 据此报过警，这里显式判断 */
    private static String hintOf(Request<?> request) {
        if (request.getContext() instanceof RequestDataContext context) {
            var clientRequest = context.getClientRequest();
            var headers = clientRequest == null ? null : clientRequest.getHeaders();
            if (headers != null) {
                return headers.getFirst(GrayRouteFilter.HEADER);
            }
        }
        return null;
    }

    static List<ServiceInstance> select(List<ServiceInstance> instances, String hint) {
        List<ServiceInstance> normal =
                instances.stream().filter(instance -> !isGray(instance)).toList();
        if (GrayRouteFilter.GRAY.equals(hint)) {
            List<ServiceInstance> gray = instances.stream()
                    .filter(GrayServiceInstanceListSupplier::isGray)
                    .toList();
            return gray.isEmpty() ? normal : gray;
        }
        return normal;
    }

    private static boolean isGray(ServiceInstance instance) {
        var metadata = instance.getMetadata();
        return metadata != null && GrayRouteFilter.GRAY.equals(metadata.get("hint"));
    }
}

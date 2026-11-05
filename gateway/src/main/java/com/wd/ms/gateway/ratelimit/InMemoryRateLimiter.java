package com.wd.ms.gateway.ratelimit;

import com.wd.ms.gateway.config.MsGatewayProperties;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import reactor.core.publisher.Mono;

/**
 * 内存版令牌桶（默认 profile 用）。算法和 Spring Cloud Gateway 的 RedisRateLimiter 的 Lua 脚本一样：
 * 每个 key 一个桶，按"距离上次取令牌过了多久"补充令牌，桶满为止；请求来了取一个，取不到就拒绝。
 *
 * <p>局限：只在单个网关实例内有效。N 个网关实例就相当于把阈值放大了 N 倍，多实例要用 redis 实现。
 */
public class InMemoryRateLimiter implements RateLimiter<Object> {

    public static final String POLICY_LOGIN = "login";

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final MsGatewayProperties properties;
    private final Clock clock;

    public InMemoryRateLimiter(MsGatewayProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** routeId 参数在这里表示限流策略（default / login） */
    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        MsGatewayProperties.Policy policy = POLICY_LOGIN.equals(routeId)
                ? properties.getRateLimit().getLogin()
                : properties.getRateLimit().getDefaults();
        Bucket bucket = buckets.computeIfAbsent(routeId + ":" + id, key -> new Bucket(policy.getBurstCapacity()));
        long remaining = bucket.tryTake(policy.getReplenishRate(), policy.getBurstCapacity(), clock.millis());
        return Mono.just(new Response(
                remaining >= 0,
                Map.of(
                        "X-RateLimit-Remaining", String.valueOf(Math.max(remaining, 0)),
                        "X-RateLimit-Replenish-Rate", String.valueOf(policy.getReplenishRate()),
                        "X-RateLimit-Burst-Capacity", String.valueOf(policy.getBurstCapacity()))));
    }

    @Override
    public Map<String, Object> getConfig() {
        return Map.of();
    }

    @Override
    public Class<Object> getConfigClass() {
        return Object.class;
    }

    @Override
    public Object newConfig() {
        return new Object();
    }

    /** 一个令牌桶。synchronized 的范围只有几行算术，竞争很小 */
    static final class Bucket {
        private double tokens;
        private long lastRefillMillis = -1;

        Bucket(int capacity) {
            this.tokens = capacity;
        }

        /** 取一个令牌：成功返回剩余令牌数，失败返回 -1 */
        synchronized long tryTake(int replenishRate, int capacity, long nowMillis) {
            if (lastRefillMillis >= 0) {
                double refill = (nowMillis - lastRefillMillis) / 1000.0 * replenishRate;
                tokens = Math.min(capacity, tokens + refill);
            }
            lastRefillMillis = nowMillis;
            if (tokens < 1) {
                return -1;
            }
            tokens -= 1;
            return (long) tokens;
        }
    }
}

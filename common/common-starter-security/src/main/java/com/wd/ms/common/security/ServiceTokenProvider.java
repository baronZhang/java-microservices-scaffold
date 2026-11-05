package com.wd.ms.common.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * 服务令牌：本服务以自己的身份（client_credentials）向认证服务换取的 access token，用于调用其他服务的 /internal/** 接口。
 *
 * <p>和前端的"无感刷新"是同一个问题的后端版本：
 *
 * <ul>
 *   <li>缓存：令牌有效期内复用，不是每次调用都去认证服务换一次
 *   <li>提前刷新：离过期还有 {@link #REFRESH_AHEAD} 就换新的，避免"拿到时有效、到对方那里刚好过期"
 *   <li>只刷新一次：多个线程同时发现过期，用锁 + 双重检查保证只有一个线程去换（前端用共享 Promise，这里用锁）
 * </ul>
 */
public class ServiceTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenProvider.class);
    static final Duration REFRESH_AHEAD = Duration.ofSeconds(30);

    private final RestTemplate loadBalancedRestTemplate;
    private final MsSecurityProperties.Client client;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();
    private volatile CachedToken cached;

    public ServiceTokenProvider(
            RestTemplate loadBalancedRestTemplate, MsSecurityProperties.Client client, Clock clock) {
        this.loadBalancedRestTemplate = loadBalancedRestTemplate;
        this.client = client;
        this.clock = clock;
    }

    public String token() {
        CachedToken current = cached;
        if (isFresh(current)) {
            return current.value();
        }
        // 用 ReentrantLock 而不是 synchronized：JDK 24 之前 synchronized 会把虚拟线程钉在平台线程上
        lock.lock();
        try {
            current = cached;
            if (!isFresh(current)) {
                current = fetch();
                cached = current;
            }
            return current.value();
        } finally {
            lock.unlock();
        }
    }

    private boolean isFresh(CachedToken token) {
        return token != null && token.expiresAt().isAfter(clock.instant().plus(REFRESH_AHEAD));
    }

    @SuppressWarnings("unchecked")
    private CachedToken fetch() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(client.id(), client.secret());
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        // 令牌端点按 OAuth2 规范（RFC 6749）返回 access_token / expires_in，不套统一响应结构
        Map<String, Object> body =
                loadBalancedRestTemplate.postForObject(client.tokenUri(), new HttpEntity<>(form, headers), Map.class);
        if (body == null || body.get("access_token") == null) {
            throw new IllegalStateException("认证服务没有返回服务令牌");
        }
        long expiresIn = ((Number) body.get("expires_in")).longValue();
        log.info("已获取服务令牌：client={}，有效期 {} 秒", client.id(), expiresIn);
        return new CachedToken(
                (String) body.get("access_token"), clock.instant().plusSeconds(expiresIn));
    }

    private record CachedToken(String value, Instant expiresAt) {}
}

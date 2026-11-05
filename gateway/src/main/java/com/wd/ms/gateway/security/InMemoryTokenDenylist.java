package com.wd.ms.gateway.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import reactor.core.publisher.Mono;

/**
 * 默认实现：进程内存。只适合单个网关实例——多实例时，A 实例吊销的 token 在 B 实例上仍然有效，
 * 这时要换成 redis profile 的 {@link RedisTokenDenylist}。
 */
public class InMemoryTokenDenylist implements TokenDenylist {

    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryTokenDenylist(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Mono<Boolean> isRevoked(String jti) {
        Instant expiresAt = revoked.get(jti);
        if (expiresAt != null && expiresAt.isBefore(clock.instant())) {
            // token 已经自然过期，验签那一步就会拒绝它，吊销记录可以删了
            revoked.remove(jti);
            return Mono.just(false);
        }
        return Mono.just(expiresAt != null);
    }

    @Override
    public Mono<Void> revoke(String jti, Instant expiresAt) {
        revoked.put(jti, expiresAt);
        // 顺手清理过期记录，避免无限增长（量很小，不值得单独起定时任务）
        Instant now = clock.instant();
        revoked.values().removeIf(time -> time.isBefore(now));
        return Mono.empty();
    }
}

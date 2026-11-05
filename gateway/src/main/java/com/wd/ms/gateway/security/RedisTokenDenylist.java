package com.wd.ms.gateway.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

/** redis profile：多个网关实例共享的吊销清单。键的过期时间等于 token 的剩余有效期，到期自动删除。 */
public class RedisTokenDenylist implements TokenDenylist {

    private static final String PREFIX = "ms:gateway:revoked:";
    private final ReactiveStringRedisTemplate redis;
    private final Clock clock;

    public RedisTokenDenylist(ReactiveStringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    @Override
    public Mono<Boolean> isRevoked(String jti) {
        return redis.hasKey(PREFIX + jti);
    }

    @Override
    public Mono<Void> revoke(String jti, Instant expiresAt) {
        Duration ttl = Duration.between(clock.instant(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            return Mono.empty();
        }
        return redis.opsForValue().set(PREFIX + jti, "1", ttl).then();
    }
}

package com.wd.ms.gateway.security;

import java.time.Instant;
import reactor.core.publisher.Mono;

/**
 * access token 吊销清单。JWT 本身是无状态的，签发后在过期前一直有效；"退出登录后立即失效"只能靠一个清单来实现。
 *
 * <p>清单放在网关：网关是唯一统一校验 token 的地方。只需要保存到 token 过期为止（最多 5 分钟），数据量很小。
 */
public interface TokenDenylist {

    Mono<Boolean> isRevoked(String jti);

    Mono<Void> revoke(String jti, Instant expiresAt);
}

package com.wd.ms.order.infrastructure.config;

import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.integration.jdbc.lock.DefaultLockRepository;
import org.springframework.integration.jdbc.lock.JdbcLockRegistry;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.integration.support.locks.LockRegistry;

/**
 * 分布式锁：用 Spring Integration 的 LockRegistry 抽象，业务代码只认 {@code java.util.concurrent.locks.Lock}。
 *
 * <ul>
 *   <li>默认：数据库实现（INT_LOCK 表，带过期时间），不需要额外中间件。同一服务的多个实例共享同一个库就能互斥
 *   <li>redis profile：Redis 实现（SET NX PX + Lua 脚本释放），性能更好
 * </ul>
 *
 * <p>两种实现都有"租期"：持锁的进程挂了，锁到期自动释放，不会永远锁死；代价是任务执行时间必须短于租期，否则要续期。
 */
@Configuration(proxyBeanMethods = false)
public class LockConfiguration {

    static final Duration LOCK_TTL = Duration.ofSeconds(30);

    @Bean
    @ConditionalOnProperty(prefix = "ms.order", name = "lock", havingValue = "jdbc", matchIfMissing = true)
    public DefaultLockRepository lockRepository(DataSource dataSource) {
        DefaultLockRepository repository = new DefaultLockRepository(dataSource);
        // region 区分不同业务，同一张锁表可以给多个服务或多个用途共用
        repository.setRegion("order-service");
        return repository;
    }

    @Bean
    @ConditionalOnProperty(prefix = "ms.order", name = "lock", havingValue = "jdbc", matchIfMissing = true)
    public LockRegistry<?> jdbcLockRegistry(DefaultLockRepository lockRepository) {
        return new JdbcLockRegistry(lockRepository, LOCK_TTL);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ms.order", name = "lock", havingValue = "redis")
    public LockRegistry<?> redisLockRegistry(RedisConnectionFactory connectionFactory) {
        return new RedisLockRegistry(connectionFactory, "ms:order:lock", LOCK_TTL);
    }
}

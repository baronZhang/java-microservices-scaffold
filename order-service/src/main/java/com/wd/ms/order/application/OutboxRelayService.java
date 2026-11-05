package com.wd.ms.order.application;

import com.wd.ms.order.domain.outbox.EventPublisher;
import com.wd.ms.order.domain.outbox.OutboxEvent;
import com.wd.ms.order.domain.outbox.OutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 消息中继：把本地消息表里到期的事件逐条投递出去。
 *
 * <ul>
 *   <li>成功 → SENT
 *   <li>失败 → 指数退避后重试（1s、2s、4s……最长 60s）
 *   <li>失败 {@value #MAX_ATTEMPTS} 次 → DEAD，打错误日志等人工处理（生产要接告警）
 * </ul>
 *
 * <p>投递语义是"至少一次"：对方处理成功、但响应在网络上丢了，这里会当成失败再投一次，所以消费方必须幂等。
 */
@Service
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);
    static final int MAX_ATTEMPTS = 10;
    static final Duration MAX_BACKOFF = Duration.ofSeconds(60);

    private final OutboxRepository outbox;
    private final EventPublisher publisher;
    private final Clock clock;

    public OutboxRelayService(OutboxRepository outbox, EventPublisher publisher, Clock clock) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.clock = clock;
    }

    /** 投递一批到期事件，返回成功条数 */
    public int relayDue(int limit) {
        int sent = 0;
        for (OutboxEvent event : outbox.findDue(clock.instant(), limit)) {
            try {
                publisher.publish(event);
                outbox.markSent(event.id(), clock.instant());
                sent++;
            } catch (RuntimeException e) {
                onFailure(event, e);
            }
        }
        return sent;
    }

    private void onFailure(OutboxEvent event, RuntimeException e) {
        int attempts = event.attempts() + 1;
        String error = abbreviate(e.toString());
        if (attempts >= MAX_ATTEMPTS) {
            outbox.markDead(event.id(), attempts, error);
            log.error(
                    "事件 {}（{} {}）投递失败 {} 次，已标记为 DEAD，需要人工处理：{}",
                    event.eventId(),
                    event.type(),
                    event.aggregateId(),
                    attempts,
                    error);
            return;
        }
        Duration delay = backoff(attempts);
        Instant next = clock.instant().plus(delay);
        outbox.markRetry(event.id(), attempts, next, error);
        log.warn(
                "事件 {}（{} {}）第 {} 次投递失败，{} 秒后重试：{}",
                event.eventId(),
                event.type(),
                event.aggregateId(),
                attempts,
                delay.toSeconds(),
                error);
    }

    /** 指数退避：第 n 次失败后等 2^(n-1) 秒，封顶 60 秒 */
    static Duration backoff(int attempts) {
        long seconds = 1L << Math.min(attempts - 1, 6);
        Duration delay = Duration.ofSeconds(seconds);
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }

    private static String abbreviate(String text) {
        return text.length() <= 480 ? text : text.substring(0, 480);
    }
}

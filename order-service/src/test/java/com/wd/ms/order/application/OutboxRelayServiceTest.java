package com.wd.ms.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.wd.ms.order.domain.outbox.EventPublisher;
import com.wd.ms.order.domain.outbox.OutboxEvent;
import com.wd.ms.order.domain.outbox.OutboxRepository;
import com.wd.ms.order.domain.outbox.OutboxStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** 消息中继的重试策略：用内存版的仓储和"前几次失败"的投递器，验证退避、标记已投递、进入 DEAD。 */
class OutboxRelayServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-27T08:00:00Z"), ZoneOffset.UTC);
    private final InMemoryOutbox outbox = new InMemoryOutbox();

    @Test
    void 指数退避_封顶60秒() {
        assertThat(OutboxRelayService.backoff(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(OutboxRelayService.backoff(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(OutboxRelayService.backoff(4)).isEqualTo(Duration.ofSeconds(8));
        assertThat(OutboxRelayService.backoff(9)).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void 投递失败安排重试_成功后标记已投递() {
        outbox.insert("e1", "order-1", "ORDER_PAID", "{}", clock.instant());
        AtomicInteger calls = new AtomicInteger();
        EventPublisher flaky = event -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("库存服务不可用");
            }
        };
        var relay = new OutboxRelayService(outbox, flaky, clock);

        assertThat(relay.relayDue(10)).isZero();
        OutboxEvent retrying = outbox.events.getFirst();
        assertThat(retrying.attempts()).isEqualTo(1);
        assertThat(retrying.nextAttemptAt()).isEqualTo(clock.instant().plusSeconds(1));

        // 时间没到不重试；把下次投递时间拨到现在，模拟"一秒后"
        outbox.events.set(0, withNextAttempt(retrying, clock.instant()));
        assertThat(relay.relayDue(10)).isEqualTo(1);
        assertThat(outbox.events.getFirst().status()).isEqualTo(OutboxStatus.SENT);
    }

    @Test
    void 连续失败到上限进入DEAD() {
        outbox.insert("e2", "order-2", "ORDER_CANCELLED", "{}", clock.instant());
        var relay = new OutboxRelayService(
                outbox,
                event -> {
                    throw new IllegalStateException("一直失败");
                },
                clock);
        for (int i = 0; i < OutboxRelayService.MAX_ATTEMPTS; i++) {
            outbox.events.set(0, withNextAttempt(outbox.events.getFirst(), clock.instant()));
            relay.relayDue(10);
        }
        assertThat(outbox.events.getFirst().status()).isEqualTo(OutboxStatus.DEAD);
        assertThat(outbox.countByStatus(OutboxStatus.DEAD)).isEqualTo(1);
    }

    private static OutboxEvent withNextAttempt(OutboxEvent event, Instant next) {
        return new OutboxEvent(
                event.id(),
                event.eventId(),
                event.aggregateId(),
                event.type(),
                event.payload(),
                event.status(),
                event.attempts(),
                next);
    }

    /** 内存版的本地消息表 */
    static final class InMemoryOutbox implements OutboxRepository {
        final List<OutboxEvent> events = new ArrayList<>();

        @Override
        public void insert(String eventId, String aggregateId, String type, String payload, Instant now) {
            events.add(new OutboxEvent(
                    events.size() + 1, eventId, aggregateId, type, payload, OutboxStatus.PENDING, 0, now));
        }

        @Override
        public List<OutboxEvent> findDue(Instant now, int limit) {
            return events.stream()
                    .filter(e -> e.status() == OutboxStatus.PENDING
                            && !e.nextAttemptAt().isAfter(now))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void markSent(long id, Instant now) {
            replace(id, OutboxStatus.SENT, null, null);
        }

        @Override
        public void markRetry(long id, int attempts, Instant nextAttemptAt, String error) {
            replace(id, OutboxStatus.PENDING, attempts, nextAttemptAt);
        }

        @Override
        public void markDead(long id, int attempts, String error) {
            replace(id, OutboxStatus.DEAD, attempts, null);
        }

        @Override
        public long countByStatus(OutboxStatus status) {
            return events.stream().filter(e -> e.status() == status).count();
        }

        @Override
        public List<OutboxEvent> findUnsent(int limit) {
            return events.stream()
                    .filter(e -> e.status() != OutboxStatus.SENT)
                    .limit(limit)
                    .toList();
        }

        private void replace(long id, OutboxStatus status, Integer attempts, Instant next) {
            int index = (int) id - 1;
            OutboxEvent e = events.get(index);
            events.set(
                    index,
                    new OutboxEvent(
                            e.id(),
                            e.eventId(),
                            e.aggregateId(),
                            e.type(),
                            e.payload(),
                            status,
                            attempts == null ? e.attempts() : attempts,
                            next == null ? e.nextAttemptAt() : next));
        }
    }
}

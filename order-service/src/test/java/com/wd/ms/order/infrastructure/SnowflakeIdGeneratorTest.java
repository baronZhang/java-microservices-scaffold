package com.wd.ms.order.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wd.ms.order.infrastructure.id.SnowflakeIdGenerator;
import com.wd.ms.order.infrastructure.id.SnowflakeIdGenerator.ClockMovedBackwardsException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** 雪花算法：时钟用可控的假时钟，回拨、同一毫秒序号用完这些边界都能精确构造出来。 */
class SnowflakeIdGeneratorTest {

    private final AtomicLong now = new AtomicLong(SnowflakeIdGenerator.EPOCH + 1_000_000);

    @Test
    void id的组成可以解析回来() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(7, now::get, millis -> {});
        long first = generator.nextId();
        long second = generator.nextId();
        assertThat(SnowflakeIdGenerator.timestampOf(first)).isEqualTo(now.get());
        assertThat(SnowflakeIdGenerator.workerIdOf(first)).isEqualTo(7);
        assertThat(SnowflakeIdGenerator.sequenceOf(first)).isZero();
        assertThat(SnowflakeIdGenerator.sequenceOf(second)).isEqualTo(1);
        assertThat(second).isGreaterThan(first);
    }

    @Test
    void 一百万个id不重复且递增() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1);
        Set<Long> ids = new HashSet<>();
        long previous = 0;
        for (int i = 0; i < 1_000_000; i++) {
            long id = generator.nextId();
            assertThat(id).isGreaterThan(previous);
            ids.add(id);
            previous = id;
        }
        assertThat(ids).hasSize(1_000_000);
    }

    @Test
    void 同一毫秒4096个序号用完后等到下一毫秒() {
        AtomicLong calls = new AtomicLong();
        // 第 4097 次取号之后，时钟才前进 1 毫秒
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(
                1, () -> calls.incrementAndGet() > 4097 ? now.get() + 1 : now.get(), millis -> {});
        long last = 0;
        for (int i = 0; i < 4097; i++) {
            last = generator.nextId();
        }
        assertThat(SnowflakeIdGenerator.timestampOf(last)).isEqualTo(now.get() + 1);
        assertThat(SnowflakeIdGenerator.sequenceOf(last)).isZero();
    }

    @Test
    void 小幅回拨_等时钟追上后继续发号() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, now::get, millis -> now.addAndGet(millis));
        long before = generator.nextId();
        now.addAndGet(-3);
        long after = generator.nextId();
        assertThat(after).isGreaterThan(before);
    }

    @Test
    void 大幅回拨_拒绝发号() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, now::get, millis -> {});
        generator.nextId();
        now.addAndGet(-1000);
        assertThatThrownBy(generator::nextId)
                .isInstanceOf(ClockMovedBackwardsException.class)
                .hasMessageContaining("1000");
    }

    @Test
    void workerId超出10位范围直接拒绝() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(1024)).isInstanceOf(IllegalArgumentException.class);
    }
}

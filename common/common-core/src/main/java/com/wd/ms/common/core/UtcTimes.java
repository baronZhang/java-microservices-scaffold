package com.wd.ms.common.core;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 时间约定：程序里一律用 {@link Instant}，数据库里一律存 UTC 的 {@code DATETIME(3)}（不带时区）。
 *
 * <p>为什么要显式转换：直接把 Instant / Timestamp 交给 JDBC 驱动，驱动会按"JVM 默认时区"或"会话时区"换算，
 * 服务器时区、容器时区、MySQL 时区只要有一个不一致，时间就会差 8 小时。显式转成 UTC 的 LocalDateTime，换算只在这一处发生。
 */
public final class UtcTimes {

    private UtcTimes() {}

    public static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public static Instant fromDb(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}

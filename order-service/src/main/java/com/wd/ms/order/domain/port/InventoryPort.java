package com.wd.ms.order.domain.port;

import java.util.List;

/**
 * 库存服务（出站端口）。预占结果分三种，第三种是分布式系统特有的：
 *
 * <ul>
 *   <li>RESERVED：成功
 *   <li>REJECTED：对方明确说"不行"（库存不足），什么都没扣
 *   <li>UNKNOWN：超时、5xx、熔断——不知道对方到底扣没扣，必须按"可能扣了"来补偿
 * </ul>
 */
public interface InventoryPort {

    ReserveOutcome reserve(String orderNo, List<Line> lines);

    record Line(long skuId, int quantity) {}

    record ReserveOutcome(Status status, String message) {

        public static ReserveOutcome reserved() {
            return new ReserveOutcome(Status.RESERVED, "ok");
        }

        public static ReserveOutcome rejected(String message) {
            return new ReserveOutcome(Status.REJECTED, message);
        }

        public static ReserveOutcome unknown(String message) {
            return new ReserveOutcome(Status.UNKNOWN, message);
        }
    }

    enum Status {
        RESERVED,
        REJECTED,
        UNKNOWN
    }
}

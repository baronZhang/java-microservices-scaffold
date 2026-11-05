package com.wd.ms.order.infrastructure.persistence;

import com.wd.ms.common.core.PageQuery;
import com.wd.ms.common.core.PageResult;
import com.wd.ms.common.core.UtcTimes;
import com.wd.ms.order.domain.order.CancelReason;
import com.wd.ms.order.domain.order.Order;
import com.wd.ms.order.domain.order.OrderItem;
import com.wd.ms.order.domain.order.OrderRepository;
import com.wd.ms.order.domain.order.OrderStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 订单表（orders + order_item）的 JDBC 实现。 */
@Repository
public class JdbcOrderRepository implements OrderRepository {

    private final JdbcClient jdbc;

    public JdbcOrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Order order, Instant now) {
        jdbc.sql("""
                INSERT INTO orders (order_no, user_id, status, cancel_reason, total_amount, idempotency_key,
                                    request_hash, expire_at, paid_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)
                .params(
                        order.orderNo(),
                        order.userId(),
                        order.status().name(),
                        null,
                        order.totalAmount(),
                        order.idempotencyKey(),
                        order.requestHash(),
                        UtcTimes.toDb(order.expireAt()),
                        null,
                        UtcTimes.toDb(order.createdAt()),
                        UtcTimes.toDb(now))
                .update();
        for (OrderItem item : order.items()) {
            jdbc.sql("""
                    INSERT INTO order_item (order_no, sku_id, product_name, unit_price, quantity)
                    VALUES (?, ?, ?, ?, ?)
                    """)
                    .params(order.orderNo(), item.skuId(), item.productName(), item.unitPrice(), item.quantity())
                    .update();
        }
    }

    @Override
    public Optional<Order> find(String orderNo) {
        return jdbc.sql("SELECT * FROM orders WHERE order_no = ?")
                .param(orderNo)
                .query(this::map)
                .optional();
    }

    @Override
    public Optional<Order> findByIdempotencyKey(long userId, String idempotencyKey) {
        return jdbc.sql("SELECT * FROM orders WHERE user_id = ? AND idempotency_key = ?")
                .params(userId, idempotencyKey)
                .query(this::map)
                .optional();
    }

    @Override
    public PageResult<Order> pageByUser(long userId, PageQuery page) {
        long total = jdbc.sql("SELECT COUNT(*) FROM orders WHERE user_id = ?")
                .param(userId)
                .query(Long.class)
                .single();
        List<Order> list = jdbc.sql("SELECT * FROM orders WHERE user_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?")
                .params(userId, page.pageSize(), page.offset())
                .query(this::map)
                .list();
        return new PageResult<>(list, total, page.page(), page.pageSize());
    }

    @Override
    public boolean transition(Order target, OrderStatus expectedStatus, Instant now) {
        return jdbc.sql("""
                UPDATE orders SET status = ?, cancel_reason = ?, expire_at = ?, paid_at = ?, updated_at = ?
                WHERE order_no = ? AND status = ?
                """)
                        .params(
                                target.status().name(),
                                target.cancelReason() == null
                                        ? null
                                        : target.cancelReason().name(),
                                UtcTimes.toDb(target.expireAt()),
                                UtcTimes.toDb(target.paidAt()),
                                UtcTimes.toDb(now),
                                target.orderNo(),
                                expectedStatus.name())
                        .update()
                == 1;
    }

    @Override
    public List<Order> findExpired(Instant now, int limit) {
        // 走 (status, expire_at) 联合索引：只扫"待支付且已过期"的那一小段
        return jdbc.sql(
                        "SELECT * FROM orders WHERE status = 'PENDING_PAYMENT' AND expire_at <= ? ORDER BY expire_at LIMIT ?")
                .params(UtcTimes.toDb(now), limit)
                .query(this::map)
                .list();
    }

    @Override
    public List<Order> findStuckReserving(Instant createdBefore, int limit) {
        return jdbc.sql(
                        "SELECT * FROM orders WHERE status = 'RESERVING' AND created_at <= ? ORDER BY created_at LIMIT ?")
                .params(UtcTimes.toDb(createdBefore), limit)
                .query(this::map)
                .list();
    }

    private Order map(ResultSet rs, int row) throws SQLException {
        String orderNo = rs.getString("order_no");
        String reason = rs.getString("cancel_reason");
        return new Order(
                orderNo,
                rs.getLong("user_id"),
                OrderStatus.valueOf(rs.getString("status")),
                reason == null ? null : CancelReason.valueOf(reason),
                items(orderNo),
                rs.getBigDecimal("total_amount"),
                rs.getString("idempotency_key"),
                rs.getString("request_hash"),
                UtcTimes.fromDb(rs.getObject("created_at", LocalDateTime.class)),
                UtcTimes.fromDb(rs.getObject("expire_at", LocalDateTime.class)),
                UtcTimes.fromDb(rs.getObject("paid_at", LocalDateTime.class)));
    }

    private List<OrderItem> items(String orderNo) {
        return jdbc.sql(
                        "SELECT sku_id, product_name, unit_price, quantity FROM order_item WHERE order_no = ? ORDER BY sku_id")
                .param(orderNo)
                .query(OrderItem.class)
                .list();
    }
}

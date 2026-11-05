package com.wd.ms.inventory.infrastructure.persistence;

import com.wd.ms.common.core.UtcTimes;
import com.wd.ms.inventory.domain.reservation.Reservation;
import com.wd.ms.inventory.domain.reservation.ReservationLine;
import com.wd.ms.inventory.domain.reservation.ReservationRepository;
import com.wd.ms.inventory.domain.reservation.ReservationStatus;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 预占记录表（reservation + reservation_item）的 JDBC 实现。 */
@Repository
public class JdbcReservationRepository implements ReservationRepository {

    private final JdbcClient jdbc;
    private final Clock clock;

    public JdbcReservationRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Optional<Reservation> find(String orderNo) {
        Optional<ReservationStatus> status = jdbc.sql("SELECT status FROM reservation WHERE order_no = ?")
                .param(orderNo)
                .query(String.class)
                .optional()
                .map(ReservationStatus::valueOf);
        return status.map(value -> new Reservation(orderNo, value, lines(orderNo)));
    }

    @Override
    public void insert(Reservation reservation) {
        var now = UtcTimes.toDb(clock.instant());
        jdbc.sql("INSERT INTO reservation (order_no, status, created_at, updated_at) VALUES (?, ?, ?, ?)")
                .params(reservation.orderNo(), reservation.status().name(), now, now)
                .update();
        for (ReservationLine line : reservation.lines()) {
            jdbc.sql("INSERT INTO reservation_item (order_no, sku_id, quantity) VALUES (?, ?, ?)")
                    .params(reservation.orderNo(), line.skuId(), line.quantity())
                    .update();
        }
    }

    @Override
    public boolean transition(String orderNo, ReservationStatus from, ReservationStatus to) {
        return jdbc.sql("UPDATE reservation SET status = ?, updated_at = ? WHERE order_no = ? AND status = ?")
                        .params(to.name(), UtcTimes.toDb(clock.instant()), orderNo, from.name())
                        .update()
                == 1;
    }

    private List<ReservationLine> lines(String orderNo) {
        return jdbc.sql("SELECT sku_id, quantity FROM reservation_item WHERE order_no = ? ORDER BY sku_id")
                .param(orderNo)
                .query((rs, row) -> new ReservationLine(rs.getLong("sku_id"), rs.getInt("quantity")))
                .list();
    }
}

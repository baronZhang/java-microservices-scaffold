package com.wd.ms.inventory.application;

import com.wd.ms.inventory.domain.reservation.Reservation;
import com.wd.ms.inventory.domain.reservation.ReservationLine;
import com.wd.ms.inventory.domain.reservation.ReservationRepository;
import com.wd.ms.inventory.domain.reservation.ReservationStatus;
import com.wd.ms.inventory.domain.stock.StockRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 库存预占、确认、释放。这是下单链路一致性方案在库存侧的全部逻辑，借鉴了 TCC 的三个经典问题：
 *
 * <ul>
 *   <li>幂等：同一订单号重复预占、重复确认、重复释放，结果都只生效一次
 *   <li>空回滚：预占请求超时没到（或根本没发出），却先收到了释放 → 记一条 RELEASED，什么也不退
 *   <li>防悬挂：释放之后，迟到的预占请求才到 → 看到 RELEASED 就拒绝，不能再扣库存
 * </ul>
 */
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final StockRepository stocks;
    private final ReservationRepository reservations;
    private final TransactionTemplate tx;

    public ReservationService(StockRepository stocks, ReservationRepository reservations, TransactionTemplate tx) {
        this.stocks = stocks;
        this.reservations = reservations;
        this.tx = tx;
    }

    public ReservationResult reserve(String orderNo, List<ReservationLine> lines) {
        Optional<Reservation> existing = reservations.find(orderNo);
        if (existing.isPresent()) {
            return replay(existing.get());
        }
        List<ReservationLine> sorted = mergeAndSort(lines);
        try {
            return tx.execute(status -> {
                // 先插入预占记录（订单号是主键），相当于"占坑"：并发的重复请求在这里撞主键，整个事务回滚
                reservations.insert(new Reservation(orderNo, ReservationStatus.RESERVED, sorted));
                for (ReservationLine line : sorted) {
                    if (!stocks.tryReserve(line.skuId(), line.quantity())) {
                        // 任何一个商品不够，整单失败：标记回滚，前面已经扣掉的商品一起恢复
                        status.setRollbackOnly();
                        return ReservationResult.rejected(orderNo, "库存不足：商品 " + line.skuId());
                    }
                }
                return ReservationResult.ok(orderNo);
            });
        } catch (DuplicateKeyException e) {
            log.info("订单 {} 的预占请求并发重复，以先到的那次为准", orderNo);
            return reservations.find(orderNo).map(ReservationService::replay).orElseThrow(() -> e);
        }
    }

    /** 支付成功：预占转为已售。只有 RESERVED 能确认 */
    public void confirm(String orderNo) {
        tx.executeWithoutResult(status -> {
            Optional<Reservation> found = reservations.find(orderNo);
            if (found.isEmpty()) {
                log.error("订单 {} 收到支付确认，但没有预占记录，需要人工核对", orderNo);
                return;
            }
            Reservation reservation = found.get();
            if (reservation.status() == ReservationStatus.RELEASED) {
                log.error("订单 {} 的库存已经释放，却收到支付确认，需要人工核对", orderNo);
                return;
            }
            // 条件迁移失败 = 已经确认过（重复消息），什么都不做
            if (reservations.transition(orderNo, ReservationStatus.RESERVED, ReservationStatus.CONFIRMED)) {
                reservation.lines().forEach(line -> stocks.confirm(line.skuId(), line.quantity()));
                log.info("订单 {} 已支付，预占的库存转为已售", orderNo);
            }
        });
    }

    /** 取消或超时：退回预占的库存；从没预占过就做空回滚 */
    public void release(String orderNo) {
        try {
            tx.executeWithoutResult(status -> {
                Optional<Reservation> found = reservations.find(orderNo);
                if (found.isEmpty()) {
                    // 空回滚：记一条 RELEASED，之后迟到的预占请求会被拒绝（防悬挂）
                    reservations.insert(new Reservation(orderNo, ReservationStatus.RELEASED, List.of()));
                    log.info("订单 {} 没有预占记录，执行空回滚", orderNo);
                    return;
                }
                Reservation reservation = found.get();
                if (reservation.status() == ReservationStatus.CONFIRMED) {
                    log.error("订单 {} 已经支付，不能释放库存，需要人工核对", orderNo);
                    return;
                }
                if (reservations.transition(orderNo, ReservationStatus.RESERVED, ReservationStatus.RELEASED)) {
                    reservation.lines().forEach(line -> stocks.release(line.skuId(), line.quantity()));
                    log.info("订单 {} 已取消，释放预占的库存", orderNo);
                }
            });
        } catch (DuplicateKeyException e) {
            // 空回滚和一个迟到的预占同时发生，预占先插入成功了：再走一遍，这次能查到记录
            release(orderNo);
        }
    }

    private static ReservationResult replay(Reservation reservation) {
        return reservation.status() == ReservationStatus.RELEASED
                ? ReservationResult.rejected(reservation.orderNo(), "订单已取消，不能再预占库存")
                : ReservationResult.ok(reservation.orderNo());
    }

    /**
     * 同一商品合并数量，并按商品 id 排序。
     * 排序是为了固定加锁顺序：订单 A 买 (1, 2)、订单 B 买 (2, 1)，如果各自按原顺序扣，可能互相等待对方的行锁而死锁。
     */
    static List<ReservationLine> mergeAndSort(List<ReservationLine> lines) {
        Map<Long, Integer> merged = new TreeMap<>();
        lines.forEach(line -> merged.merge(line.skuId(), line.quantity(), Integer::sum));
        return merged.entrySet().stream()
                .map(entry -> new ReservationLine(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(ReservationLine::skuId))
                .toList();
    }
}

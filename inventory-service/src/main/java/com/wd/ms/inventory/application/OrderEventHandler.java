package com.wd.ms.inventory.application;

import com.wd.ms.inventory.domain.message.ConsumedMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单事件的消费逻辑，HTTP 推送（默认 profile）和 Kafka（kafka profile）两种入口共用它。
 *
 * <p>消息投递只能做到"至少一次"：生产方没收到确认就会重发。所以消费方必须幂等：
 * 判重记录和业务修改放在同一个本地事务里，要么都成功，要么都不成功。
 */
@Service
public class OrderEventHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderEventHandler.class);
    public static final String TOPIC = "order-events";

    private final ConsumedMessageRepository consumedMessages;
    private final ReservationService reservationService;

    public OrderEventHandler(ConsumedMessageRepository consumedMessages, ReservationService reservationService) {
        this.consumedMessages = consumedMessages;
        this.reservationService = reservationService;
    }

    /** 返回 true 表示这次真正处理了；false 表示重复消息，已忽略 */
    @Transactional
    public boolean handle(OrderEvent event) {
        if (consumedMessages.exists(event.eventId())) {
            log.info("重复的订单事件 {}（{} {}），已忽略", event.eventId(), event.type(), event.orderNo());
            return false;
        }
        // 先插判重记录：两个重复消息并发到达时，后插入的撞主键失败、整个事务回滚，生产方稍后重投时会走上面的分支
        consumedMessages.insert(event.eventId(), TOPIC);
        switch (event.type()) {
            case OrderEvent.ORDER_PAID -> reservationService.confirm(event.orderNo());
            case OrderEvent.ORDER_CANCELLED -> reservationService.release(event.orderNo());
            default -> log.warn("不认识的订单事件类型 {}，跳过", event.type());
        }
        return true;
    }
}

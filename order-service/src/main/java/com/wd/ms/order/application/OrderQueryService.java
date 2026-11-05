package com.wd.ms.order.application;

import com.wd.ms.common.core.BizException;
import com.wd.ms.common.core.PageQuery;
import com.wd.ms.common.core.PageResult;
import com.wd.ms.order.domain.order.Order;
import com.wd.ms.order.domain.order.OrderRepository;
import org.springframework.stereotype.Service;

/** 订单查询：只能看自己的订单；有 order:all 权限（演示账号里只有管理员的 *）可以看任意订单。 */
@Service
public class OrderQueryService {

    private final OrderRepository orders;

    public OrderQueryService(OrderRepository orders) {
        this.orders = orders;
    }

    public Order get(String orderNo, long userId, boolean canViewAll) {
        Order order = orders.find(orderNo).orElseThrow(() -> BizException.notFound("订单 " + orderNo + " 不存在"));
        order.checkVisibleTo(userId, canViewAll);
        return order;
    }

    public PageResult<Order> page(long userId, PageQuery page) {
        return orders.pageByUser(userId, page);
    }
}

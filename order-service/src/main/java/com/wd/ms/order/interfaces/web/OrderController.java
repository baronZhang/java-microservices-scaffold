package com.wd.ms.order.interfaces.web;

import com.wd.ms.common.core.PageQuery;
import com.wd.ms.common.core.PageResult;
import com.wd.ms.common.core.Result;
import com.wd.ms.common.security.CurrentUser;
import com.wd.ms.order.application.CreateOrderResult;
import com.wd.ms.order.application.OrderCommandService;
import com.wd.ms.order.application.OrderLine;
import com.wd.ms.order.application.OrderQueryService;
import com.wd.ms.order.domain.order.Order;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 订单接口（经网关 /api/orders/**），登录即可下单，只能看和操作自己的订单。 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderCommandService commands;
    private final OrderQueryService queries;

    public OrderController(OrderCommandService commands, OrderQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    /**
     * 下单必须带 Idempotency-Key 请求头（客户端每次"提交"生成一个 UUID，重试时复用同一个）。
     * 网络超时后客户端重发，服务端按这个键识别出是同一次提交，不会生成两张订单。
     */
    @PostMapping
    public ResponseEntity<Result<OrderResponse>> create(
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 64) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        List<OrderLine> lines = request.items().stream()
                .map(item -> new OrderLine(item.skuId(), item.quantity()))
                .toList();
        CreateOrderResult result = commands.create(CurrentUser.get().id(), idempotencyKey, lines);
        var response = ResponseEntity.ok();
        if (result.replayed()) {
            // 告诉客户端：这是重放的结果，不是新下的单（和单体版、Go 版的约定一致）
            response.header(IDEMPOTENT_REPLAYED, "true");
        }
        return response.body(Result.ok(OrderResponse.from(result.order())));
    }

    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";
    static final String VIEW_ALL_ORDERS = "order:all";

    @GetMapping
    public Result<PageResult<OrderResponse>> page(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        return Result.ok(queries.page(CurrentUser.get().id(), PageQuery.of(page, pageSize))
                .map(OrderResponse::from));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderResponse> get(@PathVariable String orderNo) {
        CurrentUser user = CurrentUser.get();
        return Result.ok(OrderResponse.from(queries.get(orderNo, user.id(), user.hasPermission(VIEW_ALL_ORDERS))));
    }

    /** 模拟支付（真实项目里是支付渠道的异步回调，同样要幂等） */
    @PostMapping("/{orderNo}/pay")
    public Result<OrderResponse> pay(@PathVariable String orderNo) {
        return Result.ok(
                OrderResponse.from(commands.pay(orderNo, CurrentUser.get().id())));
    }

    @PostMapping("/{orderNo}/cancel")
    public Result<OrderResponse> cancel(@PathVariable String orderNo) {
        return Result.ok(
                OrderResponse.from(commands.cancel(orderNo, CurrentUser.get().id())));
    }

    public record CreateOrderRequest(
            @NotEmpty @Size(max = 10) List<@Valid Item> items) {}

    public record Item(@Min(1) long skuId, @Min(1) @Max(99) int quantity) {}

    /** 订单号是字符串：雪花 id 是 64 位整数，超过 JS Number 的安全整数（2^53 - 1），当数字传给前端会丢精度 */
    public record OrderResponse(
            String orderNo,
            String status,
            String cancelReason,
            BigDecimal totalAmount,
            List<ItemResponse> items,
            Instant createdAt,
            Instant expireAt,
            Instant paidAt) {

        static OrderResponse from(Order order) {
            return new OrderResponse(
                    order.orderNo(),
                    order.status().name(),
                    order.cancelReason() == null ? null : order.cancelReason().name(),
                    order.totalAmount(),
                    order.items().stream()
                            .map(item -> new ItemResponse(
                                    item.skuId(), item.productName(), item.unitPrice(), item.quantity()))
                            .toList(),
                    order.createdAt(),
                    order.expireAt(),
                    order.paidAt());
        }
    }

    public record ItemResponse(long skuId, String productName, BigDecimal unitPrice, int quantity) {}
}

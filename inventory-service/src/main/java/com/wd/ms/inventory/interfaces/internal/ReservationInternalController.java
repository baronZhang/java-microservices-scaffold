package com.wd.ms.inventory.interfaces.internal;

import com.wd.ms.common.core.BizException;
import com.wd.ms.common.core.ErrorCode;
import com.wd.ms.common.core.Result;
import com.wd.ms.inventory.application.ReservationResult;
import com.wd.ms.inventory.application.ReservationService;
import com.wd.ms.inventory.domain.reservation.ReservationLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部接口：订单服务下单时同步预占库存。只接受带 internal scope 的服务令牌（安全 starter 统一配置）。
 *
 * <p>契约见 contracts/order-to-inventory/，提供方和消费方的测试都用同一份样例。
 */
@RestController
@RequestMapping("/internal/inventory/reservations")
public class ReservationInternalController {

    private final ReservationService reservationService;

    public ReservationInternalController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /** 库存不足是业务结果，不是系统故障：HTTP 200 + 业务码 20001，调用方据此判断"确定失败"而不是"不知道成没成功" */
    @PostMapping
    public Result<ReservationResult> reserve(@Valid @RequestBody ReserveRequest request) {
        List<ReservationLine> lines = request.items().stream()
                .map(item -> new ReservationLine(item.skuId(), item.quantity()))
                .toList();
        ReservationResult result = reservationService.reserve(request.orderNo(), lines);
        if (!result.reserved()) {
            throw new BizException(ErrorCode.STOCK_INSUFFICIENT, result.message());
        }
        return Result.ok(result);
    }

    public record ReserveRequest(
            @NotBlank String orderNo, @NotEmpty List<@Valid Item> items) {}

    public record Item(long skuId, @Min(1) @Max(999) int quantity) {}
}

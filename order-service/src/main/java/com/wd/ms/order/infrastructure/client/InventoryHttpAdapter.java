package com.wd.ms.order.infrastructure.client;

import com.wd.ms.common.core.ErrorCode;
import com.wd.ms.common.core.Result;
import com.wd.ms.order.domain.port.InventoryPort;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.List;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * 预占库存的调用，四层保护（由内到外）：
 *
 * <ol>
 *   <li>超时：HTTP 客户端的 connect/read timeout（配置文件按分组设置）
 *   <li>重试：只对"连接失败、5xx"重试 2 次，指数退避。预占接口按订单号幂等，所以可以安全重试；
 *       4xx 和业务失败（库存不足）不重试——重试也不会变好
 *   <li>熔断：最近的调用失败率过高就打开熔断器，后面的请求直接失败，不再打到一个已经不行的服务上
 *   <li>降级：熔断或最终失败都翻译成 UNKNOWN，由应用层按"结果未知"处理（取消订单 + 释放事件兜底）
 * </ol>
 *
 * <p>重试放在熔断里面：一次下单的几次重试只算熔断器的"一次调用"，避免重试把失败率放大。
 *
 * <p>直接用 Resilience4j 的 API，而不是 Spring Cloud CircuitBreaker 的 {@code CircuitBreakerFactory.run()}：
 * 后者默认把阻塞调用放进线程池、再套一个 1 秒的 TimeLimiter，相关开关在 5.0.3 里还有互相忽略的情况（见文档"踩坑记录"）。
 * 这里在调用线程上同步执行，行为确定、好测试。熔断器的参数仍然来自配置文件 resilience4j.circuitbreaker.*。
 */
@Component
public class InventoryHttpAdapter implements InventoryPort {

    static final String CIRCUIT_BREAKER = "inventory-reserve";

    private final InventoryClient client;
    private final CircuitBreaker circuitBreaker;
    private final RetryTemplate retryTemplate;

    public InventoryHttpAdapter(InventoryClient client, CircuitBreakerRegistry circuitBreakers) {
        this.client = client;
        this.circuitBreaker = circuitBreakers.circuitBreaker(CIRCUIT_BREAKER);
        this.retryTemplate = new RetryTemplate(RetryPolicy.builder()
                .maxRetries(2)
                .delay(Duration.ofMillis(100))
                .multiplier(2)
                .includes(ResourceAccessException.class, HttpServerErrorException.class)
                .build());
    }

    @Override
    public ReserveOutcome reserve(String orderNo, List<Line> lines) {
        var request = new InventoryClient.ReserveRequest(
                orderNo,
                lines.stream()
                        .map(line -> new InventoryClient.Item(line.skuId(), line.quantity()))
                        .toList());
        try {
            Result<InventoryClient.ReservationDto> result =
                    circuitBreaker.executeSupplier(() -> retryTemplate.invoke(() -> client.reserve(request)));
            return toOutcome(result);
        } catch (CallNotPermittedException e) {
            return ReserveOutcome.unknown("熔断器已打开，暂停调用库存服务");
        } catch (RuntimeException e) {
            return ReserveOutcome.unknown(e.toString());
        }
    }

    private static ReserveOutcome toOutcome(Result<InventoryClient.ReservationDto> result) {
        if (result.succeeded()) {
            return ReserveOutcome.reserved();
        }
        if (result.code() == ErrorCode.STOCK_INSUFFICIENT.code()) {
            return ReserveOutcome.rejected(result.message());
        }
        return ReserveOutcome.unknown("库存服务返回 " + result.code() + " " + result.message());
    }
}

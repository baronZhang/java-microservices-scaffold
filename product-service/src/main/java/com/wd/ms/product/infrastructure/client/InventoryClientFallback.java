package com.wd.ms.product.infrastructure.client;

import com.wd.ms.common.core.ErrorCode;
import com.wd.ms.common.core.Result;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 库存客户端的降级实现：调用失败（超时、5xx、实例全挂）或熔断器打开时，Spring Cloud 调用这里同名的方法。
 * 第一个参数是 Throwable 的版本可以拿到失败原因。
 *
 * <p>要求：类和方法都是 public、有无参构造器（由框架实例化，不能注入 Bean）。
 */
public class InventoryClientFallback {

    private static final Logger log = LoggerFactory.getLogger(InventoryClientFallback.class);

    public Result<List<InventoryClient.StockDto>> stocks(Throwable cause, List<Long> skuIds) {
        log.warn("库存服务不可用，商品 {} 的库存降级为\"未知\"：{}", skuIds, cause.toString());
        return Result.fail(ErrorCode.SERVICE_UNAVAILABLE);
    }
}

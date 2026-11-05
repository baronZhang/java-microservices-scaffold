package com.wd.ms.order.infrastructure.client;

import com.wd.ms.common.core.BizException;
import com.wd.ms.common.core.ErrorCode;
import com.wd.ms.common.core.Result;
import com.wd.ms.order.domain.port.ProductPort;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/** 查询商品快照。查询天然幂等，失败重试 1 次；还不行就快速失败，此时订单还没落库，没有任何副作用。 */
@Component
public class ProductHttpAdapter implements ProductPort {

    private static final Logger log = LoggerFactory.getLogger(ProductHttpAdapter.class);
    private final ProductClient client;
    private final RetryTemplate retryTemplate;

    public ProductHttpAdapter(ProductClient client) {
        this.client = client;
        this.retryTemplate = new RetryTemplate(RetryPolicy.builder()
                .maxRetries(1)
                .delay(Duration.ofMillis(100))
                .includes(ResourceAccessException.class, HttpServerErrorException.class)
                .build());
    }

    @Override
    public Map<Long, ProductSnapshot> findProducts(Collection<Long> skuIds) {
        Result<List<ProductClient.ProductDto>> result;
        try {
            result = retryTemplate.invoke(() -> client.products(List.copyOf(skuIds)));
        } catch (RuntimeException e) {
            log.warn("查询商品失败：{}", e.toString());
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "商品服务暂不可用，请稍后重试");
        }
        if (!result.succeeded() || result.data() == null) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "商品服务暂不可用，请稍后重试");
        }
        return result.data().stream()
                .collect(Collectors.toMap(
                        ProductClient.ProductDto::id,
                        dto -> new ProductSnapshot(dto.id(), dto.name(), dto.price(), dto.onSale())));
    }
}

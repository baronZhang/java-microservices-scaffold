package com.wd.ms.order.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.wd.ms.common.test.StubServer;
import com.wd.ms.order.domain.port.InventoryPort;
import com.wd.ms.order.domain.port.InventoryPort.Line;
import com.wd.ms.order.domain.port.InventoryPort.Status;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

/**
 * 消费方契约测试 + 调用保护：订单服务真实的 HTTP 客户端（负载均衡、服务令牌、重试、熔断都在）对着一个 stub 库存服务。
 *
 * <ul>
 *   <li>契约：发出的请求体必须和 contracts/ 里的样例一致；样例响应必须能被正确解析
 *   <li>重试：503 会重试，总共 3 次
 *   <li>熔断：连续失败后打开，之后不再发请求，直接得到 UNKNOWN
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InventoryContractIT {

    private static final Path CONTRACTS = Path.of("../contracts/order-to-inventory");
    static final StubServer STUB = StubServer.start()
            .respond(
                    "/oauth2/token",
                    200,
                    "{\"access_token\":\"stub-token\",\"token_type\":\"Bearer\",\"expires_in\":600}");

    @DynamicPropertySource
    static void stubAsDownstream(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.inventory-service[0].uri", STUB::url);
        registry.add("spring.cloud.discovery.client.simple.instances.auth-service[0].uri", STUB::url);
    }

    @AfterAll
    static void stop() {
        STUB.close();
    }

    @Autowired
    InventoryPort inventory;

    @Autowired
    JsonMapper json;

    @Autowired
    CircuitBreakerRegistry circuitBreakers;

    @BeforeEach
    void reset() {
        STUB.reset();
    }

    @Test
    @Order(1)
    void 预占请求和响应符合契约() throws Exception {
        STUB.respond("/internal/inventory/reservations", 200, read("reserve-response-ok.json"));
        var outcome = inventory.reserve("contract-order-1", List.of(new Line(1, 2), new Line(2, 1)));
        assertThat(outcome.status()).isEqualTo(Status.RESERVED);

        var request = STUB.received().stream()
                .filter(r -> r.path().equals("/internal/inventory/reservations"))
                .findFirst()
                .orElseThrow();
        assertThat(json.readTree(request.body())).isEqualTo(json.readTree(read("reserve-request.json")));
        assertThat(request.authorization()).isEqualTo("Bearer stub-token");
    }

    @Test
    @Order(2)
    void 库存不足的契约响应被解析成REJECTED() throws Exception {
        STUB.respond("/internal/inventory/reservations", 200, read("reserve-response-insufficient.json"));
        var outcome = inventory.reserve("contract-order-2", List.of(new Line(3, 999)));
        assertThat(outcome.status()).isEqualTo(Status.REJECTED);
        assertThat(outcome.message()).isEqualTo("库存不足：商品 3");
    }

    @Test
    @Order(3)
    void 服务端503会重试_连续失败后熔断() {
        STUB.respond("/internal/inventory/reservations", 503, "{\"code\":503,\"message\":\"busy\",\"data\":null}");
        var first = inventory.reserve("retry-1", List.of(new Line(1, 1)));
        assertThat(first.status()).isEqualTo(Status.UNKNOWN);
        // 1 次原始请求 + 2 次重试
        assertThat(STUB.count("/internal/inventory/reservations"))
                .as(first.message())
                .isEqualTo(3);

        for (int i = 0; i < 5; i++) {
            inventory.reserve("retry-" + (i + 2), List.of(new Line(1, 1)));
        }
        assertThat(circuitBreakers.circuitBreaker("inventory-reserve").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);
        int calls = STUB.count("/internal/inventory/reservations");
        assertThat(inventory.reserve("after-open", List.of(new Line(1, 1))).status())
                .isEqualTo(Status.UNKNOWN);
        assertThat(STUB.count("/internal/inventory/reservations")).isEqualTo(calls);
    }

    private static String read(String name) throws Exception {
        return Files.readString(CONTRACTS.resolve(name));
    }
}

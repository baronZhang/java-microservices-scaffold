package com.wd.ms.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.wd.ms.common.test.StubServer;
import com.wd.ms.common.test.TestJwts;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 熔断降级：库存服务（用一个本地 stub 冒充）变慢或出错时，商品详情仍然能返回，只是库存显示"未知"；
 * 连续失败后熔断器打开，之后的请求不再等超时，而是立即降级。
 *
 * <p>stub 同时冒充认证服务的令牌端点（服务间调用要先换服务令牌），两个服务名都通过静态服务发现指向它。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwts.Config.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InventoryDegradeIT {

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
    MockMvc mvc;

    @Autowired
    CircuitBreakerRegistry circuitBreakers;

    @BeforeEach
    void reset() {
        STUB.reset();
    }

    @Test
    @Order(1)
    void 库存服务正常时显示库存档位() throws Exception {
        STUB.respond(
                "/internal/inventory/stocks",
                200,
                "{\"code\":0,\"message\":\"ok\",\"data\":[{\"skuId\":1,\"available\":8}]}");
        mvc.perform(get("/api/products/1").header("Authorization", "Bearer " + TestJwts.guest()))
                .andExpect(jsonPath("$.data.stock").value(8))
                .andExpect(jsonPath("$.data.stockStatus").value("LOW_STOCK"));
        // 服务间调用带的是服务令牌，不是用户的令牌
        assertThat(STUB.received())
                .anySatisfy(request -> assertThat(request.authorization()).isEqualTo("Bearer stub-token"));
    }

    @Test
    @Order(2)
    void 库存服务变慢_超时后降级_连续失败后熔断() throws Exception {
        // 比 read-timeout（1s）还慢：每次调用都会超时
        STUB.respond("/internal/inventory/stocks", 200, "{\"code\":0,\"message\":\"ok\",\"data\":[]}", 1500);
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/products/1").header("Authorization", "Bearer " + TestJwts.guest()))
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.name").value("机械键盘 K8"))
                    .andExpect(jsonPath("$.data.stock").doesNotExist())
                    .andExpect(jsonPath("$.data.stockStatus").value("UNKNOWN"));
        }
        // 每次降级最多打一次下游（不会因为熔断、重试的叠加而放大请求）；
        // 第 5 次时熔断器可能已经打开（窗口里还有上一个用例的 1 次成功调用），所以是 4 或 5
        assertThat(STUB.count("/internal/inventory/stocks")).isBetween(4, 5);
        CircuitBreaker breaker = circuitBreakers.circuitBreaker("inventory-service");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // 熔断打开后：不再调用库存服务，立即降级
        int callsBefore = STUB.count("/internal/inventory/stocks");
        long start = System.nanoTime();
        mvc.perform(get("/api/products/1").header("Authorization", "Bearer " + TestJwts.guest()))
                .andExpect(jsonPath("$.data.stockStatus").value("UNKNOWN"));
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertThat(STUB.count("/internal/inventory/stocks")).isEqualTo(callsBefore);
        assertThat(millis).isLessThan(500);
    }
}

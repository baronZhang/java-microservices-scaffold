package com.wd.ms.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.wd.ms.common.test.StubServer;
import com.wd.ms.common.test.TestJwts;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 网关集成测试：真实启动网关（随机端口），下游服务全部用 stub 冒充。
 * 覆盖：统一认证、只认用户令牌、吊销（演示接口 + 退出登录）、内部接口不暴露、登录限流、灰度路由、下游不可用。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "demo"})
class GatewayIT {

    static final StubServer USERS = StubServer.start()
            .respond("/api/users", 200, "{\"code\":0,\"message\":\"ok\",\"data\":\"from-user-service\"}")
            .respond("/api/auth", 200, "{\"code\":0,\"message\":\"ok\",\"data\":null}");
    static final StubServer STABLE =
            StubServer.start().respond("/api/products", 200, "{\"code\":0,\"message\":\"ok\",\"data\":\"stable\"}");
    static final StubServer GRAY =
            StubServer.start().respond("/api/products", 200, "{\"code\":0,\"message\":\"ok\",\"data\":\"gray\"}");

    @DynamicPropertySource
    static void downstream(DynamicPropertyRegistry registry) {
        String simple = "spring.cloud.discovery.client.simple.instances.";
        registry.add(simple + "user-service[0].uri", USERS::url);
        registry.add(simple + "auth-service[0].uri", USERS::url);
        registry.add(simple + "product-service[0].uri", STABLE::url);
        registry.add(simple + "product-service[0].metadata.hint", () -> "stable");
        registry.add(simple + "product-service[1].uri", GRAY::url);
        registry.add(simple + "product-service[1].metadata.hint", () -> "gray");
        // 订单服务指向一个没人监听的端口：模拟"服务全挂了"
        registry.add(simple + "order-service[0].uri", () -> "http://127.0.0.1:1");
    }

    @AfterAll
    static void stop() {
        USERS.close();
        STABLE.close();
        GRAY.close();
    }

    @LocalServerPort
    int port;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client =
                WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        USERS.reset();
    }

    @Test
    void 不带token返回401和统一响应结构_带traceId() {
        client.get()
                .uri("/api/users")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectHeader()
                .exists("X-Trace-Id")
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(401)
                .jsonPath("$.data")
                .isEmpty();
    }

    @Test
    void 服务令牌不能从网关进来() {
        client.get()
                .uri("/api/users")
                .header("Authorization", "Bearer " + TestJwts.service("order-service"))
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void 有效token转发到下游_原样透传Authorization() {
        String token = TestJwts.editor();
        client.get()
                .uri("/api/users")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data")
                .isEqualTo("from-user-service");
        assertThat(USERS.received())
                .anySatisfy(request -> assertThat(request.authorization()).isEqualTo("Bearer " + token));
    }

    @Test
    void 公开接口不需要token() {
        client.post()
                .uri("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void 演示接口让token立即失效() {
        String token = TestJwts.editor();
        client.post()
                .uri("/api/debug/expire-token")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);
        client.get()
                .uri("/api/users")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void 退出登录成功后旧token立即失效() {
        String token = TestJwts.guest();
        client.post()
                .uri("/api/auth/logout")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isOk();
        client.get()
                .uri("/api/users")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    void 内部接口不经网关暴露() {
        client.get()
                .uri("/internal/inventory/stocks?skuIds=1")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(404);
    }

    @Test
    void 登录接口按IP限流_超出后返回429() {
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            statuses.add(client.post()
                    .uri("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{}")
                    .exchange()
                    .returnResult(String.class)
                    .getStatus()
                    .value());
        }
        // 桶容量 3：前 3 个放行，后面的被拒绝（测试执行很快，期间补充的令牌不到 1 个）
        assertThat(statuses.subList(0, 3)).containsOnly(200);
        assertThat(statuses).contains(429);
        client.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus()
                .isEqualTo(429)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(429);
    }

    @Test
    void 灰度路由_默认走稳定版_带请求头或在白名单里走灰度版() {
        String normalUser = TestJwts.user(7, "user007", List.of("editor"), List.of());
        // 健康检查是逐个实例完成的：稳定版实例通过检查之前，严格灰度筛选宁可返回 503 也不把普通流量打到灰度版。
        // 先等它通过（最多 5 秒），再验证路由
        for (int i = 0; i < 50; i++) {
            var result = client.get()
                    .uri("/api/products")
                    .header("Authorization", "Bearer " + normalUser)
                    .exchange()
                    .returnResult(String.class);
            if (result.getStatus().value() == 200) {
                break;
            }
            sleep(100);
        }
        for (int i = 0; i < 4; i++) {
            client.get()
                    .uri("/api/products")
                    .header("Authorization", "Bearer " + normalUser)
                    .exchange()
                    .expectBody()
                    .jsonPath("$.data")
                    .isEqualTo("stable");
        }
        client.get()
                .uri("/api/products")
                .header("Authorization", "Bearer " + normalUser)
                .header("X-Gray", "gray")
                .exchange()
                .expectBody()
                .jsonPath("$.data")
                .isEqualTo("gray");
        String grayUser = TestJwts.user(42, "user042", List.of("guest"), List.of());
        client.get()
                .uri("/api/products")
                .header("Authorization", "Bearer " + grayUser)
                .exchange()
                .expectBody()
                .jsonPath("$.data")
                .isEqualTo("gray");
    }

    @Test
    void 下游服务全挂_返回503统一结构() {
        client.get()
                .uri("/api/orders")
                .header("Authorization", "Bearer " + TestJwts.guest())
                .exchange()
                .expectStatus()
                .isEqualTo(503)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(503);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 用测试密钥验签，不去请求认证服务的 JWKS */
    @TestConfiguration
    static class TestDecoderConfig {

        @Bean
        @Primary
        ReactiveJwtDecoder testReactiveJwtDecoder() {
            NimbusReactiveJwtDecoder decoder =
                    NimbusReactiveJwtDecoder.withPublicKey(TestJwts.publicKey()).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(TestJwts.ISSUER));
            return decoder;
        }
    }
}

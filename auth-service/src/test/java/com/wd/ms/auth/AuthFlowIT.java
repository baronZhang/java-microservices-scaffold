package com.wd.ms.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import db.migration.V2__SeedDemoAccounts;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 认证服务的集成测试：真实的 Spring 上下文 + 内存库 + Flyway 种子数据，经 HTTP 层（MockMvc）调用。
 * 覆盖 refresh token 轮换的三个关键点：正常轮换、复用检测、并发刷新只成功一次。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Test
    void 密码错误返回业务码10001() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void 登录后用access_token取用户信息() throws Exception {
        JsonNode pair = login("editor");
        assertThat(pair.get("expiresIn").asLong()).isEqualTo(300);
        assertThat(pair.get("refreshToken").asString()).startsWith("rt_");

        mvc.perform(get("/api/auth/profile")
                        .header(
                                "Authorization",
                                "Bearer " + pair.get("accessToken").asString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("运营小王"))
                .andExpect(jsonPath("$.data.permissions[0]").value("user:list"))
                .andExpect(jsonPath("$.data.permissions[1]").value("user:edit"));
    }

    @Test
    void 不带token访问profile返回401和统一响应结构() throws Exception {
        mvc.perform(get("/api/auth/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void refresh_token只能用一次_复用会作废整个会话() throws Exception {
        String first = login("admin").get("refreshToken").asString();

        JsonNode rotated = refresh(first);
        assertThat(rotated.get("code").asInt()).isZero();
        String second = rotated.get("data").get("refreshToken").asString();
        assertThat(second).isNotEqualTo(first);

        // 旧 token 被再次使用：可能被盗，整个会话作废
        assertThat(refresh(first).get("code").asInt()).isEqualTo(401);
        // 连刚轮换出来的新 token 也不能用了
        assertThat(refresh(second).get("code").asInt()).isEqualTo(401);
    }

    @Test
    void 同一个refresh_token并发刷新只有一个成功() throws Exception {
        String token = login("guest").get("refreshToken").asString();
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                tasks.add(() -> refresh(token).get("code").asInt());
            }
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> future : pool.invokeAll(tasks)) {
                codes.add(future.get());
            }
            assertThat(codes).filteredOn(code -> code == 0).hasSize(1);
            assertThat(codes).filteredOn(code -> code == 401).hasSize(7);
        }
    }

    @Test
    void 退出登录后refresh_token失效() throws Exception {
        JsonNode pair = login("guest");
        mvc.perform(post("/api/auth/logout")
                        .header(
                                "Authorization",
                                "Bearer " + pair.get("accessToken").asString()))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(refresh(pair.get("refreshToken").asString()).get("code").asInt())
                .isEqualTo(401);
    }

    @Test
    void 服务凭证换服务令牌_JWKS只暴露公钥() throws Exception {
        mvc.perform(post("/oauth2/token")
                        .with(request -> {
                            request.addHeader("Authorization", basic("order-service", "dev-order-secret"));
                            return request;
                        })
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.scope").value("internal"));

        mvc.perform(post("/oauth2/token")
                        .header("Authorization", basic("order-service", "wrong"))
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));

        mvc.perform(get("/oauth2/jwks"))
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                // "d" 是 RSA 私钥参数，绝不能出现在 JWKS 里
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
    }

    private JsonNode login(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\""
                                + V2__SeedDemoAccounts.DEMO_PASSWORD + "\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(body).get("data");
    }

    private JsonNode refresh(String refreshToken) throws Exception {
        String body = mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(body);
    }

    private static String basic(String id, String secret) {
        return "Basic " + java.util.Base64.getEncoder().encodeToString((id + ":" + secret).getBytes());
    }
}

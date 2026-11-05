package com.wd.ms.inventory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wd.ms.common.test.TestJwts;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 提供方契约测试：用 contracts/order-to-inventory 下的样例请求调用自己的接口，响应必须和样例一致。
 * 订单服务（消费方）的测试读的是同一份样例——两边任何一方改了字段，都会有一方的测试先失败。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwts.Config.class)
class ReservationContractIT {

    private static final Path CONTRACTS = Path.of("../contracts/order-to-inventory");

    @Autowired
    MockMvc mvc;

    @Test
    void 预占成功的契约() throws Exception {
        mvc.perform(post("/internal/inventory/reservations")
                        .header("Authorization", "Bearer " + TestJwts.service("order-service"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(read("reserve-request.json")))
                .andExpect(status().isOk())
                .andExpect(content().json(read("reserve-response-ok.json")));
    }

    @Test
    void 库存不足的契约() throws Exception {
        mvc.perform(post("/internal/inventory/reservations")
                        .header("Authorization", "Bearer " + TestJwts.service("order-service"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(read("reserve-request-insufficient.json")))
                .andExpect(status().isOk())
                .andExpect(content().json(read("reserve-response-insufficient.json")));
    }

    @Test
    void 订单事件的契约_库存服务能解析消费方发来的事件() throws Exception {
        mvc.perform(post("/internal/events/order")
                        .header("Authorization", "Bearer " + TestJwts.service("order-service"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(read("order-event-cancelled.json")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processed").value(true));
    }

    @Test
    void 内部接口只认服务令牌() throws Exception {
        mvc.perform(get("/internal/inventory/stocks").param("skuIds", "1")).andExpect(status().isUnauthorized());
        // 用户令牌（哪怕是管理员）也不能直接调内部接口
        mvc.perform(get("/internal/inventory/stocks")
                        .param("skuIds", "1")
                        .header("Authorization", "Bearer " + TestJwts.admin()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/inventory/stocks")
                        .param("skuIds", "1")
                        .header("Authorization", "Bearer " + TestJwts.service("product-service")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].skuId").value(1));
    }

    private static String read(String name) throws Exception {
        return Files.readString(CONTRACTS.resolve(name));
    }
}

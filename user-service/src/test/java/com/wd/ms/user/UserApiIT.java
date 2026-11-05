package com.wd.ms.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wd.ms.common.test.TestJwts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 用户接口：权限码和业务规则与前端 Mock 一致；种子数据和 Mock 的 createUsers 逐条一致；响应字段和前端契约一致。
 * token 用 TestJwts 现场签发（真实签名），从请求头到 @PreAuthorize 的整条链路都被测到。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwts.Config.class)
class UserApiIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Test
    void 种子数据和前端Mock一致() throws Exception {
        mvc.perform(get("/api/users/4").header("Authorization", bearer(TestJwts.admin())))
                .andExpect(jsonPath("$.data.nickname").value("陈勇"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-01-07T01:00:00Z"));
        mvc.perform(get("/api/users/7").header("Authorization", bearer(TestJwts.admin())))
                .andExpect(jsonPath("$.data.nickname").value("周娜洋"))
                .andExpect(jsonPath("$.data.role").value("editor"))
                .andExpect(jsonPath("$.data.status").value("disabled"));
        mvc.perform(get("/api/users").param("status", "disabled").header("Authorization", bearer(TestJwts.admin())))
                .andExpect(jsonPath("$.data.total").value(10));
    }

    @Test
    void 搜索和分页() throws Exception {
        mvc.perform(get("/api/users")
                        .param("keyword", "USER05")
                        .param("page", "2")
                        .param("pageSize", "4")
                        .header("Authorization", bearer(TestJwts.editor())))
                .andExpect(jsonPath("$.data.total").value(10))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.list.length()").value(4))
                .andExpect(jsonPath("$.data.list[0].username").value("user054"));
        // LIKE 通配符要按字面匹配：搜 "%" 不应该匹配到任何人
        mvc.perform(get("/api/users").param("keyword", "%").header("Authorization", bearer(TestJwts.editor())))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void 响应字段与前端契约一致() throws Exception {
        String body = mvc.perform(
                        get("/api/users").param("pageSize", "1").header("Authorization", bearer(TestJwts.admin())))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode actual = json.readTree(body);
        JsonNode contract = json.readTree(Files.readString(Path.of("../contracts/frontend/user-page-response.json")));
        assertThat(fieldNames(actual)).isEqualTo(fieldNames(contract));
        assertThat(fieldNames(actual.get("data"))).isEqualTo(fieldNames(contract.get("data")));
        assertThat(fieldNames(actual.get("data").get("list").get(0)))
                .isEqualTo(fieldNames(contract.get("data").get("list").get(0)));
    }

    @Test
    void 权限_访客403_运营不能删除() throws Exception {
        mvc.perform(get("/api/users").header("Authorization", bearer(TestJwts.guest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        mvc.perform(delete("/api/users/10").header("Authorization", bearer(TestJwts.editor())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void 业务规则_不能禁用或删除自己() throws Exception {
        mvc.perform(put("/api/users/2/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"disabled\"}")
                        .header("Authorization", bearer(TestJwts.editor())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10002))
                .andExpect(jsonPath("$.message").value("不能禁用当前登录的账号"));
        mvc.perform(delete("/api/users/1").header("Authorization", bearer(TestJwts.admin())))
                .andExpect(jsonPath("$.code").value(10002));
    }

    @Test
    void 启用禁用与删除() throws Exception {
        mvc.perform(put("/api/users/5/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"disabled\"}")
                        .header("Authorization", bearer(TestJwts.editor())))
                .andExpect(jsonPath("$.data.status").value("disabled"));
        mvc.perform(delete("/api/users/6").header("Authorization", bearer(TestJwts.admin())))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/api/users/6").header("Authorization", bearer(TestJwts.admin())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("用户 6 不存在"));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.propertyNames().forEach(names::add);
        return names;
    }
}

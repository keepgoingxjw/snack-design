package com.shiyoushu.snack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:snacktest;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("local")
@AutoConfigureMockMvc
class ApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    private String login() throws Exception {
        String response = mvc.perform(post("/api/auth/dev").contentType(APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("deviceId", UUID.randomUUID().toString()))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(response).get("token").asText();
    }
    @Test void healthAndAuthentication() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(get("/api/records")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/records").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
    }
    @Test void jsonResponsesDeclareUtf8ForMobileBrowsers() throws Exception {
        var health=mvc.perform(get("/api/health")).andExpect(status().isOk()).andReturn().getResponse();
        assertTrue(health.getContentType().toLowerCase(java.util.Locale.ROOT).contains("charset=utf-8"),health.getContentType());
        String auth=login();
        var saved=mvc.perform(post("/api/records").header("Authorization",auth).contentType(APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("raw","苯甲酸钠、柠檬黄","kind","手动输入"))))
            .andExpect(status().isOk()).andReturn().getResponse();
        assertEquals("UTF-8",saved.getCharacterEncoding());
        assertTrue(saved.getContentAsString().contains("苯甲酸钠、柠檬黄"));
        var error=mvc.perform(get("/api/records")).andExpect(status().isUnauthorized()).andReturn().getResponse();
        assertTrue(error.getContentType().toLowerCase(java.util.Locale.ROOT).contains("charset=utf-8"));
        assertTrue(error.getContentAsString().contains("登录"));
    }
    @Test void recordsArePersistedUpdatedAndIsolated() throws Exception {
        String alice = login(), bob = login();
        String response = mvc.perform(post("/api/records").header("Authorization", alice).contentType(APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("raw", "乳粉、小麦粉", "kind", "虚构示例"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = json.readTree(response).get("id").asText();
        mvc.perform(get("/api/records/" + id).header("Authorization", bob)).andExpect(status().isNotFound());
        mvc.perform(put("/api/records/" + id).header("Authorization", bob).contentType(APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("raw", "糖", "kind", "手动输入")))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/records/" + id).header("Authorization", bob)).andExpect(status().isOk());
        mvc.perform(put("/api/records/" + id).header("Authorization", alice).contentType(APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("raw", "乳粉", "kind", "手动输入")))).andExpect(status().isOk())
            .andExpect(jsonPath("$.kind").value("虚构示例"));
        mvc.perform(get("/api/records").header("Authorization", alice)).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].raw").value("乳粉"));
        mvc.perform(delete("/api/records").header("Authorization", bob));
        mvc.perform(get("/api/records/" + id).header("Authorization", alice)).andExpect(status().isOk());
        mvc.perform(delete("/api/records/" + id).header("Authorization", alice));
        mvc.perform(get("/api/records/" + id).header("Authorization", alice)).andExpect(status().isNotFound());
    }
    @Test void legacyExampleAnalysisIsDisabled() throws Exception {
        mvc.perform(post("/api/ingredients/analyze").header("Authorization", login()).contentType(APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("raw", "乳粉、巧克力（糖、可可脂）"))))
            .andExpect(status().isGone());
    }
    @Test void invalidInputsAreRejected() throws Exception {
        String token = login();
        for (String raw : new String[]{"", "乳粉（糖)", "乳粉）", "糖".repeat(5001), String.join("、", java.util.Collections.nCopies(31, "糖"))}) {
            mvc.perform(post("/api/records").header("Authorization", token).contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("raw", raw, "kind", "手动输入")))).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/auth/wechat").contentType(APPLICATION_JSON).content("{\"code\":\"fake\"}"))
            .andExpect(status().isServiceUnavailable());
        assertEquals(2, IngredientService.split("配料表：巧克力（糖、可可(可可粉,可可脂)）、乳粉").size());
    }
}

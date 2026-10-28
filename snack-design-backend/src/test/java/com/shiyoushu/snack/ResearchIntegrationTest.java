package com.shiyoushu.snack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static com.shiyoushu.snack.ResearchModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:researchtest;DB_CLOSE_DELAY=-1")
@ActiveProfiles("local")
@AutoConfigureMockMvc
class ResearchIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ResearchService research;
    @Autowired SessionService sessions;
    @MockBean ResearchGateway gateway;
    @BeforeEach void setup() { reset(gateway); when(gateway.model()).thenReturn("test-model"); }
    private String token() throws Exception {
        String body = mvc.perform(post("/api/auth/dev").contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("deviceId", UUID.randomUUID().toString()))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).path("token").asText();
    }
    private String record(String auth) throws Exception {
        String body = mvc.perform(post("/api/records").header("Authorization",auth).contentType(APPLICATION_JSON)
            .content("{\"raw\":\"苯甲酸钠\",\"kind\":\"手动输入\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("id").asText();
    }
    @Test void missingCredentialsDoesNotFakeSuccess() throws Exception {
        String auth = token(), record = record(auth);
        mvc.perform(post("/api/records/" + record + "/research").header("Authorization",auth).contentType(APPLICATION_JSON).content("{\"foodCategory\":\"\",\"refresh\":true}"))
            .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/records/" + record + "/research/latest").header("Authorization",auth)).andExpect(jsonPath("$.exists").value(false));
        verify(gateway, never()).research(anyList(), anyString());
    }
    @Test void providerFailureIsSavedWithoutInventedFindings() throws Exception {
        when(gateway.configured()).thenReturn(true);
        when(gateway.research(anyList(), anyString())).thenThrow(new IllegalStateException("AI服务额度不足"));
        String auth=token(), record=record(auth);
        var report=research.create(sessions.user(auth),record,new Query("",true));
        for(int i=0;i<100;i++) { report=research.find(sessions.user(auth),report.id()); if("failed".equals(report.status())) break; Thread.sleep(20); }
        assertEquals("failed",report.status()); assertTrue(report.findings().isEmpty()); assertEquals("AI服务额度不足",report.error());
    }
    @Test void querySnapshotsPersistArePrivateAndDoNotOverrideEditedRaw() throws Exception {
        when(gateway.configured()).thenReturn(true);
        when(gateway.research(anyList(), anyString())).thenAnswer(call -> new AiResult(List.of(new Finding("苯甲酸钠", "测试用途", "仅供自动化测试", "测试益处", "测试风险", null, List.of(), "测试")), "test-model"));
        String auth=token(), other=token(), record=record(auth);
        String body = mvc.perform(post("/api/records/"+record+"/research").header("Authorization",auth).contentType(APPLICATION_JSON)
            .content("{\"foodCategory\":\"饮料\",\"refresh\":true}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id=json.readTree(body).path("id").asText();
        Report report=null;
        for(int i=0;i<100;i++) { report=research.find(sessions.user(auth),id); if(!List.of("queued","running").contains(report.status())) break; Thread.sleep(20); }
        assertNotNull(report); assertEquals("done",report.status()); assertEquals("苯甲酸钠", report.findings().getFirst().name());
        mvc.perform(get("/api/research/"+id).header("Authorization",other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/records/"+record+"/research/latest").header("Authorization",auth)).andExpect(jsonPath("$.report.id").value(id))
            .andExpect(jsonPath("$.report.recommendation.action").value("check_first"));
        mvc.perform(put("/api/records/"+record).header("Authorization",auth).contentType(APPLICATION_JSON).content("{\"raw\":\"新配料\",\"kind\":\"手动输入\"}"));
        mvc.perform(get("/api/records/"+record+"/research/latest").header("Authorization",auth)).andExpect(jsonPath("$.exists").value(false));
        mvc.perform(get("/api/records/"+record+"/research/history").header("Authorization",auth)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(delete("/api/records/"+record).header("Authorization",auth));
        mvc.perform(get("/api/research/"+id).header("Authorization",auth)).andExpect(status().isNotFound());
    }
}

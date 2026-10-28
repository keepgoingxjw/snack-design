package com.shiyoushu.snack;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:prodtest;DB_CLOSE_DELAY=-1")
@ActiveProfiles("prod")
@AutoConfigureMockMvc
class ProductionProfileTest {
    @Autowired MockMvc mvc;
    @Test void developmentLoginIsUnavailable() throws Exception {
        mvc.perform(post("/api/auth/dev").contentType(APPLICATION_JSON)
            .content("{\"deviceId\":\"development-only-123\"}")).andExpect(status().isNotFound());
    }
}

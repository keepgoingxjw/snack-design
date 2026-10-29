package com.shiyoushu.snack;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:ocrtest;DB_CLOSE_DELAY=-1")
@ActiveProfiles("local")
@AutoConfigureMockMvc
class OcrIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SessionService sessions;
    @Autowired JdbcTemplate db;
    @MockBean OcrService ocr;
    @BeforeEach void setup() { reset(ocr); }
    @Test void sessionIsRequiredBeforeRecognition() throws Exception {
        mvc.perform(multipart("/api/ocr/recognize").file(new MockMultipartFile("image","image.png","image/png",OcrServiceTest.image())))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(ocr);
    }
    @Test void authenticatedUploadOnlyReturnsTextAndDoesNotCreateRecords() throws Exception {
        String token=sessions.login("ocr-test-user").token();
        when(ocr.recognize(any())).thenReturn("配料：燕麦片、小麦粉");
        long before=db.queryForObject("SELECT COUNT(*) FROM ingredient_records",Long.class);
        mvc.perform(multipart("/api/ocr/tesseract").file(new MockMultipartFile("image","image.png","image/png",OcrServiceTest.image()))
            .header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.text").value("配料：燕麦片、小麦粉"));
        assertEquals(before,db.queryForObject("SELECT COUNT(*) FROM ingredient_records",Long.class));
        verify(ocr,times(1)).recognize(any());
    }
    @Test void missingImageIsAReadableBadRequest() throws Exception {
        String token=sessions.login("ocr-missing-image").token();
        mvc.perform(multipart("/api/ocr/recognize").header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
        verifyNoInteractions(ocr);
    }
}

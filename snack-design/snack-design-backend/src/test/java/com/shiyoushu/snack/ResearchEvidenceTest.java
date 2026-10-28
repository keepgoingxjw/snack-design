package com.shiyoushu.snack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResearchEvidenceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final EvidenceVerifier verifier = mock(EvidenceVerifier.class);
    private final OpenAiResearchGateway gateway = new OpenAiResearchGateway(json, verifier, "test-not-a-real-key", "test-model");
    private static final String URL = "https://www.nhc.gov.cn/test-only.html";
    private Map<String, Object> item() {
        var item = new LinkedHashMap<String, Object>();
        item.put("name", "测试配料"); item.put("role", "测试用途"); item.put("summary", "仅测试数据");
        item.put("benefit", "测试说明"); item.put("risk", "不能判断本品风险"); item.put("dosage", null);
        item.put("sources", List.of(Map.of("title", "测试来源", "url", URL, "quote", "这是仅用于自动化测试的原文摘录")));
        return item;
    }
    private com.fasterxml.jackson.databind.JsonNode response(Map<String, Object> finding, String retrieved) throws Exception {
        return json.valueToTree(Map.of("status", "completed", "model", "test-model", "output", List.of(
            Map.of("type", "web_search_call", "status", "completed", "action", Map.of("sources", List.of(Map.of("url", retrieved)))),
            Map.of("type", "message", "content", List.of(Map.of("type", "output_text", "text", json.writeValueAsString(Map.of("findings", List.of(finding)))))))));
    }
    @Test void whitespaceQuoteCannotPassEvidenceVerification() {
        assertFalse(new EvidenceVerifier().verify(URL, "                         "));
    }
    @Test void acceptsOnlyUrlsActuallyRetrievedAndAllowed() throws Exception {
        var result = gateway.parse(response(item(), URL), List.of("测试配料"), "");
        assertEquals(1, result.findings().getFirst().sources().size());
        assertFalse(result.findings().getFirst().sources().getFirst().textVerified());
        var unsupported = gateway.parse(response(item(), "https://www.fda.gov/different-page"), List.of("测试配料"), "");
        assertTrue(unsupported.findings().getFirst().sources().isEmpty());
        assertEquals("资料不足", unsupported.findings().getFirst().role());
    }
    @Test void refusesNonSearchAndChangedIngredientNames() throws Exception {
        assertThrows(IllegalStateException.class, () -> gateway.parse(json.readTree("{\"status\":\"completed\",\"output\":[]}"), List.of("测试配料"), ""));
        assertThrows(IllegalStateException.class, () -> gateway.parse(response(item(), URL), List.of("另一个配料"), ""));
    }
    @Test void dosageRequiresCategoryChineseSourceAndExactVerifiedQuote() throws Exception {
        String quote = "测试食品的测试上限为1 mg/kg，仅作测试不能作为真实用量";
        var item = item();
        item.put("sources", List.of(Map.of("title", "测试来源", "url", URL, "quote", quote)));
        item.put("dosage", Map.of("value", "1", "unit", "mg/kg", "foodCategory", "测试食品", "jurisdiction", "中国大陆",
            "standard", "测试标准-非真实标准", "sourceUrl", URL, "quote", quote, "note", "测试"));
        assertNull(gateway.parse(response(item, URL), List.of("测试配料"), "测试食品").findings().getFirst().dosage());
        when(verifier.verify(URL, quote)).thenReturn(true);
        assertNull(gateway.parse(response(item, URL), List.of("测试配料"), "").findings().getFirst().dosage());
        assertNotNull(gateway.parse(response(item, URL), List.of("测试配料"), "测试食品").findings().getFirst().dosage());
    }
    @Test void blocksUntrustedUrlsAndDoesNotExposeUngroundedNumericDose() throws Exception {
        for (var url : List.of("http://www.nhc.gov.cn/x", "https://www.nhc.gov.cn.evil.com/x", "https://127.0.0.1/x", "https://www.nhc.gov.cn:8080/x", "https://user@www.nhc.gov.cn/x")) assertFalse(EvidenceVerifier.allowed(url));
        assertTrue(EvidenceVerifier.allowed(URL));
        var item = item(); item.put("benefit", "每天1 mg/kg会更健康");
        assertTrue(gateway.parse(response(item, URL), List.of("测试配料"), "").findings().getFirst().benefit().contains("已隐藏"));
    }
}

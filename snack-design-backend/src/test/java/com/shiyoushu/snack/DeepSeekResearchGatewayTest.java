package com.shiyoushu.snack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeepSeekResearchGatewayTest {
    private final ObjectMapper json = new ObjectMapper();
    private static final String URL = "https://www.nhc.gov.cn/test-deepseek.html";
    private static final String QUOTE = "This is a test quotation about a food ingredient, not real food guidance.";
    @SuppressWarnings("unchecked")
    private HttpResponse<String> reply(int status, Object body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(json.writeValueAsString(body));
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (a,b) -> true));
        return response;
    }
    private Object answer(String name, String quote) throws Exception {
        var finding = Map.of("name", name, "group", "preservation", "role", "测试用途", "summary", "测试总结", "benefit", "依据测试来源的说明", "risk", "不能推断实际风险",
            "sources", List.of(Map.of("url", URL, "title", "测试出处", "quote", quote)));
        return Map.of("model", "deepseek-flash", "choices", List.of(Map.of("finish_reason", "stop", "message", Map.of("content", json.writeValueAsString(Map.of("findings", List.of(finding)))))));
    }
    private DeepSeekResearchGateway gateway(HttpClient http, String key) {
        return new DeepSeekResearchGateway(json, new ResearchResultValidator(mock(EvidenceVerifier.class)), key, "deepseek-flash", 8, 4, http);
    }
    @Test void nativeSearchThenChatOnlyUsesDeepSeekAndChecksRetrievedBody() throws Exception {
        var http = mock(HttpClient.class);
        var searched = reply(200, Map.of("results", List.of(Map.of("url", URL, "title", "测试出处", "content", QUOTE))));
        var summarized = reply(200, answer("丙酸钙", QUOTE));
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(searched, summarized);
        var result = gateway(http, "fake-test-key").research(List.of("丙酸钙"), "");
        assertEquals("测试用途", result.findings().getFirst().role());
        assertTrue(result.findings().getFirst().sources().getFirst().textVerified());
        assertTrue(result.model().startsWith("DeepSeek/"));
        var requests = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(requests.capture(), any());
        assertEquals(List.of("/v1/tools/search", "/chat/completions"), requests.getAllValues().stream().map(r -> r.uri().getPath()).toList());
        assertTrue(requests.getAllValues().stream().allMatch(r -> r.uri().getHost().equals("api.deepseek.com")));
    }
    @Test void missingBodyUsesNativeFetchAndFakeQuoteDoesNotVerify() throws Exception {
        var http = mock(HttpClient.class);
        var searched = reply(200, Map.of("results", List.of(Map.of("url", URL, "title", "测试出处", "content", ""))));
        var fetched = reply(200, Map.of("content", QUOTE));
        var summarized = reply(200, answer("测试配料", "This quotation was never returned by the search or fetch API."));
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(searched, fetched, summarized);
        var result = gateway(http, "fake-test-key").research(List.of("测试配料"), "");
        assertTrue(result.findings().getFirst().sources().isEmpty());
        assertEquals("pending", result.findings().getFirst().group());
        var requests = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(3)).send(requests.capture(), any());
        assertEquals("/v1/tools/fetch", requests.getAllValues().get(1).uri().getPath());
    }
    @Test void missingKeyAndOverBudgetStopBeforePaidRequests() {
        var http = mock(HttpClient.class);
        assertThrows(IllegalStateException.class, () -> gateway(http, "").research(List.of("测试"), ""));
        assertThrows(IllegalStateException.class, () -> gateway(http, "test").research(Collections.nCopies(9, "测试"), ""));
        verifyNoInteractions(http);
    }
    @Test void noSourcesBalanceFailureAndTruncatedJsonNeverBecomeSuccess() throws Exception {
        var http = mock(HttpClient.class);
        var empty = reply(200, Map.of("results", List.of()));
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(empty);
        assertThrows(IllegalStateException.class, () -> gateway(http,"test").research(List.of("测试"),""));
        verify(http, times(1)).send(any(), any());
        reset(http);
        var insufficient = reply(402, Map.of("error", Map.of("message", "secret-must-not-be-shown")));
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(insufficient);
        var error = assertThrows(IllegalStateException.class, () -> gateway(http,"test").research(List.of("测试"),""));
        assertTrue(error.getMessage().contains("余额"));
        assertFalse(error.getMessage().contains("secret-must-not-be-shown"));
        assertThrows(IllegalStateException.class, () -> gateway(http,"test").contentJson(json.readTree("{\"choices\":[{\"finish_reason\":\"length\"}]}")));
    }
    @Test void deepSeekReadinessDoesNotRequireLocalSearchOrCallPaidEndpoints() {
        var sessions = mock(SessionService.class);
        var configured = mock(ConfiguredResearchGateway.class);
        var localSearch = mock(PublicEvidenceSearch.class);
        when(sessions.user("Bearer test")).thenReturn("user");
        when(configured.configured()).thenReturn(true);
        when(configured.provider()).thenReturn("deepseek");
        when(configured.model()).thenReturn("DeepSeek/deepseek-flash · 联网搜索");
        var controller = new ResearchReadinessController(sessions, configured, localSearch);
        assertEquals(true, controller.readiness("Bearer test").get("ready"));
        verifyNoInteractions(localSearch);
        verify(configured, never()).research(anyList(), anyString());
    }
    @Test void requestsConciseEvidenceBasedBulletSummariesWithoutHardcodedIngredientAnswers() throws Exception {
        var gateway = gateway(mock(HttpClient.class), "test");
        var body = json.valueToTree(gateway.summaryPayload(List.of("测试配料"), "", List.of()));
        String prompt = body.path("messages").get(0).path("content").asText();
        assertTrue(prompt.contains("• 关键词："));
        assertTrue(prompt.contains("不凑数"));
        assertTrue(prompt.contains("加工用途不等于健康收益"));
        assertTrue(prompt.contains("法规限制不是人体危害"));
        assertFalse(prompt.contains("柠檬黄"));
        assertEquals("deepseek-flash", body.path("model").asText());
        assertTrue(gateway.query("测试配料", "").contains("site:efsa.europa.eu"));
        assertTrue(gateway.model().contains("要点总结v2"));
    }
    @Test void queryIsWithinOfficialUnicodeLimitAndPayloadHasNoOtherProviders() throws Exception {
        var gateway = gateway(mock(HttpClient.class), "test");
        String query = gateway.query("很长的复合配料".repeat(100), "食品类别".repeat(100));
        assertTrue(query.codePointCount(0, query.length()) <= 128);
        assertTrue(query.contains("site:nhc.gov.cn"));
        var body = gateway.summaryPayload(List.of("测试"), "", List.of());
        assertEquals("deepseek-flash", body.get("model"));
        for (String key : List.of("plugins", "provider", "models", "web_search_options")) assertFalse(body.containsKey(key));
    }
}

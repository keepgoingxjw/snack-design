package com.shiyoushu.snack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.net.http.*;
import java.util.*;
import static com.shiyoushu.snack.ResearchModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CloudResearchGatewayTest {
    final ObjectMapper json=new ObjectMapper();
    @SuppressWarnings("unchecked") HttpResponse<String> reply(int status,String body) {
        HttpResponse<String> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status); when(response.body()).thenReturn(body);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(),(key,value)->true)); return response;
    }
    String answer(Object value)throws Exception {
        return json.writeValueAsString(Map.of("model","test/model:free","usage",Map.of("cost",0),"choices",List.of(Map.of("finish_reason","stop","message",Map.of("content",json.writeValueAsString(value))))));
    }
    HttpResponse<String> catalog() { return reply(200,"{\"data\":[{\"id\":\"openrouter/free\",\"pricing\":{\"prompt\":\"0\",\"completion\":\"0\"}}]}"); }
    OpenRouterFreeGateway gateway(HttpClient http,PublicLiteratureGateway literature,String key) {
        return new OpenRouterFreeGateway(json,mock(PublicEvidenceSearch.class),new ResearchResultValidator(mock(EvidenceVerifier.class)),literature,key,"openrouter/free",http);
    }
    @Test void calciumPropionateOutsideOldDictionaryUsesAiTermsThenRealRetrievalThenChineseAnswer()throws Exception {
        var http=mock(HttpClient.class); var literature=mock(PublicLiteratureGateway.class);
        String url="https://europepmc.org/article/MED/123",quote="Calcium propionate is used as a food preservative.";
        var planned=reply(200,answer(Map.of("terms",List.of(Map.of("name","丙酸钙","english","calcium propionate")))));
        var finding=new LinkedHashMap<String,Object>();
        finding.put("name","丙酸钙");finding.put("group","preservation");finding.put("role","防腐用途");finding.put("summary","依据检索摘要的中文说明");
        finding.put("benefit","用于食品保存，不能推断健康收益");finding.put("risk","具体食品适用性待核对");
        finding.put("sources",List.of(Map.of("title","Retrieved study","url",url,"quote",quote)));
        var summarized=reply(200,answer(Map.of("findings",List.of(finding))));
        var catalog=catalog();
        when(http.send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(catalog,planned,catalog,summarized);
        when(literature.researchWithTerms(List.of("丙酸钙"),List.of("calcium propionate"))).thenReturn(new AiResult(List.of(
            new Finding("丙酸钙","","","","",null,List.of(new Source("Retrieved study",url,quote,false)),"")),PublicLiteratureGateway.MODEL));
        var result=gateway(http,literature,"fake-test-key").research(List.of("丙酸钙"),"");
        assertEquals("防腐用途",result.findings().getFirst().role());
        assertTrue(result.findings().getFirst().sources().getFirst().textVerified());
        assertNull(result.findings().getFirst().dosage());
        assertEquals("check_first",result.findings().getFirst().advice().action());
        verify(literature).researchWithTerms(List.of("丙酸钙"),List.of("calcium propionate"));
        var requests=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http,times(4)).send(requests.capture(),any());
        assertTrue(requests.getAllValues().stream().allMatch(request->request.uri().getHost().equals("openrouter.ai")));
    }
    @Test void noKeyOrQuotaNeverRunsRetrievalOrPaidFallback()throws Exception {
        var http=mock(HttpClient.class);var literature=mock(PublicLiteratureGateway.class);
        assertThrows(IllegalStateException.class,()->gateway(http,literature,"").research(List.of("丙酸钙"),""));
        verifyNoInteractions(http,literature);
        var catalog=catalog();var limited=reply(429,"{}");
        when(http.send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(catalog,limited);
        assertThrows(IllegalStateException.class,()->gateway(http,literature,"fake-test-key").research(List.of("丙酸钙"),""));
        verify(http,times(2)).send(any(),any());verifyNoInteractions(literature);
    }
    @Test void fabricatedCitationsCannotBecomeVerifiedFindings() {
        var validator=new ResearchResultValidator(mock(EvidenceVerifier.class));
        var item=Map.of("name","丙酸钙","role","模型编造的用途","sources",List.of(Map.of("url","https://europepmc.org/article/MED/123","quote","A fabricated quotation that was never retrieved")));
        var result=validator.validateLiterature(json.valueToTree(List.of(item)),List.of("丙酸钙"),Map.of("https://europepmc.org/article/MED/123","Actual retrieved text about food."),"test");
        assertTrue(result.findings().getFirst().sources().isEmpty());
        assertEquals("资料不足",result.findings().getFirst().role());
    }
    @Test void acceptsJsonCodeFenceButRejectsIncompleteOrNarrativeOutput() {
        var gateway=gateway(mock(HttpClient.class),mock(PublicLiteratureGateway.class),"fake-test-key");
        var response=json.valueToTree(Map.of("choices",List.of(Map.of("message",Map.of("content","```json\n{\"terms\":[]}\n```")))));
        assertTrue(gateway.contentJson(response).path("terms").isArray());
        var broken=json.valueToTree(Map.of("choices",List.of(Map.of("message",Map.of("content","{\"terms\":[")))));
        assertThrows(IllegalStateException.class,()->gateway.contentJson(broken));
    }
    @Test void diagnosticSeparatesPolicyAndRateLimitsWithoutLeakingResponseOrKey() {
        var gateway=gateway(mock(HttpClient.class),mock(PublicLiteratureGateway.class),"secret-test-key");
        String privacy=gateway.failureMessage(reply(403,"{\"error\":{\"code\":403,\"message\":\"Privacy policy denied secret-test-key\"}}"));
        assertTrue(privacy.contains("隐私设置")); assertTrue(privacy.contains("HTTP 403")); assertFalse(privacy.contains("secret-test-key"));
        String rate=gateway.failureMessage(reply(429,"<html>secret-test-key</html>"));
        assertTrue(rate.contains("HTTP 429"));assertFalse(rate.contains("secret-test-key"));
    }
}

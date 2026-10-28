package com.shiyoushu.snack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ZeroCostPolicyTest {
    private final ObjectMapper json=new ObjectMapper();
    @Test void onlyFreeRouterOrExplicitFreeCatalogVariantsAreAllowed() {
        ZeroCostPolicy.requireFreeModel("openrouter/free");
        ZeroCostPolicy.requireFreeModel("example/model:free");
        for(String model:List.of("openai/paid-model","example/model:online","example/model:free:online","openrouter/auto"))
            assertThrows(IllegalArgumentException.class,()->ZeroCostPolicy.requireFreeModel(model));
    }
    @Test void requiresCurrentCatalogToShowAllPricesZero()throws Exception {
        ZeroCostPolicy.verifyCatalog(json.readTree("{\"data\":[{\"id\":\"example/model:free\",\"pricing\":{\"prompt\":\"0\",\"completion\":\"0\"}}]}"),"example/model:free");
        for(String price:List.of("0.1","-1","unknown"))
            assertThrows(IllegalStateException.class,()->ZeroCostPolicy.verifyCatalog(json.readTree("{\"data\":[{\"id\":\"example/model:free\",\"pricing\":{\"prompt\":\"0\",\"completion\":\""+price+"\"}}]}"),"example/model:free"));
        assertThrows(IllegalStateException.class,()->ZeroCostPolicy.verifyCatalog(json.readTree("{\"data\":[]}"),"example/model:free"));
    }
    @Test void payloadCannotIncludeSearchPluginsOrPaidFallbacks()throws Exception {
        var gateway=new OpenRouterFreeGateway(json,mock(PublicEvidenceSearch.class),new ResearchResultValidator(mock(EvidenceVerifier.class)),"test-key","openrouter/free");
        var body=gateway.payload(List.of("测试配料"),"",new PublicEvidenceSearch.Context(List.of(),Set.of()));
        for(String field:List.of("plugins","tools","models","web_search_options"))assertFalse(body.containsKey(field));
        assertEquals(ZeroCostPolicy.providerLimits(),body.get("provider"));
        assertEquals("openrouter/free",body.get("model"));
    }
    @Test void cloudResponseMustCompleteAndNotPretendUnverifiedCitationsAreEvidence()throws Exception {
        var gateway=new OpenRouterFreeGateway(json,mock(PublicEvidenceSearch.class),new ResearchResultValidator(mock(EvidenceVerifier.class)),"test-key","openrouter/free");
        assertThrows(IllegalStateException.class,()->gateway.parse(json.readTree("{\"choices\":[{\"finish_reason\":\"length\"}]}"),List.of("测试"),"",Set.of()));
        var finding=java.util.Map.of("name","测试","role","测试","benefit","测试","risk","测试","sources",List.of());
        var response=json.valueToTree(java.util.Map.of("choices",List.of(java.util.Map.of("finish_reason","stop","message",java.util.Map.of("content",json.writeValueAsString(java.util.Map.of("findings",List.of(finding)))))),"usage",java.util.Map.of("cost",0)));
        assertEquals("pending",gateway.parse(response,List.of("测试"),"",Set.of()).findings().getFirst().group());
        ((com.fasterxml.jackson.databind.node.ObjectNode)response.path("usage")).put("cost",0.01);
        assertThrows(IllegalStateException.class,()->gateway.parse(response,List.of("测试"),"",Set.of()));
    }
    @Test void zeroModeBlocksPaidProviderEvenIfAnOldKeyIsConfigured() {
        var local=mock(OllamaResearchGateway.class);var openai=mock(OpenAiResearchGateway.class);var free=mock(OpenRouterFreeGateway.class);
        when(openai.configured()).thenReturn(true);
        var blocked=new ConfiguredResearchGateway(local,openai,free,"openai",true);
        assertFalse(blocked.configured());
        assertThrows(IllegalStateException.class,()->blocked.research(List.of("测试"),""));
        verify(openai,never()).research(anyList(),anyString());
        when(free.configured()).thenReturn(true);
        var allowed=new ConfiguredResearchGateway(local,openai,free,"openrouter",true);
        allowed.research(List.of("测试"),"");
        verify(free).research(List.of("测试"),"");
        verifyNoInteractions(local);
    }
}

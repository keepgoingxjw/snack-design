package com.shiyoushu.snack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.net.http.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicLiteratureGatewayTest {
    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status,String body) {
        HttpResponse<String> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status); when(response.body()).thenReturn(body); return response;
    }
    @Test void realGatewayMapsChineseInputAndReturnsSourcesWithoutAiOrAdvice() throws Exception {
        var http=mock(HttpClient.class);
        var reply=response(200,"{\"resultList\":{\"result\":[{\"id\":\"123\",\"source\":\"MED\",\"title\":\"Test food study\",\"abstractText\":\"<p>Study abstract.</p>\",\"pubYear\":\"2025\"}]}}");
        when(http.send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
            .thenReturn(reply);
        var result=new PublicLiteratureGateway(new ObjectMapper(),http).research(List.of("苯甲酸钠"),"");
        assertEquals(PublicLiteratureGateway.MODEL,result.model());
        var finding=result.findings().getFirst();
        assertEquals("https://europepmc.org/article/MED/123",finding.sources().getFirst().url());
        assertEquals("Study abstract.",finding.sources().getFirst().quote());
        assertNull(finding.advice()); assertNull(finding.dosage()); assertFalse(finding.sources().getFirst().textVerified());
        verify(http).send(argThat((HttpRequest r)->r.uri().getHost().equals("www.ebi.ac.uk") && r.uri().getRawQuery().contains("sodium+benzoate") && r.headers().firstValue("Authorization").isEmpty()),any());
    }
    @Test void quotaAndNetworkFailureStopWithoutRetriesOrOtherProviders() throws Exception {
        for(int status:List.of(429,402,403,500)) {
            var http=mock(HttpClient.class);
            var reply=response(status,"{}");
            when(http.send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(reply);
            assertThrows(IllegalStateException.class,()->new PublicLiteratureGateway(new ObjectMapper(),http).research(List.of("食盐","柠檬酸"),""));
            verify(http,times(1)).send(any(),any());
        }
    }
    @Test void unknownChineseIngredientDoesNotInventTranslationOrSources() {
        var http=mock(HttpClient.class);
        var finding=new PublicLiteratureGateway(new ObjectMapper(),http).research(List.of("未收录的复合配料"),"").findings().getFirst();
        assertTrue(finding.sources().isEmpty()); assertTrue(finding.summary().contains("暂未收录")); verifyNoInteractions(http);
    }
}

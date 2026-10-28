package com.shiyoushu.snack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OllamaGatewayTest {
    private final ObjectMapper json=new ObjectMapper();
    private final EvidenceVerifier evidence=mock(EvidenceVerifier.class);
    private static final String URL="https://www.nhc.gov.cn/test-local-fixture.html";
    private Map<String,Object> finding(){return Map.of("name","测试配料","group","preservation","role","测试角色","summary","仅测试数据","benefit","测试益处","risk","测试注意","sources",List.of(Map.of("url",URL,"title","测试来源","quote","这是仅供自动化测试使用的官方正文摘录")));}
    @Test void usesLocalHttpSearchAndLocalModelWithNoCloudCredentials()throws Exception{
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/tags",exchange->{byte[] b="{\"models\":[{\"name\":\"test:local\"}]}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,b.length);exchange.getResponseBody().write(b);exchange.close();});
        server.createContext("/search",exchange->{byte[] b=json.writeValueAsBytes(Map.of("results",List.of(Map.of("url",URL,"title","测试官方来源","content","测试搜索摘要"))));exchange.sendResponseHeaders(200,b.length);exchange.getResponseBody().write(b);exchange.close();});
        server.createContext("/api/chat",exchange->{
            var payload=json.readTree(exchange.getRequestBody());
            assertEquals("test:local",payload.path("model").asText());
            assertFalse(payload.path("stream").asBoolean());
            assertTrue(payload.path("messages").get(1).path("content").asText().contains(URL));
            byte[] b=json.writeValueAsBytes(Map.of("done",true,"message",Map.of("content",json.writeValueAsString(Map.of("findings",List.of(finding()))))));
            exchange.sendResponseHeaders(200,b.length);exchange.getResponseBody().write(b);exchange.close();
        });
        server.start();
        try{
            when(evidence.readText(URL)).thenReturn("这是仅供自动化测试使用的官方正文摘录");
            when(evidence.verify(eq(URL),anyString())).thenReturn(true);
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            var gateway=new OllamaResearchGateway(json,evidence,new ResearchResultValidator(evidence),base,base,"test:local");
            var result=gateway.research(List.of("测试配料"),"");
            assertEquals("ollama/test:local",result.model());
            assertEquals("preservation",result.findings().getFirst().group());
            assertTrue(result.findings().getFirst().sources().getFirst().textVerified());
        }finally{server.stop(0);}
    }
    @Test void blocksRemoteProxiesAndIncompleteOrUnretrievedAnswers()throws Exception{
        assertThrows(IllegalArgumentException.class,()->new OllamaResearchGateway(json,evidence,new ResearchResultValidator(evidence),"http://example.com","http://127.0.0.1:8888","test"));
        var gateway=new OllamaResearchGateway(json,evidence,new ResearchResultValidator(evidence),"http://127.0.0.1:11434","http://127.0.0.1:8888","test");
        assertThrows(IllegalStateException.class,()->gateway.parse(json.readTree("{\"done\":false}"),List.of("测试配料"),"",Set.of(URL)));
        var response=json.valueToTree(Map.of("done",true,"message",Map.of("content",json.writeValueAsString(Map.of("findings",List.of(finding()))))));
        var result=gateway.parse(response,List.of("测试配料"),"",Set.of());
        assertEquals("pending",result.findings().getFirst().group());
        assertTrue(result.findings().getFirst().sources().isEmpty());
    }
}

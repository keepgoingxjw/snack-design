package com.shiyoushu.snack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import static com.shiyoushu.snack.ResearchModels.*;

/** 本地免费推理 + 自托管开源搜索。不使用公共不明代理、不自动调用付费云API。 */
@Component
public class OllamaResearchGateway {
    private final ObjectMapper json;
    private final EvidenceVerifier evidence;
    private final ResearchResultValidator validator;
    private final String ollama, search, model;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public OllamaResearchGateway(ObjectMapper json,EvidenceVerifier evidence,ResearchResultValidator validator,
            @Value("${app.ai.ollama-url:http://127.0.0.1:11434}") String ollama,
            @Value("${app.ai.search-url:http://127.0.0.1:8888}") String search,
            @Value("${app.ai.local-model:qwen2.5:7b}") String model) {
        this.json=json;this.evidence=evidence;this.validator=validator;this.ollama=localUrl(ollama);this.search=localUrl(search);this.model=model;
    }
    private static String localUrl(String value) {
        URI uri=URI.create(value);
        if(!"http".equals(uri.getScheme()) || !Set.of("localhost","127.0.0.1","[::1]").contains(String.valueOf(uri.getHost())) || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null)
            throw new IllegalArgumentException("免费AI与搜索地址只允许本机HTTP服务，不向不明远程代理发送数据");
        return value.replaceAll("/+$", "");
    }
    public String model(){return "ollama/"+model;}
    public AiResult research(List<String> names,String category) {
        try {
            JsonNode tags=get(ollama+"/api/tags",8,"未连接本机免费AI。请按 FREE_AI.md 安装并启动 Ollama，下载 "+model);
            boolean found=false;
            for(var tag:tags.path("models"))if(model.equals(tag.path("name").asText())||model.equals(tag.path("model").asText()))found=true;
            if(!found)throw new IllegalStateException("本机尚未下载模型。请运行 ollama pull "+model+"，或使用免费AI启动脚本");
            var retrieved=new HashSet<String>();
            var context=new ArrayList<Map<String,Object>>();
            int bodyBudget=12;
            for(String name:names) {
                String domains=String.join(" OR ",EvidenceVerifier.DOMAINS.stream().map(d->"site:"+d).toList());
                String query="("+domains+") \""+name+"\" 食品 用途 安全 使用标准 "+category;
                JsonNode response=get(search+"/search?format=json&language=zh-CN&q="+URLEncoder.encode(query,StandardCharsets.UTF_8),25,
                    "未连接本机免费搜索服务，或搜索引擎受限。请启动 SearXNG 并启用 JSON 搜索；不会改用付费服务");
                var docs=new ArrayList<Map<String,String>>();
                for(var result:response.path("results")) {
                    String url=result.path("url").asText();
                    if(!EvidenceVerifier.allowed(url))continue;
                    retrieved.add(url);
                    String text=bodyBudget-- > 0?evidence.readText(url):"";
                    docs.add(Map.of("title",result.path("title").asText(),"url",url,"searchSnippet",clip(result.path("content").asText(),1000),
                        "officialBody",clip(text,5000),"bodyState",text.isBlank()?"正文未核实，搜索摘要不可当作逐字原文":"已读取部分HTML正文"));
                    if(docs.size()==2)break;
                }
                context.add(Map.of("name",name,"documents",docs));
            }
            if(retrieved.isEmpty())throw new IllegalStateException("本次搜索未找到允许的官方来源，可能是搜索引擎不可用或资料不足；不使用模型记忆伪造结果");
            try(var schemaInput=new ClassPathResource("research-schema.json").getInputStream()) {
                var messages=List.of(Map.of("role","system","content",ResearchPrompt.RULES+"只输出符合指定结构的JSON，不能将searchSnippet当作原文quote；没有officialBody的来源不要伪造摘录。"),
                    Map.of("role","user","content",json.writeValueAsString(Map.of("names",names,"foodCategory",category,"retrievedDocuments",context))));
                var payload=Map.of("model",model,"stream",false,"format",json.readTree(schemaInput),"messages",messages,
                    "options",Map.of("temperature",0,"num_ctx",16384,"num_predict",12000));
                var response=http.send(HttpRequest.newBuilder(URI.create(ollama+"/api/chat")).timeout(Duration.ofMinutes(8))
                    .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build(),HttpResponse.BodyHandlers.ofString());
                if(response.statusCode()!=200)throw new IllegalStateException("本地AI生成失败，请检查模型是否下载完成及内存是否足够");
                return parse(json.readTree(response.body()),names,category,retrieved);
            }
        }catch(IllegalStateException e){throw e;}catch(Exception e){throw new IllegalStateException("免费AI或搜索服务连接失败/超时，请检查 Ollama 和 SearXNG 是否已启动；不会返回伪造结果");}
    }
    AiResult parse(JsonNode response,List<String> names,String category,Set<String> retrieved)throws Exception {
        if(!response.path("done").asBoolean(false))throw new IllegalStateException("本地AI尚未生成完整结果");
        if("length".equals(response.path("done_reason").asText()))throw new IllegalStateException("本地模型输出被截断，请减少配料数量后重试");
        JsonNode findings=json.readTree(response.path("message").path("content").asText()).path("findings");
        return validator.validate(findings,names,category,retrieved,model());
    }
    private JsonNode get(String url,int seconds,String failure) throws Exception {
        try {
            var response=http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(seconds)).GET().build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)throw new IllegalStateException(failure);
            return json.readTree(response.body());
        }catch(Exception e){throw new IllegalStateException(failure);}
    }
    private static String clip(String value,int max){return value.substring(0,Math.min(value.length(),max));}
}

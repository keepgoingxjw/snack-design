package com.shiyoushu.snack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import static com.shiyoushu.snack.ResearchModels.*;

/** 可选付费云服务适配器，不会由本地免费模式自动回退调用。 */
@Component
public class OpenAiResearchGateway {
    private final ObjectMapper json;
    private final ResearchResultValidator validator;
    private final String key, model;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    public OpenAiResearchGateway(ObjectMapper json,EvidenceVerifier evidence,
            @Value("${app.ai.key:}") String key,@Value("${app.ai.model:gpt-4.1}") String model) {
        this.json=json;this.validator=new ResearchResultValidator(evidence);this.key=key;this.model=model;
    }
    public boolean configured(){return !key.isBlank();}
    public String model(){return model;}
    public AiResult research(List<String> names,String category){
        if(!configured())throw new IllegalStateException("后端未配置 OPENAI_API_KEY，无法进行真实联网查询");
        try(var schema=new ClassPathResource("research-schema.json").getInputStream()){
            var body=Map.of("model",model,"store",false,"instructions",ResearchPrompt.RULES+"必须先调用web_search检索官方允许域名。",
                "input",json.writeValueAsString(Map.of("names",names,"foodCategory",category)),
                "tools",List.of(Map.of("type","web_search","filters",Map.of("allowed_domains",EvidenceVerifier.DOMAINS))),
                "tool_choice","required","include",List.of("web_search_call.action.sources"),"max_output_tokens",18000,
                "text",Map.of("format",Map.of("type","json_schema","name","ingredient_research","strict",true,"schema",json.readTree(schema))));
            var response=http.send(HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
                .timeout(Duration.ofSeconds(180)).header("Authorization","Bearer "+key).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==401)throw new IllegalStateException("AI服务密钥无效，请检查后端配置");
            if(response.statusCode()==429)throw new IllegalStateException("AI服务额度或频率受限，请稍后重试");
            if(response.statusCode()!=200)throw new IllegalStateException("AI联网服务失败，请检查模型是否支持Responses、web_search和结构化输出");
            return parse(json.readTree(response.body()),names,category);
        }catch(IllegalStateException e){throw e;}catch(Exception e){throw new IllegalStateException("AI检索超时或结果无法核实，请重试");}
    }
    AiResult parse(JsonNode response,List<String> names,String category)throws Exception{
        if(!"completed".equals(response.path("status").asText()))throw new IllegalStateException("AI查询未完成，未保存为成功结果");
        Set<String> retrieved=new HashSet<>();StringBuilder text=new StringBuilder();boolean searched=false;
        for(var output:response.path("output")){
            if("web_search_call".equals(output.path("type").asText())&&"completed".equals(output.path("status").asText())){
                searched=true;for(var source:output.path("action").path("sources"))addUrl(retrieved,source.path("url").asText());
            }
            for(var content:output.path("content")){
                if("output_text".equals(content.path("type").asText()))text.append(content.path("text").asText());
                for(var annotation:content.path("annotations"))if("url_citation".equals(annotation.path("type").asText()))addUrl(retrieved,annotation.path("url").asText());
            }
        }
        if(!searched)throw new IllegalStateException("本次AI未实际联网检索，拒绝当作权威资料查询");
        return validator.validate(json.readTree(text.toString()).path("findings"),names,category,retrieved,response.path("model").asText(model));
    }
    private void addUrl(Set<String> values,String url){if(EvidenceVerifier.allowed(url))values.add(url);}
}

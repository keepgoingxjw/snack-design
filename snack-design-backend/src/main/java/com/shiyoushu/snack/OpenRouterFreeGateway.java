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

/** 云端免费模型，费用上限为零；没有联网插件，也没有任何收费模型fallback。 */
@Component
public class OpenRouterFreeGateway {
    private final ObjectMapper json;
    private final PublicEvidenceSearch search;
    private final ResearchResultValidator validator;
    private final String key,model;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    public OpenRouterFreeGateway(ObjectMapper json,PublicEvidenceSearch search,ResearchResultValidator validator,
            @Value("${app.ai.openrouter-key:}") String key,@Value("${app.ai.free-model:openrouter/free}") String model) {
        ZeroCostPolicy.requireFreeModel(model);
        this.json=json;this.search=search;this.validator=validator;this.key=key;this.model=model;
    }
    public boolean configured(){return !key.isBlank();}
    public String model(){return model;}
    public AiResult research(List<String> names,String category) {
        if(!configured())throw new IllegalStateException("请在后端配置免费的OPENROUTER_API_KEY。不需要充值，不使用付费服务");
        try {
            var catalog=http.send(HttpRequest.newBuilder(URI.create("https://openrouter.ai/api/v1/models")).timeout(Duration.ofSeconds(20)).GET().build(),HttpResponse.BodyHandlers.ofString());
            if(catalog.statusCode()!=200)throw new IllegalStateException("无法确认免费模型目录价格，已停止调用");
            ZeroCostPolicy.verifyCatalog(json.readTree(catalog.body()),model);
            var context=search.lookup(names,category);
            var response=http.send(HttpRequest.newBuilder(URI.create("https://openrouter.ai/api/v1/chat/completions"))
                .timeout(Duration.ofMinutes(3)).header("Authorization","Bearer "+key).header("Content-Type","application/json")
                .header("X-OpenRouter-Title","ShiyoushuBite")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload(names,category,context)))).build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==401)throw new IllegalStateException("免费云端API密钥无效，请检查OPENROUTER_API_KEY");
            if(response.statusCode()==402 || response.statusCode()==403 || response.statusCode()==429)
                throw new IllegalStateException("免费额度、隐私路由或访问权限受限，已停止调用。不会充值、切换收费模型或启用付费搜索");
            if(response.statusCode()!=200)throw new IllegalStateException("免费云端模型暂不可用，请稍后重试；不会使用收费替代模型");
            return parse(json.readTree(response.body()),names,category,context.urls());
        }catch(IllegalStateException e){throw e;}catch(Exception e){throw new IllegalStateException("免费云端连接超时或结果格式不完整，请稍后重试；没有转用付费服务");}
    }
    Map<String,Object> payload(List<String> names,String category,PublicEvidenceSearch.Context context)throws Exception {
        try(var schema=new ClassPathResource("research-schema.json").getInputStream()) {
            var messages=List.of(Map.of("role","system","content",ResearchPrompt.RULES+"只输出JSON。仅根据本次提供的官方资料总结；searchSnippet不是核实正文，不得伪造quote。输出结构必须符合："+json.readTree(schema)),
                Map.of("role","user","content",json.writeValueAsString(Map.of("names",names,"foodCategory",category,"retrievedDocuments",context.documents()))));
            // 不允许将任意客户端参数透传到云端。特意不发送plugins、tools、models或:online。
            return Map.of("model",model,"messages",messages,"stream",false,"temperature",0,"max_tokens",10000,
                "response_format",Map.of("type","json_object"),"provider",ZeroCostPolicy.providerLimits());
        }
    }
    AiResult parse(JsonNode response,List<String> names,String category,Set<String> retrieved)throws Exception {
        var choice=response.path("choices").path(0);
        if(response.has("error") || !"stop".equals(choice.path("finish_reason").asText()))throw new IllegalStateException("免费模型未完整返回总结，未保存为成功结果");
        var cost=response.path("usage").path("cost");
        if(!cost.isMissingNode() && !cost.isNull() && new java.math.BigDecimal(cost.asText()).signum()!=0)
            throw new IllegalStateException("服务商响应出现非零费用标记，请暂停并核查账号；本程序未请求收费模型或工具");
        return validator.validate(json.readTree(choice.path("message").path("content").asText()).path("findings"),names,category,retrieved,
            "OpenRouter/"+response.path("model").asText(model)+" · 免费路由");
    }
}

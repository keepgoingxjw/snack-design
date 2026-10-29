package com.shiyoushu.snack;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.List;
import static com.shiyoushu.snack.ResearchModels.*;

@Component
public class ConfiguredResearchGateway implements ResearchGateway {
    private final OllamaResearchGateway local;
    private final OpenAiResearchGateway openai;
    private final OpenRouterFreeGateway free;
    private final PublicLiteratureGateway literature;
    private final DeepSeekResearchGateway deepseek;
    private final String provider;
    private final boolean zeroOnly;
    public ConfiguredResearchGateway(OllamaResearchGateway local,OpenAiResearchGateway openai,OpenRouterFreeGateway free,PublicLiteratureGateway literature,DeepSeekResearchGateway deepseek,
            @Value("${app.ai.provider:deepseek}") String provider,@Value("${app.ai.zero-cost-only:false}") boolean zeroOnly) {
        if(!List.of("deepseek","public-search","openrouter","ollama","openai").contains(provider))throw new IllegalArgumentException("AI_PROVIDER不受支持");
        this.local=local;this.openai=openai;this.free=free;this.literature=literature;this.deepseek=deepseek;this.provider=provider;this.zeroOnly=zeroOnly;
    }
    public boolean configured(){
        if(zeroOnly && List.of("deepseek","openai").contains(provider))return false;
        return switch(provider){case "deepseek" -> deepseek.configured();case "public-search" -> true;case "openrouter" -> free.configured();case "ollama" -> true;default -> openai.configured();};
    }
    public String model(){return switch(provider){case "deepseek" -> deepseek.model();case "public-search" -> PublicLiteratureGateway.MODEL;case "openrouter" -> free.model();case "ollama" -> local.model();default -> openai.model();};}
    public String configurationError(){
        if(zeroOnly && List.of("deepseek","openai").contains(provider))return "当前仍为零费用模式，已阻止付费模型。使用DeepSeek付费Key时，请设置AI_PROVIDER=deepseek及AI_ZERO_COST_ONLY=false后重启";
        if("deepseek".equals(provider))return "请仅在后端环境变量或.env中配置DEEPSEEK_API_KEY；不要把密钥发到聊天或小程序";
        return "openrouter".equals(provider)?"请在后端配置免费的OPENROUTER_API_KEY，无需充值，也不需要本机运行大模型":"后端AI未配置，请检查服务设置";
    }
    public AiResult research(List<String> names,String category){
        if(!configured())throw new IllegalStateException(configurationError());
        return switch(provider){case "deepseek" -> deepseek.research(names,category);case "public-search" -> literature.research(names,category);case "openrouter" -> free.research(names,category);case "ollama" -> local.research(names,category);default -> openai.research(names,category);};
    }
    public String provider(){return provider;}
}

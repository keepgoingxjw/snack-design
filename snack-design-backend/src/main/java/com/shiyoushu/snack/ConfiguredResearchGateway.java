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
    private final String provider;
    private final boolean zeroOnly;
    public ConfiguredResearchGateway(OllamaResearchGateway local,OpenAiResearchGateway openai,OpenRouterFreeGateway free,
            @Value("${app.ai.provider:openrouter}") String provider,@Value("${app.ai.zero-cost-only:true}") boolean zeroOnly) {
        if(!List.of("openrouter","ollama","openai").contains(provider))throw new IllegalArgumentException("AI_PROVIDER不受支持");
        this.local=local;this.openai=openai;this.free=free;this.provider=provider;this.zeroOnly=zeroOnly;
    }
    public boolean configured(){
        if(zeroOnly && "openai".equals(provider))return false;
        return switch(provider){case "openrouter" -> free.configured();case "ollama" -> true;default -> openai.configured();};
    }
    public String model(){return switch(provider){case "openrouter" -> free.model();case "ollama" -> local.model();default -> openai.model();};}
    public String configurationError(){
        if(zeroOnly && "openai".equals(provider))return "当前锁定零费用模式，禁止调用收费AI；请设置AI_PROVIDER=openrouter并配置免费云端密钥";
        return "openrouter".equals(provider)?"请在后端配置免费的OPENROUTER_API_KEY，无需充值，也不需要本机运行大模型":"后端AI未配置，请检查服务设置";
    }
    public AiResult research(List<String> names,String category){
        if(!configured())throw new IllegalStateException(configurationError());
        return switch(provider){case "openrouter" -> free.research(names,category);case "ollama" -> local.research(names,category);default -> openai.research(names,category);};
    }
    public String provider(){return provider;}
}

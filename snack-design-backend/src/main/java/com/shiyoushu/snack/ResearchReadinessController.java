package com.shiyoushu.snack;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** 本机配置检查不调用云端模型，也不消耗搜索额度。 */
@RestController
public class ResearchReadinessController {
    private final SessionService sessions;
    private final ResearchGateway gateway;
    private final PublicEvidenceSearch search;
    public ResearchReadinessController(SessionService sessions, ResearchGateway gateway, PublicEvidenceSearch search) {
        this.sessions = sessions; this.gateway = gateway; this.search = search;
    }
    @GetMapping("/api/research/readiness")
    public Map<String, Object> readiness(@RequestHeader(value="Authorization", required=false) String auth) {
        sessions.user(auth);
        if (!gateway.configured()) return Map.of("ready", false, "message", gateway.configurationError());
        if (PublicLiteratureGateway.MODEL.equals(gateway.model())) return Map.of("ready", true, "message", "公开文献检索，无需密钥；不调用 AI");
        if (gateway instanceof ConfiguredResearchGateway configured && "deepseek".equals(configured.provider()))
            return Map.of("ready", true, "message", "DeepSeek密钥已配置；搜索、抓取及模型总结会计费，权限与余额在实际查询时核实。不依赖本机模型或搜索容器");
        if (gateway instanceof ConfiguredResearchGateway configured && "openrouter".equals(configured.provider()))
            return Map.of("ready", true, "message", "免费云端 AI 密钥已配置；查询时核验零价格、免费额度和公开文献");
        // 本地模型路径也依赖同一个自托管检索服务。
        if (!search.available()) return Map.of("ready", false, "message",
            "免费联网搜索尚未就绪。请启动本机 SearXNG 并启用 JSON 搜索；不会调用付费搜索或收费模型。");
        return Map.of("ready", true, "message", "免费服务配置就绪；实际免费额度及来源可用性将在查询时校验");
    }
}

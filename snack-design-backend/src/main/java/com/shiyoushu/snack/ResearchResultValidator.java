package com.shiyoushu.snack;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.shiyoushu.snack.ResearchModels.*;

/** 对不同模型使用同一套证据校验，模型输出不能自行宣布已核实。 */
@Component
public class ResearchResultValidator {
    private final EvidenceVerifier evidence;
    public ResearchResultValidator(EvidenceVerifier evidence) { this.evidence = evidence; }
    public AiResult validate(JsonNode findings, List<String> names, String category, Set<String> retrieved, String model) {
        if (!findings.isArray() || findings.size() != names.size()) throw new IllegalStateException("AI 配料列表不完整，请重试");
        var results = new ArrayList<Finding>();
        var verified = new HashMap<String, Boolean>();
        int budget = 12;
        for (int i = 0; i < names.size(); i++) {
            var item = findings.get(i);
            String name = item.path("name").asText();
            if (!names.get(i).equals(name)) throw new IllegalStateException("AI 返回名称与原文不一致，已拒绝保存");
            var sources = new ArrayList<Source>();
            for (var source : item.path("sources")) {
                if (sources.size() == 3) break;
                String url = source.path("url").asText(), quote = source.path("quote").asText();
                if (!retrieved.contains(url) || !EvidenceVerifier.allowed(url) || quote.length() > 1600) continue;
                String cacheKey = url + "\n" + quote;
                if (!verified.containsKey(cacheKey)) verified.put(cacheKey, budget-- > 0 && evidence.verify(url, quote));
                sources.add(new Source(source.path("title").asText(), url, quote, verified.get(cacheKey)));
            }
            boolean supported = !sources.isEmpty();
            String group = item.path("group").asText("other");
            if (!Set.of("structure", "flavour", "preservation", "nutrition", "other").contains(group)) group = "other";
            results.add(new Finding(name, supported ? safe(item, "role", 100) : "资料不足",
                supported ? safe(item, "summary", 1000) : "未找到可追溯的官方检索来源，暂不提供结论。",
                supported ? safe(item, "benefit", 1500) : "未核实，不能推断健康益处。",
                supported ? safe(item, "risk", 1500) : "资料不足不代表没有风险，请核对实物标签及专业建议。",
                dose(item.path("dosage"), category, sources), sources,
                supported ? "AI 根据本次官方域名检索资料总结，未经人工审核；引用核对不代表标准适用性已获确认。" : "未核实",
                supported ? group : "pending", AdvicePolicy.validate(item.path("advice"), sources)));
        }
        return new AiResult(results, model);
    }
    private Dose dose(JsonNode d, String category, List<Source> sources) {
        if (d.isMissingNode() || d.isNull() || category.isBlank()) return null;
        String value=d.path("value").asText(), unit=d.path("unit").asText(), quote=d.path("quote").asText(), url=d.path("sourceUrl").asText();
        boolean matched=sources.stream().anyMatch(s -> s.url().equals(url) && s.quote().equals(quote) && s.textVerified());
        if (!matched || !EvidenceVerifier.chinaSource(url) || value.isBlank() || unit.isBlank()
            || !"中国大陆".equals(d.path("jurisdiction").asText()) || d.path("standard").asText().isBlank()
            || d.path("foodCategory").asText().isBlank() || !quote.contains(value) || !quote.contains(unit)
            || !quote.contains(d.path("foodCategory").asText())) return null;
        return new Dose(value,unit,d.path("foodCategory").asText(),"中国大陆",d.path("standard").asText(),url,quote,true,
            "在来源正文找到相同引用，但标准现行有效性、类别映射及 AI 解读仍待人工审核。不是个人食用建议。");
    }
    private String safe(JsonNode item,String field,int max) {
        String value=item.path(field).asText().strip();
        if(value.length()>max || value.isBlank()) return "资料不足，暂不提供结论。";
        if(value.matches("(?is).*\\d+(?:\\.\\d+)?\\s*(?:mg|kg|g/|g／|毫克|克/|克／|%|％).*")) return "此段含无法独立核对的用量数字，已隐藏；请查看用量与适用条件。";
        return value;
    }
}

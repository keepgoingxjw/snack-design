package com.shiyoushu.snack;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static com.shiyoushu.snack.ResearchModels.*;

/** 食用建议只允许条件性指导；不是对整款食品的“能吃/不能吃”安全判定。 */
public final class AdvicePolicy {
    private AdvicePolicy() {}
    private static final String CONDITIONS = "尚无实际含量、食用份量及个人健康信息，不能保证“吃一点没事”或判定整款食品能吃/不能吃。AI建议未经人工审核，需结合实物标签、个人情况及专业意见。";
    private static final Map<String, String> TITLES = Map.of(
        "check_first", "先核对，再决定",
        "limit_frequency", "注意控制摄入与频率",
        "avoid_for_group", "相关人群建议避免",
        "consult_professional", "特殊情况先咨询专业人士");
    public static Recommendation unknown() {
        return new Recommendation("check_first", TITLES.get("check_first"), "所有用户，尤其有过敏或特殊饮食需求者",
            "目前信息不足以判断这款食品是否适合你；先核对完整标签、食用量和个人限制。", CONDITIONS, List.of());
    }
    public static Recommendation validate(JsonNode input, List<Source> sources) {
        String action = input.path("action").asText();
        String audience = input.path("audience").asText().strip();
        String reason = input.path("reason").asText().strip();
        if (!TITLES.containsKey(action) || reason.isBlank() || reason.length() > 500 || audience.isBlank() || audience.length() > 150
            || reason.matches("(?s).*(绝对安全|完全无害|放心吃|吃一点没事|能吃|不能吃|保证安全|\\d+\\s*(mg|g|克|毫克|%)).*")) return unknown();
        var urls = new ArrayList<String>();
        for (var url : input.path("sourceUrls")) {
            String value = url.asText();
            if (sources.stream().anyMatch(s -> s.url().equals(value) && s.textVerified()) && !urls.contains(value)) urls.add(value);
        }
        // 没有原文摘录核对成功的引用，不给出限制或避免建议，也不以资料不足推断有害。
        if (urls.isEmpty()) return unknown();
        if ("avoid_for_group".equals(action) && audience.matches("(?s).*(所有人|任何人|一般人群|健康人群|普通人|全民).*")) return unknown();
        return new Recommendation(action, TITLES.get(action), audience, reason, CONDITIONS, urls);
    }
    public static Recommendation overall(List<Finding> findings, String status) {
        if (!"done".equals(status)) return null;
        var ranks = Map.of("check_first", 0, "limit_frequency", 1, "consult_professional", 2, "avoid_for_group", 3);
        Finding selected = findings.stream().filter(f -> f.advice() != null && !f.advice().sourceUrls().isEmpty())
            .max(Comparator.comparingInt(f -> ranks.getOrDefault(f.advice().action(), 0))).orElse(null);
        if (selected == null) return unknown();
        var advice = selected.advice();
        long pending = findings.stream().filter(f -> f.advice() == null || f.advice().sourceUrls().isEmpty()).count();
        String condition = CONDITIONS + (pending > 0 ? " 另有" + pending + "项配料的建议依据不足，请逐项核对。" : "");
        return new Recommendation(advice.action(), advice.title(), advice.audience(),
            "涉及配料：" + selected.name() + "。" + advice.reason(), condition, advice.sourceUrls());
    }
}

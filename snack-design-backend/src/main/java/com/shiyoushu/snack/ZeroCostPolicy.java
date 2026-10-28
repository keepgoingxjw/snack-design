package com.shiyoushu.snack;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** 请求前的零价格保护。不允许付费模型、在线后缀、付费插件或付费fallback。 */
public final class ZeroCostPolicy {
    private ZeroCostPolicy() {}
    public static void requireFreeModel(String model) {
        if (!("openrouter/free".equals(model) || model.matches("[a-zA-Z0-9._/-]+:free")))
            throw new IllegalArgumentException("零费用模式仅允许openrouter/free或目录中的:free模型，禁止付费模型及:online后缀");
    }
    public static void verifyCatalog(JsonNode catalog, String model) {
        requireFreeModel(model);
        for (var entry : catalog.path("data")) {
            if (!model.equals(entry.path("id").asText())) continue;
            var price = entry.path("pricing");
            if (!price.has("prompt") || !price.has("completion")) break;
            for (var fields = price.fields(); fields.hasNext();) {
                var field = fields.next();
                try {
                    if (new java.math.BigDecimal(field.getValue().asText()).compareTo(java.math.BigDecimal.ZERO) != 0)
                        throw new IllegalStateException("所选免费模型目录出现非零价格，已停止调用");
                } catch (NumberFormatException e) { throw new IllegalStateException("无法确认模型价格为零，已停止调用"); }
            }
            return;
        }
        throw new IllegalStateException("免费模型已下架或无法核实零价格，请等待免费服务恢复，不会切换付费模型");
    }
    public static Map<String, Object> providerLimits() {
        return Map.of("max_price", Map.of("prompt", 0, "completion", 0, "request", 0, "image", 0),
            "data_collection", "deny", "allow_fallbacks", false);
    }
}

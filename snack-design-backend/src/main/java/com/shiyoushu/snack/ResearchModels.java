package com.shiyoushu.snack;

import java.util.List;

/** AI 调研快照，不作为人工审核过的权威知识。 */
public final class ResearchModels {
    private ResearchModels() {}
    public record Source(String title, String url, String quote, boolean textVerified) {}
    public record Dose(String value, String unit, String foodCategory, String jurisdiction, String standard,
                       String sourceUrl, String quote, boolean textVerified, String note) {}
    public record Recommendation(String action, String title, String audience, String reason, String conditions, List<String> sourceUrls) {}
    public record Finding(String name, String role, String summary, String benefit, String risk,
                          Dose dosage, List<Source> sources, String verification, String group, Recommendation advice) {
        public Finding(String name, String role, String summary, String benefit, String risk, Dose dosage, List<Source> sources, String verification) {
            this(name,role,summary,benefit,risk,dosage,sources,verification,"other",null);
        }
    }
    public record Report(String id, String recordId, String status, String foodCategory, long createdAt,
                         long updatedAt, String model, List<Finding> findings, String error, String disclaimer, Recommendation recommendation) {}
    public record Query(String foodCategory, boolean refresh) {}
    public record AiResult(List<Finding> findings, String model) {}
}

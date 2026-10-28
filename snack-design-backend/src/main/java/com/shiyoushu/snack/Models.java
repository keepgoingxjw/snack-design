package com.shiyoushu.snack;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class Models {
    private Models() {}
    public record RecordInput(@NotBlank @Size(max = 5000) String raw,
            @NotBlank @Pattern(regexp = "手动输入|虚构示例|照片手动录入") String kind) {}
    public record IngredientRecord(String id, String raw, String kind, long createdAt, long updatedAt) {}
    public record Ingredient(int id, String name, String group, String role, String benefit,
                             String risk, String source, String url) {}
    public record Analysis(List<Ingredient> rows, List<String> allergens, String boundary) {}
    public record RawInput(@NotBlank @Size(max = 5000) String raw) {}
    public record LoginInput(@NotBlank @Size(max = 256) String code) {}
    public record Session(String token, long expiresAt) {}
}

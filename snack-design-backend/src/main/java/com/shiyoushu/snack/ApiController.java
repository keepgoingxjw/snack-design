package com.shiyoushu.snack;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import static com.shiyoushu.snack.Models.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final SessionService sessions;
    private final RecordService records;
    private final IngredientService ingredients;
    private final ResearchGateway gateway;
    private final boolean zeroCostOnly;
    public ApiController(SessionService sessions, RecordService records, IngredientService ingredients, ResearchGateway gateway,
            @org.springframework.beans.factory.annotation.Value("${app.ai.zero-cost-only:true}") boolean zeroCostOnly) {
        this.sessions = sessions; this.records = records; this.ingredients = ingredients; this.gateway = gateway; this.zeroCostOnly = zeroCostOnly;
    }
    @GetMapping("/health")
    public Map<String, Object> health() { return Map.of("status", "ok", "apiVersion", 3, "researchAvailable", true, "aiModel", String.valueOf(gateway.model()), "aiConfigured", gateway.configured(), "zeroCostOnly", zeroCostOnly); }
    @GetMapping("/records")
    public List<IngredientRecord> all(@RequestHeader(value = "Authorization", required = false) String auth) { return records.all(sessions.user(auth)); }
    @GetMapping("/records/{id}")
    public IngredientRecord find(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String id) { return records.find(sessions.user(auth), id); }
    @PostMapping("/records")
    public IngredientRecord create(@RequestHeader(value = "Authorization", required = false) String auth, @Valid @RequestBody RecordInput input) { return records.save(sessions.user(auth), null, input); }
    @PutMapping("/records/{id}")
    public IngredientRecord update(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String id, @Valid @RequestBody RecordInput input) { return records.save(sessions.user(auth), id, input); }
    @DeleteMapping("/records/{id}")
    public Map<String, Boolean> remove(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String id) { records.remove(sessions.user(auth), id); return Map.of("ok", true); }
    @DeleteMapping("/records")
    public Map<String, Boolean> clear(@RequestHeader(value = "Authorization", required = false) String auth) { records.clear(sessions.user(auth)); return Map.of("ok", true); }
    @PostMapping("/ingredients/analyze")
    public Analysis analyze(@RequestHeader(value = "Authorization", required = false) String auth, @Valid @RequestBody RawInput input) {
        sessions.user(auth);
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE,
            "示例配料解析已停用，请使用记录的 research 联网调研接口");
    }
}

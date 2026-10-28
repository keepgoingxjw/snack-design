package com.shiyoushu.snack;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Component
@org.springframework.context.annotation.Profile("legacy-demo")
public class KnowledgeSeed implements ApplicationRunner {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public KnowledgeSeed(JdbcTemplate db, ObjectMapper json) { this.db = db; this.json = json; }
    @Override @Transactional
    public void run(ApplicationArguments args) throws Exception {
        try (var input = new ClassPathResource("knowledge.json").getInputStream()) {
            List<Map<String, String>> rows = json.readValue(input, new TypeReference<>() {});
            for (var row : rows) {
                Integer count = db.queryForObject("SELECT COUNT(*) FROM ingredient_knowledge WHERE name = ?", Integer.class, row.get("name"));
                if (count != null && count == 0) db.update("INSERT INTO ingredient_knowledge (name, group_id, role, benefit, risk, source, url) VALUES (?,?,?,?,?,?,?)",
                    row.get("name"), row.get("group"), row.get("role"), row.get("benefit"), row.get("risk"), row.get("source"), row.get("url"));
            }
        }
    }
}

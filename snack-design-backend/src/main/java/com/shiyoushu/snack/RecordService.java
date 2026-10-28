package com.shiyoushu.snack;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import static com.shiyoushu.snack.Models.*;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class RecordService {
    private final JdbcTemplate db;
    public RecordService(JdbcTemplate db) { this.db = db; }
    private IngredientRecord map(ResultSet rs, int n) throws SQLException {
        return new IngredientRecord(rs.getString("id"), rs.getString("raw_text"), rs.getString("kind"), rs.getLong("created_at"), rs.getLong("updated_at"));
    }
    public List<IngredientRecord> all(String user) {
        return db.query("SELECT * FROM ingredient_records WHERE user_id = ? ORDER BY updated_at DESC, id DESC LIMIT 50", this::map, user);
    }
    public IngredientRecord find(String user, String id) {
        return db.query("SELECT * FROM ingredient_records WHERE user_id = ? AND id = ?", this::map, user, id)
            .stream().findFirst().orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "记录不存在或已删除"));
    }
    @Transactional
    public IngredientRecord save(String user, String id, RecordInput input) {
        IngredientService.split(input.raw());
        long now = System.currentTimeMillis();
        if (id == null) {
            id = UUID.randomUUID().toString();
            db.update("INSERT INTO ingredient_records (id,user_id,raw_text,kind,created_at,updated_at) VALUES (?,?,?,?,?,?)", id, user, input.raw().strip(), input.kind(), now, now);
        } else {
            if (db.update("UPDATE ingredient_records SET raw_text = ?, updated_at = ? WHERE user_id = ? AND id = ?", input.raw().strip(), now, user, id) == 0)
                throw new ResponseStatusException(NOT_FOUND, "记录不存在或已删除");
        }
        return find(user, id);
    }
    public void remove(String user, String id) { db.update("DELETE FROM ingredient_records WHERE user_id = ? AND id = ?", user, id); }
    public void clear(String user) { db.update("DELETE FROM ingredient_records WHERE user_id = ?", user); }
}

package com.shiyoushu.snack;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static com.shiyoushu.snack.ResearchModels.*;
import static org.springframework.http.HttpStatus.*;

@Service
public class ResearchService implements ApplicationRunner {
    private static final String DISCLAIMER = "AI 联网调研记录，非人工审核知识库。作用不等于健康收益，可能风险不等于本品有害。用量不是个人食用建议；标准现行性及食品类别适用性须人工复核。";
    private final JdbcTemplate db;
    private final RecordService records;
    private final ResearchGateway gateway;
    private final ObjectMapper json;
    private final int dailyLimit;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(10), r -> { var thread = new Thread(r, "ingredient-research"); thread.setDaemon(true); return thread; });
    public ResearchService(JdbcTemplate db, RecordService records, ResearchGateway gateway, ObjectMapper json,
            @org.springframework.beans.factory.annotation.Value("${app.ai.daily-limit:30}") int dailyLimit) {
        this.db = db; this.records = records; this.gateway = gateway; this.json = json; this.dailyLimit = dailyLimit;
    }
    @Override public void run(ApplicationArguments args) {
        db.update("UPDATE research_reports SET status='failed', error_message=?, updated_at=? WHERE status IN ('queued','running')",
            "服务已重启，查询中断。请手动重新查询。", System.currentTimeMillis());
    }
    @PreDestroy public void stop() { workers.shutdownNow(); }
    public synchronized Report create(String user, String recordId, Query query) {
        var record = records.find(user, recordId);
        String category = query.foodCategory() == null ? "" : query.foodCategory().strip();
        if (category.length() > 200) throw new IllegalArgumentException("食品类别最多200字");
        var latest = latest(user, recordId);
        if (latest != null && latest.foodCategory().equals(category)
            && (List.of("queued", "running").contains(latest.status()) || (!query.refresh() && "done".equals(latest.status())))) return latest;
        if (!gateway.configured()) {
            String message = gateway.configurationError();
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, message == null ? "AI服务未配置" : message);
        }
        Integer active = db.queryForObject("SELECT COUNT(*) FROM research_reports WHERE user_id=? AND status IN ('queued','running')", Integer.class, user);
        if (active != null && active > 0) throw new ResponseStatusException(TOO_MANY_REQUESTS, "已有联网查询进行中，请等待完成后重试");
        Integer recent = db.queryForObject("SELECT COUNT(*) FROM research_reports WHERE user_id=? AND created_at>?", Integer.class, user, System.currentTimeMillis() - 86400000L);
        if (recent != null && recent >= dailyLimit) throw new ResponseStatusException(TOO_MANY_REQUESTS, "已达到当前用户24小时联网查询次数上限");
        List<String> names = IngredientService.split(record.raw());
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        db.update("INSERT INTO research_reports (id,record_id,user_id,raw_snapshot,food_category,status,model,findings_json,error_message,created_at,updated_at) VALUES (?,?,?,?,?,'queued',?,'[]','',?,?)",
            id, recordId, user, record.raw(), category, gateway.model(), now, now);
        try { workers.execute(() -> execute(id, user, recordId, names, category)); }
        catch (RejectedExecutionException e) {
            fail(id, "当前联网查询任务较多，请稍后重试");
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "当前联网查询任务较多，请稍后重试");
        }
        return find(user, id);
    }
    private void execute(String id, String user, String recordId, List<String> names, String category) {
        try {
            if (db.update("UPDATE research_reports SET status='running',updated_at=? WHERE id=?", System.currentTimeMillis(), id) == 0) return;
            var result = gateway.research(names, category);
            // 用户在查询期间修改了原文时，保留历史快照，但不让它覆盖当前原文的解析。
            records.find(user, recordId);
            db.update("UPDATE research_reports SET status='done',model=?,findings_json=?,updated_at=? WHERE id=?",
                result.model(), json.writeValueAsString(result.findings()), System.currentTimeMillis(), id);
        } catch (Exception e) {
            String message = e instanceof IllegalStateException ? e.getMessage() : "查询失败或记录已删除，请稍后重试";
            fail(id, message == null ? "查询未完成" : message.substring(0, Math.min(message.length(), 1000)));
        }
    }
    private void fail(String id, String message) { db.update("UPDATE research_reports SET status='failed',error_message=?,updated_at=? WHERE id=?", message, System.currentTimeMillis(), id); }
    public Report find(String user, String id) {
        return db.query("SELECT * FROM research_reports WHERE user_id=? AND id=?", this::map, user, id).stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "查询记录不存在或已删除"));
    }
    public Report latest(String user, String recordId) {
        var record = records.find(user, recordId);
        return db.query("SELECT * FROM research_reports WHERE user_id=? AND record_id=? AND raw_snapshot=? ORDER BY created_at DESC LIMIT 1",
            this::map, user, recordId, record.raw()).stream().findFirst().orElse(null);
    }
    public List<Report> history(String user, String recordId) {
        records.find(user, recordId);
        return db.query("SELECT * FROM research_reports WHERE user_id=? AND record_id=? ORDER BY created_at DESC LIMIT 10", this::map, user, recordId);
    }
    private Report map(ResultSet rs, int n) throws SQLException {
        try {
            List<Finding> findings = json.readValue(rs.getString("findings_json"), new TypeReference<List<Finding>>() {});
            return new Report(rs.getString("id"), rs.getString("record_id"), rs.getString("status"), rs.getString("food_category"),
                rs.getLong("created_at"), rs.getLong("updated_at"), rs.getString("model"), findings,
                rs.getString("error_message"), DISCLAIMER, AdvicePolicy.overall(findings, rs.getString("status")));
        } catch (Exception e) { throw new SQLException("调研记录解析失败", e); }
    }
}

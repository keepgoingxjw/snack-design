package com.shiyoushu.snack;

import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import static com.shiyoushu.snack.ResearchModels.*;

@RestController
@RequestMapping("/api")
public class ResearchController {
    private final SessionService sessions;
    private final ResearchService research;
    public ResearchController(SessionService sessions, ResearchService research) { this.sessions = sessions; this.research = research; }
    @PostMapping("/records/{id}/research")
    public Report create(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable String id, @RequestBody Query query) {
        return research.create(sessions.user(auth), id, query);
    }
    @GetMapping("/records/{id}/research/latest")
    public Map<String, Object> latest(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable String id) {
        Report report = research.latest(sessions.user(auth), id);
        return report == null ? Map.of("exists", false) : Map.of("exists", true, "report", report);
    }
    @GetMapping("/records/{id}/research/history")
    public List<Report> history(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable String id) {
        return research.history(sessions.user(auth), id);
    }
    @GetMapping("/research/{id}")
    public Report find(@RequestHeader(value="Authorization", required=false) String auth, @PathVariable String id) {
        return research.find(sessions.user(auth), id);
    }
}

package com.shiyoushu.snack;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import static com.shiyoushu.snack.Models.*;

@Service
public class IngredientService {
    private final JdbcTemplate db;
    public IngredientService(JdbcTemplate db) { this.db = db; }

    public static List<String> split(String raw) {
        if (raw == null || raw.length() > 5000) throw new IllegalArgumentException("原文不能为空且不超过5000字");
        String text = raw.replaceFirst("^\\s*配料表?\\s*[:：]\\s*", "");
        var values = new ArrayList<String>();
        var brackets = new ArrayDeque<Character>();
        var buffer = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == '(' || c == '（') brackets.push(c);
            if (c == ')' || c == '）') {
                if (brackets.isEmpty() || brackets.pop() != (c == ')' ? '(' : '（'))
                    throw new IllegalArgumentException("括号不完整或不匹配，请核对原文");
            }
            if (brackets.isEmpty() && "、，,；;\n\r".indexOf(c) >= 0) {
                add(values, buffer); buffer.setLength(0);
            } else buffer.append(c);
        }
        if (!brackets.isEmpty()) throw new IllegalArgumentException("括号不完整，请核对原文");
        add(values, buffer);
        if (values.isEmpty()) throw new IllegalArgumentException("请至少填写一种配料");
        if (values.size() > 30) throw new IllegalArgumentException("一次最多填写30项配料");
        return values;
    }
    private static void add(List<String> values, StringBuilder buffer) {
        if (!buffer.toString().isBlank()) values.add(buffer.toString().strip());
    }
    public Analysis analyze(String raw) {
        var names = split(raw);
        var rows = new ArrayList<Ingredient>();
        for (int i = 0; i < names.size(); i++) {
            final int index = i;
            String name = names.get(i);
            var matches = db.query("SELECT * FROM ingredient_knowledge WHERE name = ?", (rs, n) ->
                new Ingredient(index, name, rs.getString("group_id"), rs.getString("role"),
                    rs.getString("benefit"), rs.getString("risk"), rs.getString("source"), rs.getString("url")), name);
            rows.add(matches.isEmpty() ? new Ingredient(index, name, "pending", "待解析", "暂无可核实的配料解释。",
                "不能仅根据名称判断好处或危害。复合配料保留原文，需进一步核对。", "", "") : matches.getFirst());
        }
        var allergens = new ArrayList<String>();
        String[][] clues = {{"小麦|麦粉|面粉", "小麦"}, {"乳|奶", "乳类"}, {"花生", "花生"},
            {"大豆|黄豆|豆粉|豆蛋白", "大豆"}, {"鸡蛋|鸭蛋|蛋清|蛋黄|全蛋", "蛋类"}};
        for (var clue : clues) if (java.util.regex.Pattern.compile(clue[0]).matcher(raw).find()) allergens.add(clue[1]);
        return new Analysis(rows, allergens, "仅提供一般用途及部分过敏原名称线索；没有添加量和营养成分表，不评估商品安全或营养等级。");
    }
}

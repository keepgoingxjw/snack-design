package com.shiyoushu.snack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import static com.shiyoushu.snack.ResearchModels.*;

/** 无密钥公开文献检索；不调用任何推理、搜索计费或代理接口。 */
@Component
public class PublicLiteratureGateway {
    public static final String MODEL = "public-search/EuropePMC";
    private static final Map<String,String> TERMS = Map.ofEntries(
        Map.entry("食盐","sodium chloride"), Map.entry("盐","sodium chloride"),
        Map.entry("白砂糖","sucrose"), Map.entry("蔗糖","sucrose"), Map.entry("糖","sucrose"),
        Map.entry("苯甲酸钠","sodium benzoate"), Map.entry("山梨酸钾","potassium sorbate"),
        Map.entry("柠檬酸","citric acid"), Map.entry("抗坏血酸","ascorbic acid"), Map.entry("维生素C","ascorbic acid"),
        Map.entry("阿斯巴甜","aspartame"), Map.entry("三氯蔗糖","sucralose"), Map.entry("赤藓糖醇","erythritol"),
        Map.entry("木糖醇","xylitol"), Map.entry("安赛蜜","acesulfame potassium"),
        Map.entry("味精","monosodium glutamate"), Map.entry("谷氨酸钠","monosodium glutamate"),
        Map.entry("乳粉","milk powder"), Map.entry("全脂乳粉","whole milk powder"), Map.entry("脱脂乳粉","skim milk powder"),
        Map.entry("小麦粉","wheat flour"), Map.entry("淀粉","starch"), Map.entry("麦芽糊精","maltodextrin"),
        Map.entry("大豆卵磷脂","soy lecithin"), Map.entry("卵磷脂","lecithin"),
        Map.entry("黄原胶","xanthan gum"), Map.entry("卡拉胶","carrageenan"), Map.entry("果胶","pectin"),
        Map.entry("棕榈油","palm oil"), Map.entry("植物油","vegetable oil"), Map.entry("可可粉","cocoa powder"));
    private final ObjectMapper json;
    private final HttpClient http;
    @org.springframework.beans.factory.annotation.Autowired
    public PublicLiteratureGateway(ObjectMapper json) {
        // 当前公共端点的 HTTP/2 连接会提前 EOF；显式使用已验证可用的 HTTP/1.1。
        this(json,HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build());
    }
    PublicLiteratureGateway(ObjectMapper json,HttpClient http) { this.json=json; this.http=http; }
    static String term(String name) {
        if(TERMS.containsKey(name)) return TERMS.get(name);
        return name.matches("[a-zA-Z][a-zA-Z0-9 -]{0,99}") ? name : null;
    }
    public AiResult research(List<String> names,String category) {
        return researchWithTerms(names,names.stream().map(PublicLiteratureGateway::term).toList());
    }
    public AiResult researchWithTerms(List<String> names,List<String> terms) {
        if(names.size()!=terms.size()) throw new IllegalStateException("配料检索词数量不匹配");
        var findings=new ArrayList<Finding>();
        for(int i=0;i<names.size();i++) {
            String name=names.get(i),term=terms.get(i);
            if(term!=null && !term.matches("[a-zA-Z][a-zA-Z0-9 -]{0,99}")) throw new IllegalStateException("AI 返回的英文检索词格式不正确，已停止查询");
            var sources=new ArrayList<Source>();
            if(term!=null) {
                String query="TITLE_ABS:\""+term+"\" AND (food OR dietary) AND HAS_ABSTRACT:y AND OPEN_ACCESS:y";
                try {
                    var request=HttpRequest.newBuilder(URI.create("https://www.ebi.ac.uk/europepmc/webservices/rest/search?format=json&resultType=core&pageSize=2&query="+URLEncoder.encode(query,StandardCharsets.UTF_8)))
                        .timeout(Duration.ofSeconds(20)).header("User-Agent","SnackDesign/0.1 (public literature search)").GET().build();
                    var response=http.send(request,HttpResponse.BodyHandlers.ofString());
                    if(response.statusCode()==429) throw new IllegalStateException("免费公开检索已限流，本次查询停止；不会自动重试或切换收费接口");
                    if(response.statusCode()!=200) throw new IllegalStateException("免费公开检索暂不可用（HTTP "+response.statusCode()+"），已停止，不使用收费替代");
                    var body=json.readTree(response.body());
                    if(!body.path("resultList").path("result").isArray()) throw new IllegalStateException("公开检索返回格式异常，未保存为成功结果");
                    for(var item:body.path("resultList").path("result")) {
                        String id=item.path("id").asText(), source=item.path("source").asText();
                        if(!id.matches("[a-zA-Z0-9_-]+") || !source.matches("[A-Z]+")) continue;
                        String excerpt=plain(item.path("abstractText").asText());
                        if(excerpt.isBlank()) continue;
                        sources.add(new Source(plain(item.path("title").asText())+" · "+item.path("pubYear").asText(),
                            "https://europepmc.org/article/"+source+"/"+id,excerpt.substring(0,Math.min(1000,excerpt.length())),false));
                    }
                } catch(IllegalStateException e) { throw e; }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("公开检索已中断，请手动重试"); }
                catch(Exception e) { throw new IllegalStateException("无法连接免费公开检索，请检查网络后手动重试；没有调用收费服务"); }
            }
            String summary=term==null ? "暂未收录此中文配料的英文检索词，可先尝试苯甲酸钠、柠檬酸、食盐等常见配料，或输入英文名称。"
                : sources.isEmpty() ? "本次未检索到带摘要的开放文献，不据此推断安全或风险。"
                : "已找到 "+sources.size()+" 篇开放文献。下方为接口返回的英文摘要节选，未经过 AI 总结、翻译或食品标准适用性审核。";
            findings.add(new Finding(name,"公开文献检索",summary,summary,
                "文献可能涉及实验或特定人群，不能直接推断该食品的健康益处、风险或食用量。",null,sources,
                "检索词："+(term==null?"未匹配":term)+"；限定食品/饮食相关开放文献。公开 API 检索，未调用 AI。",
                "pending",null));
        }
        return new AiResult(findings,MODEL);
    }
    private static String plain(String value) { return value.replaceAll("<[^>]+>"," ").replace("&amp;","&").replace("&lt;","<").replace("&gt;",">").replaceAll("\\s+"," ").strip(); }
}

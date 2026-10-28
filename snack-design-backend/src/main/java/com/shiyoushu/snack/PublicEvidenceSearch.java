package com.shiyoushu.snack;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** 免费公开网页检索，与云端推理分开。只调用自托管的轻量SearXNG，不使用计费搜索插件。 */
@Component
public class PublicEvidenceSearch {
    public record Context(List<Map<String,Object>> documents, Set<String> urls) {}
    private final ObjectMapper json;
    private final EvidenceVerifier evidence;
    private final String base;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public PublicEvidenceSearch(ObjectMapper json,EvidenceVerifier evidence,@Value("${app.ai.search-url:http://127.0.0.1:8888}") String base) {
        URI uri=URI.create(base);
        if(!"http".equals(uri.getScheme()) || !Set.of("localhost","127.0.0.1","[::1]").contains(String.valueOf(uri.getHost())) || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null)
            throw new IllegalArgumentException("零费用检索只允许本机自托管搜索服务，不使用不明远程代理");
        this.json=json;this.evidence=evidence;this.base=base.replaceAll("/+$","");
    }
    public Context lookup(List<String> names,String category) {
        var urls=new HashSet<String>();var context=new ArrayList<Map<String,Object>>();int budget=12;
        try {
            for(String name:names) {
                String domains=String.join(" OR ",EvidenceVerifier.DOMAINS.stream().map(d->"site:"+d).toList());
                String query="("+domains+") \""+name+"\" 食品 用途 安全 使用标准 "+category;
                var response=http.send(HttpRequest.newBuilder(URI.create(base+"/search?format=json&language=zh-CN&q="+URLEncoder.encode(query,StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(25)).GET().build(),HttpResponse.BodyHandlers.ofString());
                if(response.statusCode()!=200)throw new IllegalStateException("免费检索服务不可用，请启动轻量SearXNG；不会启用收费联网搜索");
                var docs=new ArrayList<Map<String,String>>();
                for(var item:json.readTree(response.body()).path("results")) {
                    String url=item.path("url").asText();if(!EvidenceVerifier.allowed(url))continue;
                    urls.add(url);String text=budget-- > 0?evidence.readText(url):"";
                    docs.add(Map.of("title",item.path("title").asText(),"url",url,"searchSnippet",clip(item.path("content").asText(),1000),
                        "officialBody",clip(text,5000),"bodyState",text.isBlank()?"正文未核实，搜索摘要不可当作原文":"已读取部分正文"));
                    if(docs.size()==2)break;
                }
                context.add(Map.of("name",name,"documents",docs));
            }
        }catch(IllegalStateException e){throw e;}catch(Exception e){throw new IllegalStateException("免费检索未启动或网络受限。可运行start-zero-cost-cloud.cmd；不需要下载本地模型，也不会改用收费搜索");}
        if(urls.isEmpty())throw new IllegalStateException("本次免费检索未找到可追溯的官方资料，暂停AI总结，不把模型记忆冒充实时核实");
        return new Context(context,urls);
    }
    private static String clip(String text,int max){return text.substring(0,Math.min(text.length(),max));}
}

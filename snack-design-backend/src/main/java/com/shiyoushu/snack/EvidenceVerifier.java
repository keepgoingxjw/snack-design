package com.shiyoushu.snack;

import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Component
public class EvidenceVerifier {
    public static final List<String> DOMAINS = List.of("nhc.gov.cn", "samr.gov.cn", "cfsa.net.cn", "who.int", "fda.gov", "ecfr.gov", "efsa.europa.eu");
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    public static boolean allowed(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            return "https".equals(uri.getScheme()) && uri.getRawUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443)
                && DOMAINS.stream().anyMatch(d -> host.equals(d) || host.endsWith("." + d));
        } catch (Exception e) { return false; }
    }
    public static boolean chinaSource(String url) {
        if (!allowed(url)) return false;
        String host = URI.create(url).getHost();
        return host.endsWith(".gov.cn") || host.equals("cfsa.net.cn") || host.endsWith(".cfsa.net.cn");
    }
    /** 仅校验引用确实存在于当前可读取的 HTML 原文；不等于确认 AI 的语义解读。 */
    public boolean verify(String url, String quote) {
        if (!allowed(url) || quote == null || normalize(quote).length() < 12 || quote.length() > 1600) return false;
        return normalize(readText(url)).contains(normalize(quote));
    }
    /** 失败返回空正文，不能让AI把搜索摘要当作已核实的原文。 */
    public String readText(String url) {
        if (!allowed(url)) return "";
        try {
            var response = client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Shiyoushu-Evidence/0.1").GET().build(), ignored -> new LimitedBody());
            if (response.statusCode() != 200 || !response.headers().firstValue("content-type").orElse("").contains("text/html")) return "";
            String html = new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
            return html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?s)<[^>]+>", " ").replace("&nbsp;", " ").replace("&amp;", "&").replaceAll("\\s+", " ");
        } catch (Exception e) { return ""; }
    }
    /** 限制正文体积，并让 HttpRequest 的超时覆盖完整正文接收。 */
    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final java.util.concurrent.CompletableFuture<byte[]> result = new java.util.concurrent.CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        private java.util.concurrent.Flow.Subscription subscription;
        public java.util.concurrent.CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(java.util.concurrent.Flow.Subscription s) { subscription = s; s.request(1); }
        public void onNext(List<java.nio.ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (bytes.size() + buffer.remaining() > 1_000_000) {
                    subscription.cancel(); result.completeExceptionally(new IllegalStateException("正文过大")); return;
                }
                byte[] part = new byte[buffer.remaining()]; buffer.get(part); bytes.writeBytes(part);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(error); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
    public static String normalize(String value) { return value.replaceAll("[\\s\\p{Z}]+", ""); }
}

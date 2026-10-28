package com.shiyoushu.snack;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import static com.shiyoushu.snack.Models.*;
import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final SessionService sessions;
    private final String appId;
    private final String secret;
    private final RestClient client;
    public AuthController(SessionService sessions, @Value("${app.wechat.app-id}") String appId,
                          @Value("${app.wechat.secret}") String secret) {
        this.sessions = sessions; this.appId = appId; this.secret = secret;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(8));
        client = RestClient.builder().requestFactory(factory).build();
    }
    @PostMapping("/wechat")
    public Session login(@Valid @RequestBody LoginInput input) {
        if (appId.isBlank() || secret.isBlank()) throw new ResponseStatusException(SERVICE_UNAVAILABLE, "尚未配置微信登录");
        Map<?, ?> result;
        try {
            result = client.get().uri(builder -> builder.scheme("https").host("api.weixin.qq.com").path("/sns/jscode2session")
                .queryParam("appid", appId).queryParam("secret", secret).queryParam("js_code", input.code())
                .queryParam("grant_type", "authorization_code").build()).retrieve().body(Map.class);
        } catch (Exception e) { throw new ResponseStatusException(BAD_GATEWAY, "微信登录服务暂不可用，请稍后重试"); }
        if (result == null || !(result.get("openid") instanceof String openid) || openid.isBlank())
            throw new ResponseStatusException(UNAUTHORIZED, "微信登录校验失败，请重试");
        return sessions.login("wechat:" + appId + ":" + openid);
    }
}

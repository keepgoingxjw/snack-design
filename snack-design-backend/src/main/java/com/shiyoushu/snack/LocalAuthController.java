package com.shiyoushu.snack;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import static com.shiyoushu.snack.Models.*;

// 只在 local 配置下注册。测试设备标识不是生产身份凭证。
@Profile("local")
@RestController
public class LocalAuthController {
    private final SessionService sessions;
    public LocalAuthController(SessionService sessions) { this.sessions = sessions; }
    public record DevLogin(@NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{16,80}") String deviceId) {}
    @PostMapping("/api/auth/dev")
    public Session login(@Valid @RequestBody DevLogin input) { return sessions.login("dev:" + input.deviceId()); }
}

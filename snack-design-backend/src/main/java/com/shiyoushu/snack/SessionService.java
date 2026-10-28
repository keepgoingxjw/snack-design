package com.shiyoushu.snack;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static com.shiyoushu.snack.Models.*;

@Service
public class SessionService {
    private final JdbcTemplate db;
    public SessionService(JdbcTemplate db) { this.db = db; }
    @Transactional
    public Session login(String identity) {
        var ids = db.queryForList("SELECT id FROM app_users WHERE identity_key = ?", String.class, identity);
        String userId;
        if (ids.isEmpty()) {
            userId = UUID.randomUUID().toString();
            db.update("INSERT INTO app_users (id, identity_key, created_at) VALUES (?,?,?)", userId, identity, System.currentTimeMillis());
        } else userId = ids.getFirst();
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long expires = System.currentTimeMillis() + 7L * 24 * 3600 * 1000;
        db.update("DELETE FROM sessions WHERE expires_at < ?", System.currentTimeMillis());
        db.update("INSERT INTO sessions (token_hash, user_id, expires_at) VALUES (?,?,?)", hash(token), userId, expires);
        return new Session(token, expires);
    }
    public String user(String header) {
        if (header == null || !header.startsWith("Bearer ") || header.length() > 128) throw unauthorized();
        var ids = db.queryForList("SELECT user_id FROM sessions WHERE token_hash = ? AND expires_at > ?", String.class,
            hash(header.substring(7)), System.currentTimeMillis());
        if (ids.isEmpty()) throw unauthorized();
        return ids.getFirst();
    }
    private ResponseStatusException unauthorized() { return new ResponseStatusException(UNAUTHORIZED, "登录已过期，请重新登录"); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}

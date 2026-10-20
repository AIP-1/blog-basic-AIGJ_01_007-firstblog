package com.ownlog.auth.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 소셜 로그인 성공 → 화면으로 돌아갈 때 JWT 대신 넘기는 "1회용 교환 코드".
 *
 * JWT를 주소에 바로 실으면 브라우저 기록·Referer·로그에 남을 수 있어요. 그래서 60초 동안 한 번만 쓸 수 있는 코드를 주고,
 * 화면이 POST /api/auth/oauth/exchange로 코드를 내면 그때 JWT를 응답 본문으로 줘요 (Authorization Code 방식과 같은 생각).
 *
 * <p>코드는 DB(oauth_login_codes)에 둬요. 서버 메모리에 두면 블루-그린 전환 중에 blue가 준 코드를 green이 몰라서
 * 교환이 실패했어요 (트러블슈팅 24번). DB에는 코드 원문이 아니라 SHA-256 해시만 저장해서, DB가 보여도 코드를 쓸 수 없어요.
 * 꺼낼 때는 DELETE … RETURNING 한 문장이라, 같은 코드로 동시에 두 번 와도 한 번만 성공해요.
 */
@Component
@RequiredArgsConstructor
public class OAuthLoginCodeStore {

    static final Duration TTL = Duration.ofSeconds(60);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;

    public String issue(Long userId) {
        jdbcTemplate.update("DELETE FROM oauth_login_codes WHERE expires_at <= now()");   // 쓰이지 않은 코드 정리
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbcTemplate.update("INSERT INTO oauth_login_codes (code_hash, user_id, expires_at) VALUES (?, ?, now() + ?::interval)",
                hash(code), userId, TTL.toSeconds() + " seconds");
        return code;
    }

    /** 한 번 꺼내면 지워져요. 없거나 시간이 지났으면 empty */
    public Optional<Long> consume(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        List<Long> rows = jdbcTemplate.query(
                "DELETE FROM oauth_login_codes WHERE code_hash = ? AND expires_at > now() RETURNING user_id",
                (rs, i) -> rs.getLong(1), hash(code));
        return rows.stream().findFirst();
    }

    private static String hash(String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // SHA-256은 모든 JVM에 있어요
        }
    }
}

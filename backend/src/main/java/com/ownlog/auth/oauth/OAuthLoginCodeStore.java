package com.ownlog.auth.oauth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 소셜 로그인 성공 → 화면으로 돌아갈 때 JWT 대신 넘기는 "1회용 교환 코드".
 *
 * JWT를 주소에 바로 실으면 브라우저 기록·Referer·로그에 남을 수 있어요. 그래서 60초 동안 한 번만 쓸 수 있는 코드를 주고,
 * 화면이 POST /api/auth/oauth/exchange로 코드를 내면 그때 JWT를 응답 본문으로 줘요 (Authorization Code 방식과 같은 생각).
 * 서버 메모리에 두는 이유: 코드는 몇 초 안에 쓰이고 사라져서 DB에 남길 필요가 없어요.
 */
@Component
public class OAuthLoginCodeStore {

    static final Duration TTL = Duration.ofSeconds(60);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Entry> codes = new ConcurrentHashMap<>();
    private final Clock clock;

    public OAuthLoginCodeStore() {
        this(Clock.systemUTC());
    }

    OAuthLoginCodeStore(Clock clock) {
        this.clock = clock;
    }

    public String issue(Long userId) {
        Instant now = clock.instant();
        codes.values().removeIf(e -> e.expiresAt().isBefore(now));   // 쓰이지 않은 코드가 쌓이지 않게
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        codes.put(code, new Entry(userId, now.plus(TTL)));
        return code;
    }

    /** 한 번 꺼내면 지워져요. 없거나 시간이 지났으면 empty */
    public Optional<Long> consume(String code) {
        if (code == null) {
            return Optional.empty();
        }
        Entry entry = codes.remove(code);
        if (entry == null || entry.expiresAt().isBefore(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(entry.userId());
    }

    private record Entry(Long userId, Instant expiresAt) {
    }
}

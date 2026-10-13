package com.ownlog.auth.oauth;

import com.ownlog.blog.domain.Blog;
import com.ownlog.blog.repository.BlogRepository;
import com.ownlog.category.domain.Category;
import com.ownlog.category.repository.CategoryRepository;
import com.ownlog.user.domain.User;
import com.ownlog.user.domain.UsernamePolicy;
import com.ownlog.user.repository.UserRepository;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 로그인 (U-09). 처음 온 소셜 계정이면 회원 + 블로그 + "미분류"를 만들고, 다음부터는 연결된 회원으로 로그인해요.
 *
 * 같은 이메일의 기존 회원에 자동으로 연결하지 않아요. 연결해 주면 편하지만, 이메일 확인이 느슨한 서비스가 하나라도 있으면
 * 남의 이메일로 소셜 계정을 만들어 그 사람 계정에 들어갈 수 있어요 (계정 탈취). 그래서 이메일이 겹치면 안내만 해요.
 */
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    static final String NO_EMAIL_DOMAIN = "social.ownlog.invalid";
    private static final String DEFAULT_NICKNAME = "OwnLog 사용자";
    private static final String RANDOM_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;
    private final BlogRepository blogRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User login(SocialProfile profile) {
        Optional<SocialAccount> linked = socialAccountRepository
                .findByProviderAndProviderUserId(profile.provider(), profile.providerUserId());
        if (linked.isPresent()) {
            return userRepository.findById(linked.get().getUserId())
                    .filter(User::isActive)
                    .orElseThrow(() -> new SocialLoginException("inactive", "탈퇴한 회원과 연결된 소셜 계정"));
        }
        return signup(profile);
    }

    private User signup(SocialProfile profile) {
        String email = profile.email() != null
                ? User.normalizeEmail(profile.email())
                // 이메일을 주지 않는 서비스(깃허브 비공개, 카카오 이메일 미동의)도 있어서 겹치지 않는 자리표시 주소를 써요
                : profile.provider().id() + "_" + profile.providerUserId() + "@" + NO_EMAIL_DOMAIN;
        if (userRepository.existsByEmail(email)) {
            throw new SocialLoginException("email_in_use", "이미 가입된 이메일");
        }

        User user = userRepository.save(User.createSocial(
                email, passwordEncoder.encode(UUID.randomUUID().toString()),
                newUsername(profile), nickname(profile.nickname())));
        Blog blog = blogRepository.save(Blog.createDefault(user));
        categoryRepository.save(Category.createDefault(blog));
        socialAccountRepository.save(SocialAccount.link(user.getId(), profile.provider(), profile.providerUserId()));
        return user;
    }

    /** 아이디(/@아이디)는 나중에 바꿀 수 없어서, 깃허브 로그인 이름처럼 규칙에 맞는 후보가 있으면 쓰고 아니면 kakao_x7k2m9처럼 만들어요 */
    String newUsername(SocialProfile profile) {
        if (profile.usernameHint() != null) {
            String hint = profile.usernameHint().toLowerCase(Locale.ROOT).replace('-', '_');
            if (isAvailable(hint)) {
                return hint;
            }
        }
        for (int i = 0; i < 20; i++) {
            String candidate = profile.provider().id() + "_" + random(6);
            if (isAvailable(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("아이디를 만들지 못했어요");
    }

    private boolean isAvailable(String username) {
        return UsernamePolicy.isValidFormat(username) && !UsernamePolicy.isReserved(username)
                && !userRepository.existsByUsername(username);
    }

    /** 닉네임은 20자까지예요 (이모지 같은 문자가 반으로 잘리지 않게 코드 포인트 기준) */
    static String nickname(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_NICKNAME;
        }
        String s = raw.strip();
        return s.codePointCount(0, s.length()) <= 20 ? s : s.substring(0, s.offsetByCodePoints(0, 20));
    }

    private static String random(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM_CHARS.charAt(RANDOM.nextInt(RANDOM_CHARS.length())));
        }
        return sb.toString();
    }
}

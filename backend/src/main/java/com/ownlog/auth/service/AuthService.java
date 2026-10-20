package com.ownlog.auth.service;

import com.ownlog.auth.dto.LoginRequest;
import com.ownlog.auth.dto.LoginResponse;
import com.ownlog.auth.dto.SignupRequest;
import com.ownlog.auth.dto.SignupResponse;
import com.ownlog.auth.dto.UsernameCheckResponse;
import com.ownlog.auth.dto.UsernameCheckResponse.Reason;
import com.ownlog.auth.oauth.OAuthLoginCodeStore;
import com.ownlog.blog.domain.Blog;
import com.ownlog.blog.repository.BlogRepository;
import com.ownlog.category.domain.Category;
import com.ownlog.category.repository.CategoryRepository;
import com.ownlog.global.error.BusinessException;
import com.ownlog.global.error.ErrorCode;
import com.ownlog.global.security.JwtTokenProvider;
import com.ownlog.user.domain.User;
import com.ownlog.user.domain.UsernamePolicy;
import com.ownlog.user.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * 존재하지 않는 이메일일 때도 BCrypt 비교를 한 번 실행하기 위한 가짜 해시예요.
     * 그러지 않으면 "없는 이메일"은 BCrypt를 건너뛰어서 응답이 눈에 띄게 빨라지고,
     * 응답 시간만 재 봐도 가입 여부를 알아낼 수 있어요 (타이밍 공격, NF-06).
     */
    private static final String DUMMY_PASSWORD_HASH = new BCryptPasswordEncoder().encode("ownlog-dummy-password");

    private final UserRepository userRepository;
    private final BlogRepository blogRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final OAuthLoginCodeStore oauthLoginCodeStore;

    /**
     * 회원가입 (U-01, U-02). 회원 → 블로그 → "미분류"를 한 트랜잭션으로 만들어요.
     * 중간에 하나라도 실패하면 앞에서 저장한 것까지 모두 롤백돼요.
     */
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }
        if (UsernamePolicy.isReserved(request.username())) {
            throw new BusinessException(ErrorCode.RESERVED_USERNAME);
        }
        String email = User.normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.DUPLICATE_USERNAME);
        }

        User user = userRepository.save(User.create(
                email, passwordEncoder.encode(request.password()),
                request.username(), request.nickname().strip()));
        Blog blog = blogRepository.save(Blog.createDefault(user));
        categoryRepository.save(Category.createDefault(blog));

        return new SignupResponse(user.getId(), user.getUsername());
    }

    /** 로그인 (U-03). 이메일이 없든, 탈퇴했든, 비밀번호가 틀렸든 같은 에러예요. */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Optional<User> found = userRepository.findByEmail(User.normalizeEmail(request.email()))
                .filter(User::isActive);

        String hash = found.map(User::getPassword).orElse(DUMMY_PASSWORD_HASH);
        boolean matches = passwordEncoder.matches(request.password(), hash);   // 항상 한 번 실행
        if (found.isEmpty() || !matches) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        return issueToken(found.get());
    }

    /** API 4.10 소셜 로그인 교환: 소셜 로그인이 끝나고 받은 1회용 코드 → 이메일 로그인과 같은 응답 (코드를 지우므로 쓰기 트랜잭션) */
    @Transactional
    public LoginResponse exchangeOAuthCode(String code) {
        User user = oauthLoginCodeStore.consume(code)
                .flatMap(userRepository::findById)
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_OAUTH_CODE));
        return issueToken(user);
    }

    private LoginResponse issueToken(User user) {
        Blog blog = blogRepository.findFirstByUserIdOrderByIdAsc(user.getId())
                .orElseThrow(() -> new IllegalStateException("블로그가 없는 회원: " + user.getId()));
        return LoginResponse.of(tokenProvider.createAccessToken(user.getId()),
                tokenProvider.getValiditySeconds(), user, blog.getId());
    }

    /** 아이디 사용 가능 여부 (API 4.3). 형식 → 예약어 → 중복 순서로 확인해요. */
    @Transactional(readOnly = true)
    public UsernameCheckResponse checkUsername(String username) {
        if (!UsernamePolicy.isValidFormat(username)) {
            return UsernameCheckResponse.unavailable(Reason.INVALID_FORMAT);
        }
        if (UsernamePolicy.isReserved(username)) {
            return UsernameCheckResponse.unavailable(Reason.RESERVED);
        }
        if (userRepository.existsByUsername(username)) {
            return UsernameCheckResponse.unavailable(Reason.DUPLICATE);
        }
        return UsernameCheckResponse.ok();
    }
}

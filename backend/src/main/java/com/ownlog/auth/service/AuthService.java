package com.ownlog.auth.service;

import com.ownlog.auth.dto.SignupRequest;
import com.ownlog.auth.dto.SignupResponse;
import com.ownlog.auth.dto.UsernameCheckResponse;
import com.ownlog.auth.dto.UsernameCheckResponse.Reason;
import com.ownlog.blog.domain.Blog;
import com.ownlog.blog.repository.BlogRepository;
import com.ownlog.category.domain.Category;
import com.ownlog.category.repository.CategoryRepository;
import com.ownlog.global.error.BusinessException;
import com.ownlog.global.error.ErrorCode;
import com.ownlog.user.domain.User;
import com.ownlog.user.domain.UsernamePolicy;
import com.ownlog.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final BlogRepository blogRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;

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

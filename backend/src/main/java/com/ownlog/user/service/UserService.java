package com.ownlog.user.service;

import com.ownlog.blog.domain.Blog;
import com.ownlog.blog.repository.BlogRepository;
import com.ownlog.global.error.BusinessException;
import com.ownlog.global.error.ErrorCode;
import com.ownlog.user.domain.User;
import com.ownlog.user.dto.MeResponse;
import com.ownlog.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BlogRepository blogRepository;

    /** API 4.4 내 정보 */
    @Transactional(readOnly = true)
    public MeResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Blog blog = blogRepository.findFirstByUserIdOrderByIdAsc(userId)
                .orElseThrow(() -> new IllegalStateException("블로그가 없는 회원: " + userId));
        return MeResponse.of(user, blog);
    }
}

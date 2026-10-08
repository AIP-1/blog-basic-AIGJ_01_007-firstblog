package com.ownlog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.ownlog.IntegrationTest;
import com.ownlog.auth.dto.SignupRequest;
import com.ownlog.auth.service.AuthService;
import com.ownlog.blog.repository.BlogRepository;
import com.ownlog.category.repository.CategoryRepository;
import com.ownlog.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 📚 트랜잭션 학습용: 회원가입 중 마지막 단계가 실패하면 앞에서 저장한 것까지 모두 롤백되는지 확인해요.
 * AuthService.signup()의 @Transactional을 지우고 돌려 보면 회원과 블로그가 남아서 이 테스트가 실패해요.
 */
class SignupTransactionTest extends IntegrationTest {

    @MockitoBean CategoryRepository categoryRepository;   // 진짜 대신 가짜로 바꿔서 실패를 일으켜요

    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired BlogRepository blogRepository;

    @Test
    @DisplayName("기본 카테고리 저장이 실패하면 회원과 블로그도 롤백된다")
    void rollbackWhenDefaultCategoryFails() {
        doThrow(new RuntimeException("강제 실패")).when(categoryRepository).save(any());

        SignupRequest request = new SignupRequest(
                "rollback@gmail.com", "ownlog1234", "ownlog1234", "rollback", "롤백");

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("강제 실패");

        assertThat(userRepository.count()).isZero();   // 1단계에서 저장한 회원도 사라져야 해요
        assertThat(blogRepository.count()).isZero();   // 2단계에서 저장한 블로그도 사라져야 해요
    }
}

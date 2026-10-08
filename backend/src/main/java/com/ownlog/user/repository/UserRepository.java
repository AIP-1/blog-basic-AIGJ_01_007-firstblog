package com.ownlog.user.repository;

import com.ownlog.user.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** 로그인 (status는 서비스에서 확인해요) */
    Optional<User> findByEmail(String email);

    /** 가입 시 이메일 중복 확인 */
    boolean existsByEmail(String email);

    /** 아이디 확인. 탈퇴 회원도 포함해서 아이디 재사용을 막아요. */
    boolean existsByUsername(String username);
}

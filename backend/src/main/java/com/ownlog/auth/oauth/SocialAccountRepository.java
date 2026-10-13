package com.ownlog.auth.oauth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

    /** 탈퇴하면 연결을 끊어요. 같은 소셜 계정으로 다시 오면 새 회원으로 가입돼요 */
    @Modifying
    @Query("delete from SocialAccount s where s.userId = :userId")
    void deleteByUserId(Long userId);
}

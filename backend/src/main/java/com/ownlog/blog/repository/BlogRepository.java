package com.ownlog.blog.repository;

import com.ownlog.blog.domain.Blog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BlogRepository extends JpaRepository<Blog, Long> {

    /** /@{username} → 블로그. 탈퇴 회원은 제외하고, 주인 정보도 함께 가져와요 (N+1 방지). */
    @Query("""
            select b from Blog b join fetch b.user u
            where u.username = :username and u.status = com.ownlog.user.domain.UserStatus.ACTIVE
            order by b.id
            """)
    List<Blog> findActiveByUsername(String username);  // MVP는 1개. 다중 블로그가 되면 첫 번째가 대표 블로그

    /** 내 블로그 (토큰의 회원 id로) */
    Optional<Blog> findFirstByUserIdOrderByIdAsc(Long userId);
}

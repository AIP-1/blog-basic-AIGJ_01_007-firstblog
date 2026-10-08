package com.ownlog.tag.repository;

import com.ownlog.tag.domain.Tag;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TagRepository extends JpaRepository<Tag, Long> {

    /** 없으면 만들고, 있으면 아무것도 안 해요. 동시에 같은 새 태그를 만들어도 에러가 나지 않아요 (ERD 3.5) */
    @Modifying
    @Query(value = """
            INSERT INTO tags (name, created_at) VALUES (:name, now())
            ON CONFLICT ((lower(name))) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(String name);

    /** 대소문자 무시 조회 (uk_tags_name_lower 인덱스를 써요) */
    @Query(value = "SELECT * FROM tags WHERE lower(name) = lower(:name)", nativeQuery = true)
    Optional<Tag> findByNameIgnoreCase(String name);
}

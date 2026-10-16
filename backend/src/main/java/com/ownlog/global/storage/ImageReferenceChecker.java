package com.ownlog.global.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 업로드 파일을 아직 누가 쓰고 있는지 확인해요.
 * 업로드한 사람을 따로 기록하지 않아서, 프로필 사진 주소에 남의 이미지 주소를 넣은 뒤 바꾸면
 * 남의 파일이 지워질 수 있었어요. 지우기 전에 다른 회원 프로필이나 글(썸네일·본문)이 쓰고 있으면 지우지 않아요.
 */
@Component
@RequiredArgsConstructor
public class ImageReferenceChecker {

    private final JdbcTemplate jdbcTemplate;

    /** userId 본인의 프로필 말고 이 주소를 쓰는 곳이 있나 (같은 트랜잭션에서 지운 글은 이미 빠져 있어요) */
    public boolean isUsedElsewhere(String url, Long userId) {
        Boolean used = jdbcTemplate.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM users WHERE profile_image_url = ? AND id <> ?)
                    OR EXISTS (SELECT 1 FROM posts WHERE thumbnail_url = ? OR content LIKE ? ESCAPE '\\')
                """, Boolean.class, url, userId, url, likePattern(url));
        return Boolean.TRUE.equals(used);
    }

    /**
     * 이 주소를 쓰는 곳이 하나라도 있나 (글을 지우거나 고칠 때, 정리 배치).
     * isUsedElsewhere와 달리 내 프로필 사진도 "쓰는 곳"으로 쳐요. 그러지 않으면 프로필로도 쓰는 사진을 글에서 빼는 순간
     * 프로필 사진 파일이 지워져요.
     * JdbcTemplate은 DB에 실제로 반영된 것만 보므로, 같은 트랜잭션의 JPA 변경은 부르기 전에 flush해야 해요 (UnusedImageCleaner).
     */
    public boolean isUsedAnywhere(String url) {
        Boolean used = jdbcTemplate.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM users WHERE profile_image_url = ?)
                    OR EXISTS (SELECT 1 FROM posts WHERE thumbnail_url = ? OR content LIKE ? ESCAPE '\\')
                """, Boolean.class, url, url, likePattern(url));
        return Boolean.TRUE.equals(used);
    }

    /** LIKE의 특수문자(\\, %, _)가 주소에 있어도 글자 그대로 찾게 이스케이프해요 */
    private static String likePattern(String url) {
        return "%" + url.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}

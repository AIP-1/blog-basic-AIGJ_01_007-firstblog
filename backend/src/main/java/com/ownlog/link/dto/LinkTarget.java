package com.ownlog.link.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

/**
 * 글 상세의 linkedPosts 값 (LK-02). 볼 수 없는 글이면 제목과 주인을 주지 않아요.
 * summary·thumbnailUrl·nickname·publishedAt은 링크에 마우스를 올렸을 때 보여줄 미리보기 카드용이에요 (LK-06).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LinkTarget(boolean accessible, String username, String title,
                         String nickname, String summary, String thumbnailUrl, OffsetDateTime publishedAt) {

    public static final LinkTarget INACCESSIBLE = new LinkTarget(false, null, null, null, null, null, null);
}

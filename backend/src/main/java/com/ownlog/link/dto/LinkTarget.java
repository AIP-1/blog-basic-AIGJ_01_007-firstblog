package com.ownlog.link.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 글 상세의 linkedPosts 값 (LK-02). 볼 수 없는 글이면 제목과 주인을 주지 않아요 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LinkTarget(boolean accessible, String username, String title) {

    public static final LinkTarget INACCESSIBLE = new LinkTarget(false, null, null);
}

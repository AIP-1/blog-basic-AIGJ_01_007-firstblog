package com.ownlog.post.dto;

import com.ownlog.global.util.KstTime;
import com.ownlog.post.domain.Post;
import java.time.OffsetDateTime;

/** API 7.1, 7.2 응답. 화면에 "임시저장됨 (HH:mm)"으로 보여줘요 (D-07) */
public record DraftSaveResponse(Long id, OffsetDateTime updatedAt) {

    public static DraftSaveResponse from(Post draft) {
        return new DraftSaveResponse(draft.getId(), KstTime.of(draft.getUpdatedAt()));
    }
}

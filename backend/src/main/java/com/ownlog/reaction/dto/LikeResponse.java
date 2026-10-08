package com.ownlog.reaction.dto;

/** API 9.1 응답. likeCount는 DB에서 방금 바뀐 값을 다시 읽은 거예요 */
public record LikeResponse(boolean liked, int likeCount) {
}

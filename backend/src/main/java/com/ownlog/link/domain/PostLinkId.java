package com.ownlog.link.domain;

import java.io.Serializable;

/** post_links 복합 PK */
public record PostLinkId(Long sourcePostId, Long targetPostId) implements Serializable {

    public PostLinkId() {
        this(null, null);
    }
}

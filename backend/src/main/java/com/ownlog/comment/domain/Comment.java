package com.ownlog.comment.domain;

import com.ownlog.global.entity.BaseTimeEntity;
import com.ownlog.post.domain.Post;
import com.ownlog.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 댓글과 답글 (ERD 3.6). parent가 null이면 일반 댓글이에요. */
@Getter
@Entity
@Table(name = "comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseTimeEntity {

    public static final int MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    @Column(nullable = false, length = MAX_LENGTH)
    private String content;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    public static Comment create(Post post, User user, Comment parent, String content) {
        Comment comment = new Comment();
        comment.post = post;
        comment.user = user;
        comment.parent = parent;
        comment.content = content;
        return comment;
    }

    public void edit(String content) {
        this.content = content;
    }

    /**
     * "삭제된 댓글입니다"로 표시 (답글이 남아 있는 일반 댓글).
     * 내용은 DB에 남겨 둬요. 이 댓글에 들어온 신고를 2차 관리자 기능에서 확인할 수 있게요.
     * API 응답에는 절대 내보내지 않아요.
     */
    public void markDeleted() {
        this.isDeleted = true;
    }

    public boolean isReply() {
        return parent != null;
    }

    public boolean isWrittenBy(Long userId) {
        return user.getId().equals(userId);
    }

    /** 화면의 "(수정됨)": 만들 때는 createdAt과 updatedAt이 같고, 수정하면 updatedAt이 뒤로 가요 */
    public boolean isEdited() {
        return getUpdatedAt().isAfter(getCreatedAt());
    }
}

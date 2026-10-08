package com.ownlog.comment.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import com.ownlog.comment.domain.Comment;
import com.ownlog.comment.dto.CommentListResponse;
import com.ownlog.comment.dto.CommentRequest;
import com.ownlog.comment.dto.CommentResponse;
import com.ownlog.comment.dto.CommentUpdateRequest;
import com.ownlog.comment.repository.CommentRepository;
import com.ownlog.global.error.BusinessException;
import com.ownlog.global.error.ErrorCode;
import com.ownlog.global.security.LoginUser;
import com.ownlog.post.domain.Post;
import com.ownlog.post.service.PostQueryService;
import com.ownlog.user.repository.UserRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 댓글 (API 8.1 ~ 8.4, C-01, C-02) */
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final PostQueryService postQueryService;

    /** API 8.1 댓글 전체: SQL 정렬 결과를 위에서부터 훑으며 부모별로 답글을 묶기만 해요 */
    @Transactional(readOnly = true)
    public CommentListResponse getComments(Long postId, LoginUser me) {
        Post post = postQueryService.getViewablePost(postId, me);
        Long viewerId = me == null ? null : me.id();
        Long postOwnerId = postOwnerId(post);

        List<Comment> comments = commentRepository.findAllForPost(postId);   // 이미 "부모 → 그 답글들" 순서
        Map<Long, List<Comment>> repliesByParent = comments.stream()
                .filter(Comment::isReply)
                .collect(groupingBy(reply -> reply.getParent().getId(), LinkedHashMap::new, toList()));

        List<CommentResponse> result = comments.stream()
                .filter(comment -> !comment.isReply())
                .map(parent -> CommentResponse.of(parent, viewerId, postOwnerId,
                        repliesByParent.getOrDefault(parent.getId(), List.of()).stream()
                                .map(reply -> CommentResponse.of(reply, viewerId, postOwnerId, null))
                                .toList()))
                .toList();

        long totalCount = comments.stream().filter(comment -> !comment.isDeleted()).count();
        return new CommentListResponse(totalCount, result);
    }

    /** API 8.2 댓글·답글 작성 */
    @Transactional
    public CommentResponse create(Long postId, LoginUser me, CommentRequest request) {
        Post post = postQueryService.getViewablePost(postId, me);
        Comment parent = request.parentId() == null ? null : getReplyableParent(request.parentId(), postId);

        // 회원은 조회하지 않고 id만 가진 프록시로 연결해요 (user_id만 있으면 되니까 SELECT를 아껴요)
        Comment saved = commentRepository.save(Comment.create(
                post, userRepository.getReferenceById(me.id()), parent, request.content().strip()));
        return CommentResponse.of(saved, me.id(), postOwnerId(post), parent == null ? List.of() : null);
    }

    /** API 8.3 수정: 작성자만 (블로그 주인도 남의 댓글은 수정할 수 없어요) */
    @Transactional
    public CommentResponse update(Long commentId, LoginUser me, CommentUpdateRequest request) {
        Comment comment = getActiveComment(commentId, me);
        if (!comment.isWrittenBy(me.id())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        comment.edit(request.content().strip());
        commentRepository.flush();   // updatedAt이 갱신돼야 isEdited가 true로 나와요
        return CommentResponse.of(comment, me.id(), postOwnerId(comment.getPost()), null);
    }

    /** API 8.4 삭제: 작성자 또는 글 주인 */
    @Transactional
    public void delete(Long commentId, LoginUser me) {
        Comment comment = getActiveComment(commentId, me);
        if (!comment.isWrittenBy(me.id()) && !me.id().equals(postOwnerId(comment.getPost()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (!comment.isReply() && commentRepository.existsByParentId(commentId)) {
            comment.markDeleted();   // 답글이 있으면 "삭제된 댓글입니다"
            return;
        }

        Comment parent = comment.getParent();
        hardDelete(comment);
        // 마지막 답글이 지워졌고 부모가 "삭제된 댓글입니다"라면 부모도 정리해요 (ERD 5장)
        // existsByParentId 실행 직전에 위의 DELETE가 먼저 DB로 나가서(FlushMode.AUTO) 방금 지운 답글은 세지 않아요
        if (parent != null && parent.isDeleted() && !commentRepository.existsByParentId(parent.getId())) {
            hardDelete(parent);
        }
    }

    /** 답글을 달 수 있는 부모: 같은 글의, 삭제되지 않은, 일반 댓글 (C-02) */
    private Comment getReplyableParent(Long parentId, Long postId) {
        return commentRepository.findById(parentId)
                .filter(parent -> parent.getPost().getId().equals(postId))
                .filter(parent -> !parent.isReply())
                .filter(parent -> !parent.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PARENT_COMMENT));
    }

    /** 수정·삭제 대상: 없거나 "삭제된 댓글입니다"면 404, 볼 수 없는 글의 댓글이면 POST_NOT_FOUND */
    private Comment getActiveComment(Long commentId, LoginUser me) {
        Comment comment = commentRepository.findById(commentId)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        postQueryService.getViewablePost(comment.getPost().getId(), me);
        return comment;
    }

    private void hardDelete(Comment comment) {
        commentRepository.deleteReportsOf(comment.getId());
        commentRepository.delete(comment);
    }

    private Long postOwnerId(Post post) {
        return post.getBlog().getUser().getId();
    }
}

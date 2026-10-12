package com.ownlog.post.domain;

import com.ownlog.blog.domain.Blog;
import com.ownlog.category.domain.Category;
import com.ownlog.global.common.Visibility;
import com.ownlog.global.entity.BaseTimeEntity;
import com.ownlog.global.util.MarkdownText;
import com.ownlog.tag.domain.Tag;
import com.ownlog.topic.domain.Topic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 글과 임시저장 글 (ERD 3.4). status로 구분해요. */
@Getter
@Entity
@Table(name = "posts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blog_id", nullable = false)
    private Blog blog;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")   // null = 주제 없음 (T-02)
    private Topic topic;

    @Column(length = 100)
    private String title;

    @Column(columnDefinition = "text")
    private String content;

    @Column(length = 200)
    private String summary;

    @Column(length = 500)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility;

    /**
     * DB가 직접 더해요 (SET view_count = view_count + 1).
     * Hibernate는 수정할 때 모든 컬럼을 UPDATE해서, 막지 않으면 예전 값으로 덮어써요 (Lost Update).
     */
    @Column(nullable = false, insertable = false, updatable = false)
    private int viewCount;

    @Column(nullable = false, insertable = false, updatable = false)
    private int likeCount;

    /** 화면의 작성일 (처음 발행한 시각). 타임캡슐이면 개봉 시각이에요 (공개 조건이 이 값으로 봉인 글을 걸러요) */
    private Instant publishedAt;

    /** 타임캡슐을 봉인한(실제로 쓴) 시각. null이면 일반 글이에요 (TC-01) */
    private Instant sealedAt;

    /** 화면의 수정일 (발행 후 제목·본문·썸네일·태그를 바꾼 시각) */
    private Instant editedAt;

    @ManyToMany
    @JoinTable(name = "post_tags",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new HashSet<>();

    /** 바로 발행 (P-01). openAt이 있으면 그 시각에 열리는 타임캡슐로 발행해요 (TC-01) */
    public static Post publish(Blog blog, Category category, Topic topic, String title, String content,
                               String thumbnailUrl, Visibility visibility, Set<Tag> tags, Instant openAt) {
        Post post = new Post();
        post.blog = blog;
        post.apply(category, topic, title, content, thumbnailUrl, visibility, tags);
        post.markPublished(openAt);
        return post;
    }

    /** 처음 임시저장 (D-01). 제목과 본문이 비어 있어도 돼요 (D-03) */
    public static Post createDraft(Blog blog, Category category, Topic topic, String title, String content,
                                   String thumbnailUrl, Visibility visibility, Set<Tag> tags) {
        Post post = new Post();
        post.blog = blog;
        post.status = PostStatus.DRAFT;
        post.apply(category, topic, title, content, thumbnailUrl, visibility, tags);
        return post;
    }

    /** 다시 임시저장: 덮어써요 (D-02) */
    public void saveDraft(Category category, Topic topic, String title, String content,
                          String thumbnailUrl, Visibility visibility, Set<Tag> tags) {
        requireDraft();
        apply(category, topic, title, content, thumbnailUrl, visibility, tags);
    }

    /** 임시저장 글 발행 (D-05): 화면의 최신 내용을 저장하면서 발행해요. 작성일은 발행한 시각이에요 */
    public void publishDraft(Category category, Topic topic, String title, String content,
                             String thumbnailUrl, Visibility visibility, Set<Tag> tags, Instant openAt) {
        requireDraft();
        apply(category, topic, title, content, thumbnailUrl, visibility, tags);
        markPublished(openAt);
    }

    /** 일반 글은 지금 공개, 타임캡슐은 지금 봉인하고 openAt에 공개 */
    private void markPublished(Instant openAt) {
        Instant now = Instant.now();
        this.status = PostStatus.PUBLISHED;
        this.sealedAt = openAt == null ? null : now;
        this.publishedAt = openAt == null ? now : openAt;
    }

    private void requireDraft() {
        if (status != PostStatus.DRAFT) {
            throw new IllegalStateException("임시저장 글이 아니에요: " + id);   // 서비스가 먼저 걸러서 생길 수 없는 상황
        }
    }

    /** 발행 글 수정 (P-04). 제목·본문·썸네일·태그가 바뀌었을 때만 수정일을 갱신해요 (P-05) */
    public void edit(Category category, Topic topic, String title, String content,
                     String thumbnailUrl, Visibility visibility, Set<Tag> tags) {
        boolean contentChanged = !Objects.equals(this.title, title)
                || !Objects.equals(this.content, content)
                || !Objects.equals(this.thumbnailUrl, resolveThumbnail(thumbnailUrl, content))
                || !tagNames(this.tags).equals(tagNames(tags));

        // 카테고리·토픽·공개 범위만 바뀐 건 수정으로 보지 않아요
        apply(category, topic, title, content, thumbnailUrl, visibility, tags);
        if (contentChanged) {
            this.editedAt = Instant.now();
        }
    }

    private void apply(Category category, Topic topic, String title, String content,
                       String thumbnailUrl, Visibility visibility, Set<Tag> tags) {
        this.category = category;
        this.topic = topic;
        this.title = title;
        this.content = content;
        this.summary = MarkdownText.summarize(content);               // 카드용 요약 (저장할 때 한 번만)
        this.thumbnailUrl = resolveThumbnail(thumbnailUrl, content);  // 없으면 본문 첫 이미지 (P-02)
        this.visibility = visibility;
        this.tags.clear();
        this.tags.addAll(tags);
    }

    private static String resolveThumbnail(String thumbnailUrl, String content) {
        return thumbnailUrl != null ? thumbnailUrl : MarkdownText.firstImageUrl(content);
    }

    private static Set<String> tagNames(Set<Tag> tags) {
        return tags.stream()
                .map(tag -> tag.getName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    public boolean isOwnedBy(Long userId) {
        return blog.isOwnedBy(userId);
    }

    public boolean isPublished() {
        return status == PostStatus.PUBLISHED;
    }

    /** 방문자가 볼 수 있는 글인가 (ERD 4.1): 발행 + 글 공개 + 카테고리 공개 + 봉인 중이 아님 (TC-03) */
    public boolean isVisibleToPublic() {
        return isPublicExceptSeal() && !isSealed();
    }

    /** 봉인만 아니면 공개될 글인가. 봉인된 공개 캡슐은 방문자에게 잠금 카드로 보여줘요 (TC-03) */
    public boolean isPublicExceptSeal() {
        return isPublished() && visibility == Visibility.PUBLIC && !category.isPrivate();
    }

    public boolean isTimeCapsule() {
        return sealedAt != null;
    }

    /** 아직 열리지 않은 타임캡슐인가 (TC-05: 개봉 시각이 지나면 저절로 false) */
    public boolean isSealed() {
        return isTimeCapsule() && publishedAt.isAfter(Instant.now());
    }
}

-- =====================================================================
-- OwnLog 초기 스키마 (docs/ERD.md 기준)
-- 테이블 11개: users, blogs, categories, topics, posts, tags, post_tags,
--              comments, post_likes, bookmarks, reports
-- 규칙
--   - Enum 값은 VARCHAR로 저장해요 (JPA @Enumerated(STRING))
--   - 시간은 TIMESTAMPTZ로 저장해요 (Java는 Instant, API 응답은 +09:00로 변환)
--   - FK에 ON DELETE CASCADE를 쓰지 않아요. 삭제는 서비스에서 순서대로 해요 (ERD 5장)
--   - 부분 인덱스, 함수 인덱스, CHECK 제약은 JPA로 만들 수 없어서 여기에만 있어요
-- =====================================================================


-- ---------------------------------------------------------------------
-- users (회원) · ERD 3.1
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id                BIGSERIAL    PRIMARY KEY,
    email             VARCHAR(255) NOT NULL,  -- 소문자로 저장, 탈퇴 시 deleted_{id}@ownlog.invalid
    password          VARCHAR(255) NOT NULL,  -- BCrypt 해시
    username          VARCHAR(20)  NOT NULL,  -- 아이디 = /@아이디, 탈퇴해도 재사용 불가
    nickname          VARCHAR(20)  NOT NULL,
    bio               VARCHAR(100),
    profile_image_url VARCHAR(500),
    role              VARCHAR(20)  NOT NULL DEFAULT 'USER',    -- USER / ADMIN
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE / DELETED
    created_at        TIMESTAMPTZ    NOT NULL,
    updated_at        TIMESTAMPTZ    NOT NULL,
    deleted_at        TIMESTAMPTZ,

    CONSTRAINT uk_users_email    UNIQUE (email),
    CONSTRAINT uk_users_username UNIQUE (username)
);


-- ---------------------------------------------------------------------
-- blogs (블로그) · ERD 3.2
-- user_id에 UNIQUE를 걸지 않아요 → DB는 1:N, 1인 1블로그는 서비스에서 제한
-- ---------------------------------------------------------------------
CREATE TABLE blogs (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(200),
    created_at  TIMESTAMPTZ    NOT NULL,
    updated_at  TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_blogs_user_id ON blogs (user_id);


-- ---------------------------------------------------------------------
-- categories (카테고리) · ERD 3.3
-- ---------------------------------------------------------------------
CREATE TABLE categories (
    id            BIGSERIAL   PRIMARY KEY,
    blog_id       BIGINT      NOT NULL REFERENCES blogs (id),
    name          VARCHAR(30) NOT NULL,
    display_order INT         NOT NULL DEFAULT 0,
    is_default    BOOLEAN     NOT NULL DEFAULT false,     -- "미분류"만 true
    visibility    VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',  -- PUBLIC / PRIVATE
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,

    CONSTRAINT uk_categories_blog_name UNIQUE (blog_id, name)
);

-- 블로그마다 기본 카테고리는 딱 1개 (부분 UNIQUE 인덱스)
CREATE UNIQUE INDEX uk_categories_blog_default
    ON categories (blog_id) WHERE is_default = true;


-- ---------------------------------------------------------------------
-- topics (토픽, 서비스 주제 분류) · ERD 3.9
-- ---------------------------------------------------------------------
CREATE TABLE topics (
    id            BIGSERIAL   PRIMARY KEY,
    slug          VARCHAR(30) NOT NULL,  -- URL용, 바꾸지 않아요
    name          VARCHAR(30) NOT NULL,
    display_order INT         NOT NULL DEFAULT 0,

    CONSTRAINT uk_topics_slug UNIQUE (slug),
    CONSTRAINT uk_topics_name UNIQUE (name)
);


-- ---------------------------------------------------------------------
-- posts (글과 임시저장 글) · ERD 3.4
-- ---------------------------------------------------------------------
CREATE TABLE posts (
    id            BIGSERIAL    PRIMARY KEY,
    blog_id       BIGINT       NOT NULL REFERENCES blogs (id),
    category_id   BIGINT       NOT NULL REFERENCES categories (id),
    topic_id      BIGINT       REFERENCES topics (id),     -- NULL = 주제 없음
    title         VARCHAR(100),                            -- 임시저장 때문에 NULL 허용
    content       TEXT,                                    -- 마크다운 원문, 최대 100,000자(서비스 검증)
    summary       VARCHAR(200),
    thumbnail_url VARCHAR(500),
    status        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',   -- DRAFT / PUBLISHED
    visibility    VARCHAR(20)  NOT NULL DEFAULT 'PUBLIC',  -- PUBLIC / PRIVATE
    view_count    INT          NOT NULL DEFAULT 0,
    like_count    INT          NOT NULL DEFAULT 0,
    published_at  TIMESTAMPTZ,                               -- 화면의 작성일
    edited_at     TIMESTAMPTZ,                               -- 화면의 수정일
    created_at    TIMESTAMPTZ    NOT NULL,
    updated_at    TIMESTAMPTZ    NOT NULL,

    -- 발행 글은 제목, 본문, 발행 시각이 반드시 있어야 해요
    CONSTRAINT ck_posts_published_required CHECK (
        status = 'DRAFT'
        OR (title IS NOT NULL AND content IS NOT NULL AND published_at IS NOT NULL)
    )
);

CREATE INDEX idx_posts_public_latest   ON posts (status, visibility, published_at DESC);            -- S-01, S-05
CREATE INDEX idx_posts_blog            ON posts (blog_id, status, published_at DESC);               -- B-03
CREATE INDEX idx_posts_blog_category   ON posts (blog_id, category_id, status, published_at DESC);  -- S-07, 카테고리 삭제
CREATE INDEX idx_posts_popular         ON posts (published_at, like_count DESC);                    -- S-02
CREATE INDEX idx_posts_topic           ON posts (topic_id, status, visibility, published_at DESC);  -- T-03


-- ---------------------------------------------------------------------
-- tags / post_tags (태그, N:M) · ERD 3.5
-- ---------------------------------------------------------------------
CREATE TABLE tags (
    id         BIGSERIAL   PRIMARY KEY,
    name       VARCHAR(20) NOT NULL,  -- 처음 만들어진 표기 그대로
    created_at TIMESTAMPTZ   NOT NULL
);

-- 대소문자 무시 UNIQUE (Java = java = JAVA)
CREATE UNIQUE INDEX uk_tags_name_lower ON tags (LOWER(name));

CREATE TABLE post_tags (
    post_id BIGINT NOT NULL REFERENCES posts (id),
    tag_id  BIGINT NOT NULL REFERENCES tags (id),

    PRIMARY KEY (post_id, tag_id)
);

CREATE INDEX idx_post_tags_tag_id ON post_tags (tag_id);  -- S-06


-- ---------------------------------------------------------------------
-- comments (댓글과 대댓글) · ERD 3.6
-- ---------------------------------------------------------------------
CREATE TABLE comments (
    id         BIGSERIAL     PRIMARY KEY,
    post_id    BIGINT        NOT NULL REFERENCES posts (id),
    user_id    BIGINT        NOT NULL REFERENCES users (id),
    parent_id  BIGINT        REFERENCES comments (id),  -- NULL = 일반 댓글, 깊이 1단계는 서비스 검증
    content    VARCHAR(1000) NOT NULL,                  -- 일반 텍스트
    is_deleted BOOLEAN       NOT NULL DEFAULT false,    -- "삭제된 댓글입니다"
    created_at TIMESTAMPTZ     NOT NULL,
    updated_at TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_comments_post ON comments (post_id, parent_id, created_at);


-- ---------------------------------------------------------------------
-- post_likes / bookmarks (좋아요, 북마크) · ERD 3.7
-- 복합 PK로 1인 1회를 보장해요
-- ---------------------------------------------------------------------
CREATE TABLE post_likes (
    user_id    BIGINT    NOT NULL REFERENCES users (id),
    post_id    BIGINT    NOT NULL REFERENCES posts (id),
    created_at TIMESTAMPTZ NOT NULL,

    PRIMARY KEY (user_id, post_id)
);

CREATE INDEX idx_post_likes_post_id ON post_likes (post_id);

CREATE TABLE bookmarks (
    user_id    BIGINT    NOT NULL REFERENCES users (id),
    post_id    BIGINT    NOT NULL REFERENCES posts (id),
    created_at TIMESTAMPTZ NOT NULL,

    PRIMARY KEY (user_id, post_id)
);

CREATE INDEX idx_bookmarks_post_id ON bookmarks (post_id);


-- ---------------------------------------------------------------------
-- reports (글 신고, 댓글 신고) · ERD 3.8
-- ---------------------------------------------------------------------
CREATE TABLE reports (
    id          BIGSERIAL    PRIMARY KEY,
    post_id     BIGINT       REFERENCES posts (id),     -- 글 신고일 때
    comment_id  BIGINT       REFERENCES comments (id),  -- 댓글 신고일 때
    reporter_id BIGINT       NOT NULL REFERENCES users (id),
    reason      VARCHAR(20)  NOT NULL,                  -- SPAM / ABUSE / ADULT / ETC
    detail      VARCHAR(500),
    status      VARCHAR(20)  NOT NULL DEFAULT 'RECEIVED',  -- RECEIVED / RESOLVED / REJECTED
    created_at  TIMESTAMPTZ    NOT NULL,

    -- 대상은 글 또는 댓글 중 정확히 하나
    CONSTRAINT ck_reports_one_target  CHECK ((post_id IS NULL) <> (comment_id IS NULL)),
    -- "기타" 사유는 상세 사유 필수
    CONSTRAINT ck_reports_etc_detail  CHECK (reason <> 'ETC' OR detail IS NOT NULL),
    -- 같은 대상 중복 신고 방지 (NULL끼리는 서로 다른 값으로 봐서 대상별로만 작동해요)
    CONSTRAINT uk_reports_post        UNIQUE (post_id, reporter_id),
    CONSTRAINT uk_reports_comment     UNIQUE (comment_id, reporter_id)
);


-- ---------------------------------------------------------------------
-- 토픽 초기 데이터 11개 · ERD 3.9
-- ---------------------------------------------------------------------
INSERT INTO topics (slug, name, display_order) VALUES
    ('it',      'IT·개발',     1),
    ('daily',   '일상',        2),
    ('travel',  '여행',        3),
    ('food',    '요리·맛집',   4),
    ('sports',  '스포츠',      5),
    ('game',    '게임',        6),
    ('movie',   '영화·드라마', 7),
    ('music',   '음악',        8),
    ('book',    '도서',        9),
    ('pet',     '반려동물',   10),
    ('finance', '경제·재테크', 11);

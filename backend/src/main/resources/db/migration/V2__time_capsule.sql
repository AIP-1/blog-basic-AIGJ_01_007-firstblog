-- 타임캡슐 글 (기록유니버스-설계.md 3장, TC-01 ~ TC-06)
-- 타임캡슐은 published_at에 "개봉 시각"(미래)을 넣고, 실제로 쓴 시각은 sealed_at에 남겨요.
-- 그래서 공개 조건에 "published_at <= 지금" 한 줄만 더하면 모든 목록에서 봉인 글이 빠져요.

ALTER TABLE posts ADD COLUMN sealed_at TIMESTAMPTZ;   -- NULL = 일반 글

-- 봉인 시각은 개봉 시각보다 앞이어야 해요
ALTER TABLE posts ADD CONSTRAINT ck_posts_capsule_order
    CHECK (sealed_at IS NULL OR (published_at IS NOT NULL AND sealed_at < published_at));

-- 블로그 홈 "곧 열리는 타임캡슐"(12.1), 메인 "최근 열린 타임캡슐"(12.2)
CREATE INDEX idx_posts_capsule ON posts (blog_id, published_at) WHERE sealed_at IS NOT NULL;
CREATE INDEX idx_posts_capsule_opened ON posts (published_at DESC) WHERE sealed_at IS NOT NULL;

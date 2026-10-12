-- 받는 사람이 있는 타임캡슐 (기록유니버스-설계.md 7.4, TC-07 ~ TC-10)
-- 보낸 사람과 받는 사람 둘만 보는 캡슐이라 항상 비공개(PRIVATE)예요. 그래서 공개 목록·검색·그래프에는 저절로 안 나와요.
ALTER TABLE posts ADD COLUMN recipient_id BIGINT REFERENCES users (id);   -- NULL = 받는 사람 없음

ALTER TABLE posts ADD CONSTRAINT ck_posts_recipient_capsule
    CHECK (recipient_id IS NULL OR (sealed_at IS NOT NULL AND visibility = 'PRIVATE'));

-- 받은 타임캡슐 목록 (12.3)
CREATE INDEX idx_posts_recipient ON posts (recipient_id) WHERE recipient_id IS NOT NULL;

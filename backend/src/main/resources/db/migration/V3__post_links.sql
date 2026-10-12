-- 글과 글의 연결 (기록유니버스-설계.md 4장, LK-01 ~ LK-05)
-- 본문의 [[글번호|표시 글자]]를 발행·수정할 때 분석해서 저장해요. 백링크("이 글을 언급한 글")와 기록 그래프가 써요.
CREATE TABLE post_links (
    source_post_id BIGINT      NOT NULL REFERENCES posts (id),   -- 링크를 건 글
    target_post_id BIGINT      NOT NULL REFERENCES posts (id),   -- 링크된 글
    created_at     TIMESTAMPTZ NOT NULL,

    PRIMARY KEY (source_post_id, target_post_id),
    CONSTRAINT ck_post_links_not_self CHECK (source_post_id <> target_post_id)
);

CREATE INDEX idx_post_links_target ON post_links (target_post_id);   -- 백링크 조회

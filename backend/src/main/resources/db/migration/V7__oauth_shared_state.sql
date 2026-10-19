-- =====================================================================
-- V7: 소셜 로그인 중간 상태를 DB에 둬요 (트러블슈팅 24번)
--
-- 블루-그린 배포 중에는 blue와 green이 동시에 떠 있어요. 로그인 중간 상태를 서버 메모리에 두면,
-- blue에서 시작한 로그인이 green으로 돌아왔을 때 green은 그 상태를 몰라서 실패해요.
-- 두 서버가 함께 쓰는 DB에 두면 어느 쪽으로 돌아와도 이어져요.
-- 무중단 배포 규칙대로 "추가만" 해요 (예전 버전은 이 테이블을 몰라도 그대로 동작).
-- =====================================================================

-- 동의 화면으로 보낼 때 만든 요청 정보. 돌아올 때 state로 찾고, 한 번 쓰면 지워요 (5분 유효)
CREATE TABLE oauth_authorization_requests (
    state       VARCHAR(255) PRIMARY KEY,   -- Spring이 만든 무작위 값 (같은 값이 브라우저 쿠키에도 있어야 해요)
    request     BYTEA        NOT NULL,      -- OAuth2AuthorizationRequest (서버가 쓰고 서버만 읽어요)
    expires_at  TIMESTAMPTZ  NOT NULL
);

-- 로그인 성공 후 화면에 넘기는 1회용 교환 코드 (60초 유효). 코드 원문이 아니라 SHA-256 해시만 저장해요
CREATE TABLE oauth_login_codes (
    code_hash   CHAR(64)     PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    expires_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_oauth_requests_expires ON oauth_authorization_requests (expires_at);
CREATE INDEX idx_oauth_codes_expires ON oauth_login_codes (expires_at);

-- =====================================================================
-- V6: 소셜 로그인 (카카오·네이버·구글·깃허브) · ERD 3.12
--
-- 무중단 배포와 함께 쓰려고 "추가만" 해요 (expand). 새 버전이 뜨는 동안 예전 버전도 같은 DB를 쓰는데,
-- 컬럼을 지우거나 이름을 바꾸면 예전 버전이 깨져요. 새 컬럼은 기본값이 있어서 예전 버전이 몰라도 괜찮아요.
-- =====================================================================

-- 비밀번호를 직접 정했는지. 소셜로만 가입한 회원은 FALSE → 비밀번호 변경 때 "현재 비밀번호"를 묻지 않아요
ALTER TABLE users ADD COLUMN password_set BOOLEAN NOT NULL DEFAULT TRUE;

-- 회원 ↔ 소셜 계정 연결. 한 서비스의 한 계정은 OwnLog 회원 한 명에게만 연결돼요
CREATE TABLE user_social_accounts (
    id               BIGSERIAL    PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users (id),
    provider         VARCHAR(20)  NOT NULL,   -- KAKAO / NAVER / GOOGLE / GITHUB
    provider_user_id VARCHAR(100) NOT NULL,   -- 그 서비스가 주는 회원 번호 (이메일은 바뀔 수 있어서 쓰지 않아요)
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uk_social_provider_user UNIQUE (provider, provider_user_id)
);
CREATE INDEX idx_social_user ON user_social_accounts (user_id);

package com.ownlog.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * API 명세서 3장의 에러 코드. code 값은 enum 이름 그대로 응답에 나가요.
 * message는 사용자에게 바로 보여줄 문장이라 내부 정보를 넣지 않아요 (NF-06).
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없어요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없어요."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식이에요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요."),

    // 회원
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일이에요."),
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "이미 사용 중인 아이디예요."),
    RESERVED_USERNAME(HttpStatus.BAD_REQUEST, "사용할 수 없는 아이디예요."),
    PASSWORD_CONFIRM_MISMATCH(HttpStatus.BAD_REQUEST, "비밀번호 확인이 일치하지 않아요."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않아요."),
    WRONG_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않아요."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자예요."),
    WRONG_CONFIRM_USERNAME(HttpStatus.BAD_REQUEST, "확인용 아이디가 올바르지 않아요."),
    INVALID_OAUTH_CODE(HttpStatus.BAD_REQUEST, "로그인 시간이 지났어요. 다시 시도해 주세요."),

    // 블로그 · 카테고리 · 토픽
    BLOG_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 블로그예요."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 카테고리예요."),
    INVALID_CATEGORY(HttpStatus.BAD_REQUEST, "선택할 수 없는 카테고리예요."),
    INVALID_TOPIC(HttpStatus.BAD_REQUEST, "존재하지 않는 토픽이에요."),
    DUPLICATE_CATEGORY_NAME(HttpStatus.CONFLICT, "같은 이름의 카테고리가 있어요."),
    CATEGORY_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "카테고리는 최대 30개까지 만들 수 있어요."),
    DEFAULT_CATEGORY_NOT_DELETABLE(HttpStatus.BAD_REQUEST, "기본 카테고리는 삭제할 수 없어요."),
    INVALID_CATEGORY_ORDER(HttpStatus.BAD_REQUEST, "카테고리 순서 정보가 올바르지 않아요."),

    // 글 · 임시저장
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 글이에요."),
    DRAFT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 임시저장 글이에요."),

    // 타임캡슐
    INVALID_OPEN_AT(HttpStatus.BAD_REQUEST, "열리는 시각은 1분 뒤부터 10년 안으로 정해 주세요."),
    TIME_CAPSULE_SEALED(HttpStatus.BAD_REQUEST, "봉인된 타임캡슐은 열리기 전까지 수정할 수 없어요."),
    INVALID_RECIPIENT(HttpStatus.BAD_REQUEST, "받는 사람을 찾을 수 없어요. 다른 회원의 아이디를 확인해 주세요."),
    RECIPIENT_REQUIRES_CAPSULE(HttpStatus.BAD_REQUEST, "받는 사람은 타임캡슐에만 정할 수 있어요."),

    // 댓글
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 댓글이에요."),
    INVALID_PARENT_COMMENT(HttpStatus.BAD_REQUEST, "이 댓글에는 답글을 달 수 없어요."),

    // 신고
    CANNOT_REPORT_OWN_CONTENT(HttpStatus.BAD_REQUEST, "본인의 글이나 댓글은 신고할 수 없어요."),
    CANNOT_REPORT_DELETED_COMMENT(HttpStatus.BAD_REQUEST, "삭제된 댓글은 신고할 수 없어요."),
    ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 신고했어요."),

    // 이미지
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "jpg, png, gif, webp 파일만 올릴 수 있어요."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "5MB 이하의 파일만 올릴 수 있어요.");

    private final HttpStatus status;
    private final String message;
}

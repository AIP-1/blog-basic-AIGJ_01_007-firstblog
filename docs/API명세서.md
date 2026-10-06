# OwnLog REST API 명세서 (MVP)

> - 작성일: 2026-10-06
> - 기준 문서: [요구사항.md](../요구사항.md), [ERD.md](ERD.md)
> - 백엔드: Spring Boot / 프론트엔드: React (Vite)

---

## 목차

0. [엔드포인트 한눈에 보기](#0-엔드포인트-한눈에-보기)
1. [공통 규칙](#1-공통-규칙)
2. [공통 객체](#2-공통-객체)
3. [에러 코드](#3-에러-코드)
4. [회원 · 인증](#4-회원--인증)
5. [블로그 · 카테고리](#5-블로그--카테고리)
6. [글 · 목록](#6-글--목록)
7. [임시저장](#7-임시저장)
8. [댓글](#8-댓글)
9. [좋아요 · 북마크](#9-좋아요--북마크)
10. [신고](#10-신고)
11. [이미지](#11-이미지)

---

## 0. 엔드포인트 한눈에 보기

> 🔓 누구나 · 🔒 로그인 필요 · 👤 본인 또는 주인만

| # | 메서드 | 주소 | 설명 | 권한 | 요구사항 |
|---|---|---|---|---|---|
| 4.1 | POST | `/api/auth/signup` | 회원가입 | 🔓 | U-01, U-02 |
| 4.2 | POST | `/api/auth/login` | 로그인 | 🔓 | U-03 |
| 4.3 | GET | `/api/auth/check-username` | 아이디 사용 가능 여부 | 🔓 | U-01 |
| 4.4 | GET | `/api/users/me` | 내 정보 | 🔒 | U-04 |
| 4.5 | PUT | `/api/users/me` | 내 프로필 수정 | 🔒 | U-04 |
| 4.6 | PATCH | `/api/users/me/password` | 비밀번호 변경 | 🔒 | U-05 |
| 4.7 | DELETE | `/api/users/me` | 회원 탈퇴 | 🔒 | U-06 |
| 4.8 | GET | `/api/users/me/bookmarks` | 내 북마크 목록 | 🔒 | K-02 |
| 5.1 | GET | `/api/blogs/{username}` | 블로그 홈 정보 | 🔓 | B-01 |
| 5.2 | PUT | `/api/blogs/{blogId}` | 블로그 이름·소개 수정 | 👤 | U-04 |
| 5.3 | GET | `/api/blogs/{username}/categories` | 카테고리 사이드바 | 🔓 | B-02 |
| 5.4 | POST | `/api/blogs/{blogId}/categories` | 카테고리 생성 | 👤 | B-04 |
| 5.5 | PUT | `/api/blogs/{blogId}/categories/order` | 카테고리 순서 변경 | 👤 | B-04 |
| 5.6 | PUT | `/api/categories/{categoryId}` | 카테고리 이름·공개 범위 수정 | 👤 | B-04, B-05 |
| 5.7 | DELETE | `/api/categories/{categoryId}` | 카테고리 삭제 | 👤 | B-04, B-05 |
| 6.1 | GET | `/api/posts` | 메인 최신·인기 글 | 🔓 | S-01, S-02 |
| 6.2 | GET | `/api/posts/search` | 키워드 검색 | 🔓 | S-05 |
| 6.3 | GET | `/api/tags/{tagName}/posts` | 태그 모아보기 | 🔓 | S-06 |
| 6.4 | GET | `/api/blogs/{username}/posts` | 블로그 글 목록 (카테고리별) | 🔓 | B-03, S-07 |
| 6.5 | GET | `/api/blogs/{username}/posts/{postId}` | 글 상세 | 🔓 | P-05, P-06, C-04 |
| 6.6 | POST | `/api/posts` | 글 바로 발행 | 🔒 | P-01, P-02 |
| 6.7 | PUT | `/api/posts/{postId}` | 발행 글 수정 | 👤 | P-04 |
| 6.8 | DELETE | `/api/posts/{postId}` | 글 삭제 | 👤 | P-04 |
| 7.1 | POST | `/api/drafts` | 처음 임시저장 | 🔒 | D-01, D-02 |
| 7.2 | PUT | `/api/drafts/{postId}` | 다시 임시저장 (덮어쓰기) | 👤 | D-02 |
| 7.3 | GET | `/api/drafts` | 임시저장 목록 | 🔒 | D-04 |
| 7.4 | GET | `/api/drafts/{postId}` | 임시저장 글 불러오기 | 👤 | D-04 |
| 7.5 | DELETE | `/api/drafts/{postId}` | 임시저장 글 삭제 | 👤 | D-04 |
| 7.6 | POST | `/api/drafts/{postId}/publish` | 임시저장 글 발행 | 👤 | D-05 |
| 8.1 | GET | `/api/posts/{postId}/comments` | 댓글 목록 | 🔓 | C-01, C-02 |
| 8.2 | POST | `/api/posts/{postId}/comments` | 댓글·답글 작성 | 🔒 | C-01, C-02 |
| 8.3 | PATCH | `/api/comments/{commentId}` | 댓글 수정 | 👤 작성자 | C-01 |
| 8.4 | DELETE | `/api/comments/{commentId}` | 댓글 삭제 | 👤 작성자, 글 주인 | C-01 |
| 9.1 | PUT · DELETE | `/api/posts/{postId}/like` | 좋아요 누르기 · 취소 | 🔒 | C-03 |
| 9.2 | PUT · DELETE | `/api/posts/{postId}/bookmark` | 북마크 추가 · 해제 | 🔒 | K-01 |
| 10.1 | POST | `/api/posts/{postId}/reports` | 글 신고 | 🔒 | R-01, R-02 |
| 10.2 | POST | `/api/comments/{commentId}/reports` | 댓글 신고 | 🔒 | R-02, R-04 |
| 11.1 | POST | `/api/images` | 이미지 업로드 | 🔒 | P-03 |

---

## 1. 공통 규칙

### 1.1 주소와 형식
| 항목 | 규칙 |
|---|---|
| 주소 앞부분 | 모든 API는 `/api`로 시작해요. Nginx가 `/api/*`는 Spring으로, 나머지는 React로 보내요 |
| 요청·응답 형식 | `Content-Type: application/json; charset=UTF-8` (이미지 업로드만 `multipart/form-data`) |
| JSON 이름 | camelCase (`thumbnailUrl`, `likeCount`) |
| 시간 형식 | ISO-8601에 한국 시간대 표기. 예: `2026-10-06T14:30:00+09:00` |
| 빈 값 | 값이 없는 필드는 생략하지 않고 `null`로 보내요 |

### 1.2 인증
- 로그인하면 받은 토큰을 요청 헤더에 넣어요.
  ```
  Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
  ```
- 토큰은 프론트엔드가 `localStorage`에 저장하고, 만료 시간은 **2시간**이에요.
- **🔓 API에도 토큰을 보낼 수 있어요.** 토큰이 있으면 서버가 "누가 보는지" 알아서 `liked`, `bookmarked`, `isOwner` 같은 값과 주인에게만 보이는 데이터(비공개 글 등)를 채워 줘요. 토큰이 없거나 만료됐으면 방문자로 처리해요.
- 🔒/👤 API에 토큰이 없거나, 만료됐거나, **탈퇴한 회원의 토큰**이면 `401 UNAUTHORIZED`로 응답해요. 프론트엔드는 토큰을 지우고 로그인 화면으로 보내요(U-08).
- 로그아웃은 API 없이 프론트엔드가 토큰을 지우는 것으로 처리해요(U-03).

### 1.3 HTTP 상태 코드
| 코드 | 쓰는 경우 |
|---|---|
| 200 OK | 조회, 수정 성공 |
| 201 Created | 생성 성공 |
| 204 No Content | 삭제 성공, 응답 본문이 필요 없는 성공 |
| 400 Bad Request | 입력값 오류, 규칙 위반 (예: 기본 카테고리 삭제) |
| 401 Unauthorized | 로그인 필요, 토큰 만료, 로그인 실패 |
| 403 Forbidden | 볼 수는 있지만 **권한이 없는** 작업 (예: 남의 공개 글 수정) |
| 404 Not Found | 대상이 없거나 **볼 수 없는** 대상 (비공개 글, 남의 임시저장 글 등) |
| 409 Conflict | 중복 (이메일, 아이디, 카테고리 이름, 신고) |
| 500 Internal Server Error | 서버 오류 (내부 정보는 응답에 넣지 않아요) |

> 💡 **403과 404 구분 규칙:** 요청한 사람이 **볼 수 없는 대상**이면 존재 여부도 알려주지 않기 위해 **404**, 볼 수는 있지만 고칠 권한이 없으면 **403**이에요. 예를 들어 남의 **비공개** 글을 수정하려 하면 404, 남의 **공개** 글을 수정하려 하면 403이에요.

### 1.4 에러 응답
모든 에러는 같은 형태예요.
```json
{
  "code": "INVALID_INPUT",
  "message": "입력값을 확인해 주세요.",
  "fieldErrors": [
    { "field": "email", "message": "이메일 형식이 아니에요." },
    { "field": "password", "message": "비밀번호는 8~64자이고 영문과 숫자를 포함해야 해요." }
  ]
}
```
| 필드 | 설명 |
|---|---|
| `code` | 프론트엔드가 분기할 때 쓰는 고정 문자열 (3장 에러 코드) |
| `message` | 사용자에게 바로 보여줄 문장. 계정 존재 여부나 서버 내부 정보는 드러내지 않아요 (NF-06) |
| `fieldErrors` | 입력 검증 실패일 때만 들어가요. 그 밖의 에러에서는 빈 배열 `[]`이에요 |

### 1.5 페이지 요청과 응답
- 요청: `?page=1&size=12` — **page는 1부터** 시작해요. 생략하면 `page=1`, `size=12`예요. `size`는 최대 50이에요.
- 응답:
```json
{
  "content": [ ],
  "page": 1,
  "size": 12,
  "totalElements": 37,
  "totalPages": 4
}
```
- 범위를 넘는 페이지(예: 글이 37개인데 `page=10`)는 `content: []`로 200 응답해요.

---

## 2. 공통 객체

여러 API에서 반복되는 응답 조각이에요. 각 API 설명에서는 이름으로 참조해요.

### 2.1 `UserSummary` (작성자 정보)
```json
{
  "username": "nayoung",
  "nickname": "나영",
  "profileImageUrl": "/uploads/2026/10/6f1c2a.png"
}
```
**탈퇴한 회원**이면 아래처럼 보내요. 프론트엔드는 `username`이 `null`이면 프로필 링크를 걸지 않아요.
```json
{
  "username": null,
  "nickname": "탈퇴한 사용자",
  "profileImageUrl": null
}
```

### 2.2 `PostCard` (목록 카드)
```json
{
  "id": 10,
  "title": "JPA 연관관계 정리",
  "summary": "JPA에서 연관관계의 주인을 정하는 기준과 양방향 매핑 시 주의할 점을 정리했어요...",
  "thumbnailUrl": "/uploads/2026/10/a81b9e.png",
  "likeCount": 12,
  "publishedAt": "2026-10-06T14:30:00+09:00",
  "author": { "username": "nayoung", "nickname": "나영", "profileImageUrl": null },
  "visibility": "PUBLIC",
  "categoryVisibility": "PUBLIC"
}
```
- `thumbnailUrl`이 `null`이면 썸네일 없는 카드로 보여줘요(P-02).
- `visibility`와 `categoryVisibility`는 **블로그 주인이 자기 목록을 볼 때** 🔒 표시를 붙이는 데 써요. 방문자에게는 항상 둘 다 `PUBLIC`인 글만 와요.

### 2.3 `CategoryItem`
```json
{
  "id": 2,
  "name": "개발",
  "displayOrder": 1,
  "isDefault": false,
  "visibility": "PUBLIC",
  "postCount": 12
}
```

### 2.4 Enum 값
| 이름 | 값 |
|---|---|
| `Visibility` | `PUBLIC`, `PRIVATE` |
| `ReportReason` | `SPAM`, `ABUSE`, `ADULT`, `ETC` |
| `PostSort` | `latest`, `popular` |

---

## 3. 에러 코드

| 코드 | HTTP | 메시지 (예시) | 쓰는 곳 |
|---|---|---|---|
| `INVALID_INPUT` | 400 | 입력값을 확인해 주세요. | 모든 입력 검증 (`fieldErrors` 포함) |
| `UNAUTHORIZED` | 401 | 로그인이 필요해요. | 토큰 없음·만료·탈퇴 회원 |
| `FORBIDDEN` | 403 | 권한이 없어요. | 남의 공개 글·댓글·블로그 수정 시도 |
| `INTERNAL_ERROR` | 500 | 일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요. | 예상하지 못한 오류 |
| **회원** | | | |
| `DUPLICATE_EMAIL` | 409 | 이미 가입된 이메일이에요. | 4.1 |
| `DUPLICATE_USERNAME` | 409 | 이미 사용 중인 아이디예요. | 4.1 |
| `RESERVED_USERNAME` | 400 | 사용할 수 없는 아이디예요. | 4.1 |
| `PASSWORD_CONFIRM_MISMATCH` | 400 | 비밀번호 확인이 일치하지 않아요. | 4.1, 4.6 |
| `INVALID_CREDENTIALS` | 401 | 이메일 또는 비밀번호가 올바르지 않아요. | 4.2 |
| `WRONG_PASSWORD` | 400 | 현재 비밀번호가 올바르지 않아요. | 4.6, 4.7 |
| `USER_NOT_FOUND` | 404 | 존재하지 않는 사용자예요. | 5.1 등 `{username}` 조회 |
| **블로그 · 카테고리** | | | |
| `BLOG_NOT_FOUND` | 404 | 존재하지 않는 블로그예요. | 5.2 |
| `CATEGORY_NOT_FOUND` | 404 | 존재하지 않는 카테고리예요. | 5.6, 5.7 |
| `INVALID_CATEGORY` | 400 | 선택할 수 없는 카테고리예요. | 6.6, 6.7, 7.x (남의 블로그 카테고리 지정) |
| `DUPLICATE_CATEGORY_NAME` | 409 | 같은 이름의 카테고리가 있어요. | 5.4, 5.6 |
| `CATEGORY_LIMIT_EXCEEDED` | 400 | 카테고리는 최대 30개까지 만들 수 있어요. | 5.4 |
| `DEFAULT_CATEGORY_NOT_DELETABLE` | 400 | 기본 카테고리는 삭제할 수 없어요. | 5.7 |
| `INVALID_CATEGORY_ORDER` | 400 | 카테고리 순서 정보가 올바르지 않아요. | 5.5 |
| **글 · 임시저장** | | | |
| `POST_NOT_FOUND` | 404 | 존재하지 않는 글이에요. | 글 조회·수정·삭제, 볼 수 없는 글 |
| `DRAFT_NOT_FOUND` | 404 | 존재하지 않는 임시저장 글이에요. | 7.2 ~ 7.6 |
| **댓글** | | | |
| `COMMENT_NOT_FOUND` | 404 | 존재하지 않는 댓글이에요. | 8.3, 8.4, 10.2 |
| `INVALID_PARENT_COMMENT` | 400 | 이 댓글에는 답글을 달 수 없어요. | 8.2 (답글의 답글, 다른 글의 댓글, 삭제된 댓글) |
| **신고** | | | |
| `CANNOT_REPORT_OWN_CONTENT` | 400 | 본인의 글이나 댓글은 신고할 수 없어요. | 10.1, 10.2 |
| `CANNOT_REPORT_DELETED_COMMENT` | 400 | 삭제된 댓글은 신고할 수 없어요. | 10.2 |
| `ALREADY_REPORTED` | 409 | 이미 신고했어요. | 10.1, 10.2 |
| **이미지** | | | |
| `INVALID_FILE_TYPE` | 400 | jpg, png, gif, webp 파일만 올릴 수 있어요. | 11.1 |
| `FILE_TOO_LARGE` | 400 | 5MB 이하의 파일만 올릴 수 있어요. | 11.1 |

---

## 4. 회원 · 인증

### 4.1 회원가입
`POST /api/auth/signup` 🔓 · U-01, U-02

가입하면 블로그(`{닉네임}의 블로그`)와 기본 카테고리("미분류")가 **한 트랜잭션**으로 함께 만들어져요. 자동 로그인은 하지 않고, 프론트엔드가 이어서 4.2 로그인을 호출해요.

**요청**
```json
{
  "email": "Nayoung@Gmail.com",
  "password": "ownlog1234",
  "passwordConfirm": "ownlog1234",
  "username": "nayoung",
  "nickname": "나영"
}
```
| 필드 | 필수 | 규칙 |
|---|---|---|
| `email` | ✅ | 이메일 형식, 최대 255자. **소문자로 바꿔서 저장**해요 |
| `password` | ✅ | 8~64자, 영문과 숫자 포함 |
| `passwordConfirm` | ✅ | `password`와 같아야 해요 |
| `username` | ✅ | 영문 소문자, 숫자, `_` 3~20자. 예약어(`admin`, `ownlog`, `official`, `system` 등) 불가 |
| `nickname` | ✅ | 2~20자 |

**응답 `201 Created`**
```json
{
  "id": 1,
  "username": "nayoung"
}
```

**에러:** `INVALID_INPUT` · `PASSWORD_CONFIRM_MISMATCH` · `RESERVED_USERNAME` · `DUPLICATE_EMAIL` · `DUPLICATE_USERNAME`

---

### 4.2 로그인
`POST /api/auth/login` 🔓 · U-03

**요청**
```json
{
  "email": "nayoung@gmail.com",
  "password": "ownlog1234"
}
```

**응답 `200 OK`**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIi...",
  "tokenType": "Bearer",
  "expiresIn": 7200,
  "user": {
    "id": 1,
    "username": "nayoung",
    "nickname": "나영",
    "profileImageUrl": null,
    "blogId": 1
  }
}
```
- `expiresIn`: 토큰 유효 시간(초). 2시간 = 7200초예요.
- `user.blogId`: 글쓰기, 카테고리 관리 API에 필요한 내 블로그 id예요.

**에러:** `INVALID_INPUT` · `INVALID_CREDENTIALS` (이메일이 없든 비밀번호가 틀렸든 **같은 응답**이에요. 탈퇴한 회원도 같아요)

---

### 4.3 아이디 사용 가능 여부
`GET /api/auth/check-username?username=nayoung` 🔓 · U-01

가입 폼에서 아이디를 입력할 때 실시간으로 확인해요. 이메일 확인 API는 계정 존재 여부를 대량으로 조회하는 데 쓰일 수 있어서 **만들지 않아요.** 이메일 중복은 가입 요청(4.1)에서 알려줘요.

**응답 `200 OK`**
```json
{ "available": false, "reason": "DUPLICATE" }
```
| `reason` | 뜻 |
|---|---|
| `null` | 사용할 수 있어요 (`available: true`) |
| `INVALID_FORMAT` | 형식이 맞지 않아요 |
| `RESERVED` | 예약어예요 |
| `DUPLICATE` | 이미 쓰고 있거나 탈퇴한 회원이 쓰던 아이디예요 |

> 이 API는 확인용일 뿐이에요. 확인과 가입 사이에 다른 사람이 같은 아이디로 가입할 수 있어서, 4.1에서 다시 검사해요.

---

### 4.4 내 정보
`GET /api/users/me` 🔒 · U-04

**응답 `200 OK`**
```json
{
  "id": 1,
  "email": "nayoung@gmail.com",
  "username": "nayoung",
  "nickname": "나영",
  "bio": "백엔드 개발자를 꿈꾸는 대학생",
  "profileImageUrl": "/uploads/2026/10/6f1c2a.png",
  "role": "USER",
  "blog": {
    "id": 1,
    "name": "나영의 개발일지",
    "description": "Spring과 React 공부 기록"
  },
  "createdAt": "2026-10-01T10:00:00+09:00"
}
```
> 새로고침한 뒤 로그인 상태를 복원할 때도 이 API를 써요. 토큰이 유효하면 200, 아니면 401이에요.

---

### 4.5 내 프로필 수정
`PUT /api/users/me` 🔒 · U-04

마이페이지 폼의 값을 **모두** 보내요. `null`을 보내면 그 값을 지워요.

**요청**
```json
{
  "nickname": "나영",
  "bio": "백엔드 개발자를 꿈꾸는 대학생",
  "profileImageUrl": "/uploads/2026/10/6f1c2a.png"
}
```
| 필드 | 필수 | 규칙 |
|---|---|---|
| `nickname` | ✅ | 2~20자 |
| `bio` | | 최대 100자. `null`이면 지워요 |
| `profileImageUrl` | | 11.1에서 받은 URL. `null`이면 기본 이미지로 돌아가요 (이전 파일은 서버가 삭제해요) |

**응답 `200 OK`**: 4.4와 같은 형태

**에러:** `INVALID_INPUT`

> 블로그 이름과 소개는 5.2에서 따로 수정해요. 같은 마이페이지 화면에서 두 API를 차례로 호출해요.

---

### 4.6 비밀번호 변경
`PATCH /api/users/me/password` 🔒 · U-05

**요청**
```json
{
  "currentPassword": "ownlog1234",
  "newPassword": "newpass5678",
  "newPasswordConfirm": "newpass5678"
}
```

**응답 `204 No Content`**

**에러:** `INVALID_INPUT` · `WRONG_PASSWORD` · `PASSWORD_CONFIRM_MISMATCH`

> 💡 현재 비밀번호가 틀렸을 때 **401이 아니라 400**(`WRONG_PASSWORD`)이에요. 401을 쓰면 프론트엔드가 "토큰 만료"로 착각해서 로그아웃시켜 버려요.

---

### 4.7 회원 탈퇴
`DELETE /api/users/me` 🔒 · U-06

되돌릴 수 없어서 **비밀번호를 다시 확인**해요.

**요청**
```json
{ "password": "ownlog1234" }
```
> axios에서는 `axios.delete(url, { data: { password } })`처럼 `data` 옵션으로 본문을 보내요.

**응답 `204 No Content`**

처리 순서는 ERD 5장을 따라요. 글·카테고리·블로그 삭제 → 좋아요(`like_count` 보정)·북마크 삭제 → 개인정보 정리 후 `status = DELETED`. 응답을 받으면 프론트엔드는 토큰을 지우고 메인으로 이동해요.

**에러:** `WRONG_PASSWORD`

---

### 4.8 내 북마크 목록
`GET /api/users/me/bookmarks?page=1&size=12` 🔒 · K-02

북마크한 최신순이에요. 북마크한 뒤 **볼 수 없게 된 글**(비공개 글, 비공개 카테고리의 글)은 빠져요.

**응답 `200 OK`**: 페이지 응답. `content`는 `PostCard`에 `bookmarkedAt`을 더한 형태예요.
```json
{
  "content": [
    {
      "id": 25,
      "title": "부산 2박 3일 여행 코스",
      "summary": "해운대, 광안리, 감천문화마을까지...",
      "thumbnailUrl": "/uploads/2026/10/c03d11.jpg",
      "likeCount": 30,
      "publishedAt": "2026-10-04T09:12:00+09:00",
      "author": { "username": "minsu", "nickname": "민수", "profileImageUrl": null },
      "visibility": "PUBLIC",
      "categoryVisibility": "PUBLIC",
      "bookmarkedAt": "2026-10-05T21:40:00+09:00"
    }
  ],
  "page": 1, "size": 12, "totalElements": 1, "totalPages": 1
}
```

---

## 5. 블로그 · 카테고리

### 5.1 블로그 홈 정보
`GET /api/blogs/{username}` 🔓 · B-01

블로그 홈 상단(프로필, 블로그 이름·소개)에 쓰여요. 글 목록은 6.4, 사이드바는 5.3이에요.

**응답 `200 OK`**
```json
{
  "blogId": 1,
  "name": "나영의 개발일지",
  "description": "Spring과 React 공부 기록",
  "owner": {
    "username": "nayoung",
    "nickname": "나영",
    "bio": "백엔드 개발자를 꿈꾸는 대학생",
    "profileImageUrl": "/uploads/2026/10/6f1c2a.png"
  },
  "isOwner": true
}
```
- `isOwner`: 요청한 사람이 블로그 주인이면 `true`예요. 프론트엔드는 이 값으로 [글쓰기], [카테고리 관리] 버튼을 보여줘요.

**에러:** `USER_NOT_FOUND` (없는 아이디, 탈퇴한 회원)

---

### 5.2 블로그 이름·소개 수정
`PUT /api/blogs/{blogId}` 👤 · U-04

**요청**
```json
{
  "name": "나영의 개발일지",
  "description": "Spring과 React 공부 기록"
}
```
| 필드 | 필수 | 규칙 |
|---|---|---|
| `name` | ✅ | 1~50자 |
| `description` | | 최대 200자. `null`이면 지워요 |

**응답 `200 OK`**
```json
{ "id": 1, "name": "나영의 개발일지", "description": "Spring과 React 공부 기록" }
```

**에러:** `INVALID_INPUT` · `BLOG_NOT_FOUND` · `FORBIDDEN`

---

### 5.3 카테고리 사이드바
`GET /api/blogs/{username}/categories` 🔓 · B-02

**응답 `200 OK`**
```json
{
  "totalPostCount": 15,
  "categories": [
    { "id": 1, "name": "미분류", "displayOrder": 0, "isDefault": true,  "visibility": "PUBLIC",  "postCount": 1 },
    { "id": 2, "name": "개발",   "displayOrder": 1, "isDefault": false, "visibility": "PUBLIC",  "postCount": 12 },
    { "id": 3, "name": "일기",   "displayOrder": 2, "isDefault": false, "visibility": "PRIVATE", "postCount": 2 }
  ]
}
```
| 보는 사람 | 비공개 카테고리 | `postCount` |
|---|---|---|
| 방문자 | **목록에서 빠져요** | 볼 수 있는 글 수 (공개 글 + 공개 카테고리) |
| 주인 | 포함돼요 (🔒 표시용 `visibility`) | 비공개 글을 포함한 전체 발행 글 수 |

- 임시저장 글은 어느 쪽에도 세지 않아요.
- `totalPostCount`: "전체 글" 메뉴 옆에 보여줄 수예요. 같은 규칙으로 세요.
- 카테고리 관리 화면도 주인으로 이 API를 호출해서 목록을 그려요.

**에러:** `USER_NOT_FOUND`

---

### 5.4 카테고리 생성
`POST /api/blogs/{blogId}/categories` 👤 · B-04

**요청**
```json
{ "name": "여행", "visibility": "PUBLIC" }
```
| 필드 | 필수 | 규칙 |
|---|---|---|
| `name` | ✅ | 1~30자, 앞뒤 공백 제거, 같은 블로그 안에서 중복 불가 |
| `visibility` | | 생략하면 `PUBLIC` |

**응답 `201 Created`**: `CategoryItem` (맨 아래에 추가돼요)
```json
{ "id": 4, "name": "여행", "displayOrder": 3, "isDefault": false, "visibility": "PUBLIC", "postCount": 0 }
```

**에러:** `INVALID_INPUT` · `FORBIDDEN` · `DUPLICATE_CATEGORY_NAME` · `CATEGORY_LIMIT_EXCEEDED`

---

### 5.5 카테고리 순서 변경
`PUT /api/blogs/{blogId}/categories/order` 👤 · B-04

드래그로 바꾼 순서대로 **블로그의 모든 카테고리 id**를 보내요. 서버는 배열 순서대로 `displayOrder`를 0, 1, 2…로 다시 매겨요.

**요청**
```json
{ "categoryIds": [2, 1, 4, 3] }
```

**응답 `204 No Content`**

**에러:** `FORBIDDEN` · `INVALID_CATEGORY_ORDER` (빠진 카테고리, 중복 id, 다른 블로그의 카테고리가 있을 때)

---

### 5.6 카테고리 수정
`PUT /api/categories/{categoryId}` 👤 · B-04, B-05

**요청**
```json
{ "name": "일기", "visibility": "PRIVATE" }
```
| 필드 | 필수 | 규칙 |
|---|---|---|
| `name` | ✅ | 5.4와 같아요. "미분류"도 이름을 바꿀 수 있어요 |
| `visibility` | ✅ | `PUBLIC` / `PRIVATE`. 바꿔도 **글의 공개 설정은 그대로**예요 |

**응답 `200 OK`**: `CategoryItem`

**에러:** `INVALID_INPUT` · `CATEGORY_NOT_FOUND` · `FORBIDDEN` · `DUPLICATE_CATEGORY_NAME`

---

### 5.7 카테고리 삭제
`DELETE /api/categories/{categoryId}` 👤 · B-04, B-05

그 카테고리의 글(임시저장 글 포함)은 **"미분류"로 옮긴 뒤** 삭제해요. **비공개 카테고리**였다면 옮기는 글을 모두 **비공개로 바꿔서** 옮겨요.

**응답 `200 OK`**
```json
{ "movedPostCount": 3, "movedToCategoryId": 1 }
```
> 프론트엔드는 "글 3개를 '미분류'로 옮겼어요" 같은 안내를 보여줄 수 있어요.

**에러:** `CATEGORY_NOT_FOUND` · `FORBIDDEN` · `DEFAULT_CATEGORY_NOT_DELETABLE`

---

## 6. 글 · 목록

> **공개 조건(ERD 4.1):** 방문자에게는 `status = PUBLISHED` **그리고** 글 공개 **그리고** 카테고리 공개인 글만 보여요. 6.1~6.3은 블로그 주인이 봐도 이 조건을 똑같이 적용해요(메인과 검색은 "남이 보는 화면"이라서요). 6.4, 6.5만 주인에게 비공개 글을 보여줘요.

### 6.1 메인 최신·인기 글
`GET /api/posts?sort=latest&page=1&size=12` 🔓 · S-01, S-02

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `sort` | `latest` | `latest`: 발행 최신순 / `popular`: **최근 7일**간 발행된 글 중 좋아요 많은 순(같으면 최신순) |
| `page`, `size` | 1, 12 | 1.5 참고 |

**응답 `200 OK`**: 페이지 응답, `content`는 `PostCard[]`
```json
{
  "content": [
    {
      "id": 10,
      "title": "JPA 연관관계 정리",
      "summary": "JPA에서 연관관계의 주인을 정하는 기준과...",
      "thumbnailUrl": "/uploads/2026/10/a81b9e.png",
      "likeCount": 12,
      "publishedAt": "2026-10-06T14:30:00+09:00",
      "author": { "username": "nayoung", "nickname": "나영", "profileImageUrl": null },
      "visibility": "PUBLIC",
      "categoryVisibility": "PUBLIC"
    }
  ],
  "page": 1, "size": 12, "totalElements": 37, "totalPages": 4
}
```

**에러:** `INVALID_INPUT` (`sort` 값이 잘못됐을 때)

---

### 6.2 키워드 검색
`GET /api/posts/search?q=JPA&page=1&size=12` 🔓 · S-05

| 파라미터 | 필수 | 규칙 |
|---|---|---|
| `q` | ✅ | 앞뒤 공백 제거 후 1~50자. **제목과 본문**에서 대소문자 구분 없이 찾아요 |

**응답 `200 OK`**: 6.1과 같은 형태 (발행 최신순)

**에러:** `INVALID_INPUT` (`q`가 비었거나 너무 길 때)

---

### 6.3 태그 모아보기
`GET /api/tags/{tagName}/posts?page=1&size=12` 🔓 · S-06

- `{tagName}`은 URL 인코딩해서 보내요. 예: `/api/tags/%EC%97%AC%ED%96%89/posts` (= `여행`)
- 태그는 **대소문자를 구분하지 않아요.** `/api/tags/java/posts`와 `/api/tags/Java/posts`는 같은 결과예요.

**응답 `200 OK`**
```json
{
  "tag": "Java",
  "posts": {
    "content": [ ],
    "page": 1, "size": 12, "totalElements": 8, "totalPages": 1
  }
}
```
- `tag`: 화면 제목에 쓸 **대표 표기**(처음 만들어진 표기)예요.
- 존재하지 않는 태그면 404가 아니라 `tag`에 요청한 이름, `posts.content`에 빈 배열을 담아 200으로 응답해요.

---

### 6.4 블로그 글 목록 (카테고리별)
`GET /api/blogs/{username}/posts?categoryId=2&page=1&size=12` 🔓 · B-03, S-07

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `categoryId` | | 생략하면 전체 글, 넣으면 그 카테고리의 글만 보여줘요 |

| 보는 사람 | 보이는 글 |
|---|---|
| 방문자 | 공개 조건을 만족하는 글만. 비공개 카테고리의 `categoryId`를 넣으면 빈 목록이에요 |
| 주인 | 비공개 글과 비공개 카테고리의 글까지 (`visibility`, `categoryVisibility`로 🔒 표시). **임시저장 글은 제외** |

**응답 `200 OK`**: 6.1과 같은 형태 (발행 최신순)

**에러:** `USER_NOT_FOUND`

---

### 6.5 글 상세
`GET /api/blogs/{username}/posts/{postId}` 🔓 · P-05, P-06, C-04

- 주소의 `username`과 글의 실제 블로그 주인이 다르면 **404**예요.
- 볼 수 없는 글(비공개, 비공개 카테고리, 임시저장)이면 주인이 아닌 사람에게 **404**예요.
- **작성자 본인이 아니면 조회수가 1 올라가요.** 응답의 `viewCount`는 올라간 뒤의 값이에요.

**응답 `200 OK`**
```json
{
  "id": 10,
  "title": "JPA 연관관계 정리",
  "content": "## 연관관계의 주인\n\n외래 키를 가진 쪽이 주인이에요...\n\n![diagram](/uploads/2026/10/a81b9e.png)",
  "thumbnailUrl": "/uploads/2026/10/a81b9e.png",
  "visibility": "PUBLIC",
  "category": { "id": 2, "name": "개발", "visibility": "PUBLIC" },
  "tags": ["Java", "JPA", "Spring"],
  "author": { "username": "nayoung", "nickname": "나영", "profileImageUrl": null },
  "blog": { "id": 1, "name": "나영의 개발일지" },
  "viewCount": 128,
  "likeCount": 12,
  "liked": false,
  "bookmarked": true,
  "isOwner": false,
  "publishedAt": "2026-10-06T14:30:00+09:00",
  "editedAt": null
}
```
| 필드 | 설명 |
|---|---|
| `content` | **마크다운 원문**이에요. 프론트엔드가 렌더링하면서 **반드시 sanitize**해요 (XSS 방지, 5장 보안) |
| `tags` | 이름순 정렬 (P-05) |
| `liked`, `bookmarked` | 로그인한 사람 기준. 방문자는 항상 `false` |
| `isOwner` | `true`면 [수정], [삭제] 버튼을 보여주고 [신고] 버튼은 숨겨요 |
| `publishedAt` | 화면의 **작성일** |
| `editedAt` | 화면의 **수정일**. `null`이면 수정 표시를 하지 않아요 |

**에러:** `USER_NOT_FOUND` · `POST_NOT_FOUND`

---

### 6.6 글 바로 발행
`POST /api/posts` 🔒 · P-01, P-02

내 블로그에 바로 발행해요. 블로그는 토큰의 회원으로 정해져서 `blogId`를 보내지 않아요.

**요청**
```json
{
  "title": "JPA 연관관계 정리",
  "content": "## 연관관계의 주인\n\n외래 키를 가진 쪽이 주인이에요...",
  "thumbnailUrl": null,
  "categoryId": 2,
  "tags": ["Java", "jpa", "Spring", "java"],
  "visibility": "PUBLIC"
}
```
| 필드 | 필수 | 규칙 |
|---|---|---|
| `title` | ✅ | 공백 제외 1자 이상, 최대 100자 |
| `content` | ✅ | 공백 제외 1자 이상, 최대 100,000자 |
| `thumbnailUrl` | | `null`이면 **본문의 첫 번째 이미지**를 자동으로 써요 |
| `categoryId` | | `null`이면 "미분류". **내 블로그의 카테고리**만 가능해요 |
| `tags` | | 최대 10개, 태그당 20자, 한글·영문·숫자·`_`·`-`만. 대소문자 무시로 **중복은 합쳐요** (위 예시는 `Java`, `jpa`, `Spring` 3개가 돼요) |
| `visibility` | | 생략하면 `PUBLIC` |

**응답 `201 Created`**
```json
{ "id": 10, "username": "nayoung" }
```
> 프론트엔드는 `/@nayoung/posts/10`으로 이동해요.

**에러:** `INVALID_INPUT` · `INVALID_CATEGORY`

---

### 6.7 발행 글 수정
`PUT /api/posts/{postId}` 👤 · P-04

요청 형식은 6.6과 같아요. 폼의 값을 **모두** 보내요.

- **제목, 본문, 썸네일, 태그**가 실제로 바뀌었을 때만 `editedAt`이 갱신돼요. 카테고리 이동과 공개 범위 변경은 수정으로 보지 않아요(P-05).
- 임시저장 글 id를 보내면 `POST_NOT_FOUND`예요(임시저장 글은 7.2로 수정해요).

**응답 `200 OK`**
```json
{ "id": 10, "username": "nayoung", "editedAt": "2026-10-07T09:00:00+09:00" }
```

**에러:** `INVALID_INPUT` · `INVALID_CATEGORY` · `POST_NOT_FOUND` · `FORBIDDEN`

---

### 6.8 글 삭제
`DELETE /api/posts/{postId}` 👤 · P-04

댓글(그 댓글들의 신고 포함), 좋아요, 북마크, 태그 연결, 글 신고가 함께 삭제돼요(ERD 5장).

**응답 `204 No Content`**

**에러:** `POST_NOT_FOUND` · `FORBIDDEN`

---

## 7. 임시저장

> 임시저장 글은 **본인에게만 존재해요.** 다른 사람이 임시저장 글 id로 요청하면 403이 아니라 **404**(`DRAFT_NOT_FOUND`)예요. 발행된 글 id로 7.x를 호출해도 404예요.

### 7.1 처음 임시저장
`POST /api/drafts` 🔒 · D-01, D-02

**요청**: 6.6과 같은 필드지만 **모두 선택**이에요 (D-03).
```json
{
  "title": null,
  "content": "쓰다 만 글...",
  "thumbnailUrl": null,
  "categoryId": null,
  "tags": [],
  "visibility": "PUBLIC"
}
```
- 길이와 형식 제한(제목 100자, 본문 100,000자, 태그 규칙)은 그대로 검사해요. **비어 있어도 되는 것**만 달라요.
- `visibility`는 나중에 발행할 때 쓸 값을 미리 저장해 두는 거예요.

**응답 `201 Created`**
```json
{ "id": 31, "updatedAt": "2026-10-06T15:02:00+09:00" }
```
> 프론트엔드는 받은 `id`를 기억했다가 다음 임시저장부터 7.2를 호출해요. 화면에는 "임시저장됨 (15:02)"을 보여줘요(D-07).

**에러:** `INVALID_INPUT` · `INVALID_CATEGORY`

---

### 7.2 다시 임시저장 (덮어쓰기)
`PUT /api/drafts/{postId}` 👤 · D-02

**요청**: 7.1과 같아요. **응답 `200 OK`**: 7.1과 같은 형태

**에러:** `INVALID_INPUT` · `INVALID_CATEGORY` · `DRAFT_NOT_FOUND`

---

### 7.3 임시저장 목록
`GET /api/drafts?page=1&size=12` 🔒 · D-04

최근에 저장한 순이에요(`updatedAt` 내림차순).

**응답 `200 OK`**
```json
{
  "content": [
    {
      "id": 31,
      "title": null,
      "summary": "쓰다 만 글...",
      "updatedAt": "2026-10-06T15:02:00+09:00"
    }
  ],
  "page": 1, "size": 12, "totalElements": 1, "totalPages": 1
}
```
> `title`이 `null`이면 화면에 "제목 없음"으로 보여줘요(D-03).

---

### 7.4 임시저장 글 불러오기
`GET /api/drafts/{postId}` 👤 · D-04

**응답 `200 OK`**
```json
{
  "id": 31,
  "title": null,
  "content": "쓰다 만 글...",
  "thumbnailUrl": null,
  "category": { "id": 1, "name": "미분류", "visibility": "PUBLIC" },
  "tags": [],
  "visibility": "PUBLIC",
  "updatedAt": "2026-10-06T15:02:00+09:00"
}
```

**에러:** `DRAFT_NOT_FOUND`

---

### 7.5 임시저장 글 삭제
`DELETE /api/drafts/{postId}` 👤 · D-04

**응답 `204 No Content`** · **에러:** `DRAFT_NOT_FOUND`

---

### 7.6 임시저장 글 발행
`POST /api/drafts/{postId}/publish` 👤 · D-05

이어 쓰던 **현재 화면의 내용**을 함께 보내요. 마지막 임시저장 이후에 고친 내용이 있을 수 있어서, 서버는 **저장과 발행을 한 트랜잭션**으로 처리해요.

**요청**: 6.6과 같아요 (이번에는 제목과 본문이 **필수**예요).

**응답 `200 OK`**
```json
{ "id": 31, "username": "nayoung" }
```
- `status`가 `PUBLISHED`로 바뀌고 `publishedAt`이 지금 시각으로 기록돼요. 글 id는 그대로예요.

**에러:** `INVALID_INPUT` · `INVALID_CATEGORY` · `DRAFT_NOT_FOUND`

---

## 8. 댓글

> 볼 수 없는 글(비공개, 비공개 카테고리, 임시저장)의 댓글 API는 주인이 아니면 모두 `POST_NOT_FOUND`(404)예요.

### 8.1 댓글 목록
`GET /api/posts/{postId}/comments` 🔓 · C-01, C-02

**한 번에 모두** 불러와요. 일반 댓글은 오래된 순이고, 각 댓글의 `replies`에 답글이 오래된 순으로 들어 있어요.

**응답 `200 OK`**
```json
{
  "totalCount": 4,
  "comments": [
    {
      "id": 100,
      "content": null,
      "author": null,
      "isDeleted": true,
      "isEdited": false,
      "createdAt": "2026-10-06T15:10:00+09:00",
      "canEdit": false,
      "canDelete": false,
      "canReport": false,
      "replies": [
        {
          "id": 101,
          "content": "감사합니다!\n도움이 됐다니 기뻐요.",
          "author": { "username": "nayoung", "nickname": "나영", "profileImageUrl": null },
          "isDeleted": false,
          "isEdited": true,
          "createdAt": "2026-10-06T15:20:00+09:00",
          "canEdit": true,
          "canDelete": true,
          "canReport": false
        }
      ]
    },
    {
      "id": 103,
      "content": "질문 있어요",
      "author": { "username": null, "nickname": "탈퇴한 사용자", "profileImageUrl": null },
      "isDeleted": false,
      "isEdited": false,
      "createdAt": "2026-10-06T16:00:00+09:00",
      "canEdit": false,
      "canDelete": true,
      "canReport": true,
      "replies": []
    }
  ]
}
```
| 필드 | 설명 |
|---|---|
| `totalCount` | 화면에 보여줄 댓글 수. "삭제된 댓글입니다"는 세지 않아요 |
| `content` | **일반 텍스트**예요. 프론트엔드는 HTML로 렌더링하지 말고 글자 그대로(줄바꿈만 유지) 보여줘요. 삭제된 댓글이면 `null` |
| `author` | 삭제된 댓글이면 `null`. 탈퇴한 회원이면 2.1의 탈퇴 형태 |
| `isDeleted` | `true`면 "삭제된 댓글입니다"로 보여줘요 |
| `isEdited` | `true`면 "(수정됨)"을 붙여요 |
| `canEdit` | 댓글 작성자 본인일 때 `true` |
| `canDelete` | 댓글 작성자 본인 **또는 글의 블로그 주인**일 때 `true` |
| `canReport` | 로그인했고, 내 댓글이 아니고, 삭제되지 않았을 때 `true` |
| `replies` | 답글 목록. 답글에는 `replies` 필드가 없어요 (깊이 1단계) |

**에러:** `POST_NOT_FOUND`

---

### 8.2 댓글·답글 작성
`POST /api/posts/{postId}/comments` 🔒 · C-01, C-02

**요청**
```json
{ "content": "잘 봤어요!", "parentId": null }
```
| 필드 | 필수 | 규칙 |
|---|---|---|
| `content` | ✅ | 공백 제외 1자 이상, 최대 1,000자 |
| `parentId` | | `null`이면 일반 댓글. 값이 있으면 그 댓글의 답글이에요. 부모는 **같은 글의 삭제되지 않은 일반 댓글**이어야 해요 |

**응답 `201 Created`**: 8.1의 댓글 한 개 형태 (일반 댓글이면 `replies: []` 포함)

**에러:** `INVALID_INPUT` · `POST_NOT_FOUND` · `INVALID_PARENT_COMMENT`

---

### 8.3 댓글 수정
`PATCH /api/comments/{commentId}` 👤 작성자 · C-01

**요청**
```json
{ "content": "잘 봤어요! 다음 글도 기대할게요." }
```

**응답 `200 OK`**: 8.1의 댓글 한 개 형태 (`isEdited: true`)

**에러:** `INVALID_INPUT` · `COMMENT_NOT_FOUND` (삭제된 댓글 포함) · `FORBIDDEN` (글 주인이라도 남의 댓글은 수정할 수 없어요)

---

### 8.4 댓글 삭제
`DELETE /api/comments/{commentId}` 👤 작성자, 글 주인 · C-01

| 상황 | 처리 |
|---|---|
| 답글이 있는 일반 댓글 | "삭제된 댓글입니다"로 표시 (`isDeleted = true`) |
| 답글이 없는 댓글, 또는 답글 | 실제로 삭제 (그 댓글의 신고 기록도 함께) |
| 답글을 지운 뒤 부모가 "삭제된 댓글입니다"이고 남은 답글이 없음 | 부모도 함께 실제로 삭제 |

**응답 `204 No Content`**

**에러:** `COMMENT_NOT_FOUND` · `FORBIDDEN`

---

## 9. 좋아요 · 북마크

> 누르기(`PUT`)와 취소(`DELETE`)를 따로 둬요. **같은 요청을 여러 번 보내도 결과가 같아요**(멱등성). 이미 누른 상태에서 `PUT`을 또 보내도 에러 없이 현재 상태로 200 응답해요. 볼 수 없는 글이면 `POST_NOT_FOUND`(404)예요. 본인 글에도 할 수 있어요.

### 9.1 좋아요 누르기 · 취소
`PUT /api/posts/{postId}/like` · `DELETE /api/posts/{postId}/like` 🔒 · C-03

**요청 본문 없음**

**응답 `200 OK`** (두 메서드 모두)
```json
{ "liked": true, "likeCount": 13 }
```
> 프론트엔드는 응답의 `likeCount`로 화면 숫자를 바로 갱신해요.

**에러:** `POST_NOT_FOUND`

---

### 9.2 북마크 추가 · 해제
`PUT /api/posts/{postId}/bookmark` · `DELETE /api/posts/{postId}/bookmark` 🔒 · K-01

**요청 본문 없음**

**응답 `200 OK`** (두 메서드 모두)
```json
{ "bookmarked": true }
```

**에러:** `POST_NOT_FOUND`

---

## 10. 신고

### 10.1 글 신고
`POST /api/posts/{postId}/reports` 🔒 · R-01, R-02

**요청**
```json
{ "reason": "ETC", "detail": "다른 블로그의 글을 그대로 복사했어요." }
```
| 필드 | 필수 | 규칙 |
|---|---|---|
| `reason` | ✅ | `SPAM` / `ABUSE` / `ADULT` / `ETC` |
| `detail` | `ETC`일 때 ✅ | 최대 500자. `ETC`가 아니면 선택이에요 |

**응답 `201 Created`**
```json
{ "id": 7 }
```
> 화면에는 "신고가 접수되었어요"를 보여줘요. MVP에서는 처리 결과를 알려주지 않아요(R-03).

**에러:** `INVALID_INPUT` · `POST_NOT_FOUND` · `CANNOT_REPORT_OWN_CONTENT` · `ALREADY_REPORTED`

---

### 10.2 댓글 신고
`POST /api/comments/{commentId}/reports` 🔒 · R-02, R-04

**요청 · 응답**: 10.1과 같아요.

**에러:** `INVALID_INPUT` · `COMMENT_NOT_FOUND` (볼 수 없는 글의 댓글 포함) · `CANNOT_REPORT_OWN_CONTENT` · `CANNOT_REPORT_DELETED_COMMENT` · `ALREADY_REPORTED`

---

## 11. 이미지

### 11.1 이미지 업로드
`POST /api/images` 🔒 · P-03

썸네일, 본문 이미지, 프로필 사진에 함께 써요. 업로드해서 받은 URL을 글(6.6, 7.1)이나 프로필(4.5) 요청에 넣어요.

**요청** `Content-Type: multipart/form-data`
| 파트 | 필수 | 규칙 |
|---|---|---|
| `file` | ✅ | jpg, png, gif, webp만 가능해요. **확장자와 파일 내용(MIME)을 모두** 검사해요. 최대 5MB예요 |

**응답 `201 Created`**
```json
{ "url": "/uploads/2026/10/3f9a7c2e-1b4d-4e8a-9c0f-5d2e8b7a6c41.png" }
```
- 파일 이름은 사용자가 올린 이름 대신 **UUID**로 바꿔서 저장해요.
- 마크다운 본문에는 `![설명](/uploads/2026/10/...png)` 형태로 넣어요.

**에러:** `INVALID_FILE_TYPE` · `FILE_TOO_LARGE`

> ⚠️ **배포할 때 주의:** Nginx의 기본 업로드 크기 제한(`client_max_body_size`)은 **1MB**예요. 6MB 정도로 늘려 두지 않으면 1MB가 넘는 이미지가 Spring까지 오지도 못하고 Nginx에서 413 에러가 나요. Spring의 `spring.servlet.multipart.max-file-size`도 5MB로 맞춰요.
>
> 업로드했지만 글에 쓰이지 않은 이미지는 MVP에서 정리하지 않아요(ERD 6장, 2차).

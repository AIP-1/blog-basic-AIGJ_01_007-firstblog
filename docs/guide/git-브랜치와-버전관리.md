# Git 브랜치와 버전 관리

> 이 문서를 읽고 나면: Git의 기본 동작을 이해하고, 이 저장소의 브랜치(Git Flow)가 왜 이렇게 생겼는지 알고,
> 기능 추가 · 릴리스 · 핫픽스를 명령어 그대로 따라 할 수 있어요.
>
> 규칙의 원본은 [`CLAUDE.md` 11장](../../CLAUDE.md)이에요. 이 문서는 그 규칙을 **풀어서 설명**해요.

## 목차
1. [Git 기초 개념](#1-git-기초-개념)
2. [브랜치 전략: Git Flow vs GitHub Flow](#2-브랜치-전략-git-flow-vs-github-flow)
3. [이 저장소의 브랜치](#3-이-저장소의-브랜치)
4. [실제 이력으로 보는 Git Flow](#4-실제-이력으로-보는-git-flow)
5. [병합 방식: `--no-ff`](#5-병합-방식---no-ff)
6. [worktree: 여러 작업을 동시에](#6-worktree-여러-작업을-동시에)
7. [병합 충돌 해결 (실제 사례)](#7-병합-충돌-해결-실제-사례)
8. [커밋 메시지: Conventional Commits](#8-커밋-메시지-conventional-commits)
9. [버전 번호: SemVer](#9-버전-번호-semver)
10. [변경 기록: Keep a Changelog](#10-변경-기록-keep-a-changelog)
11. [태그](#11-태그)
12. [PR 템플릿과 브랜치 보호](#12-pr-템플릿과-브랜치-보호)
13. [하지 말아야 할 것](#13-하지-말아야-할-것)
14. [따라 하기 레시피](#14-따라-하기-레시피)
15. [FAQ](#15-faq)

---

## 1. Git 기초 개념

**Git**은 파일의 변경 이력을 저장하는 도구예요. 게임의 "세이브 포인트"를 생각하면 쉬워요. 언제든 예전 세이브로 돌아가 볼 수 있고, 세이브를 여러 갈래로 나눠 따로 진행할 수도 있어요.

| 용어 | 뜻 | 비유 |
|---|---|---|
| **저장소(repository)** | 프로젝트 파일 + 모든 변경 이력. 숨김 폴더 `.git`에 들어 있어요 | 세이브 파일 전체 |
| **커밋(commit)** | "이 순간의 상태"를 메시지와 함께 저장한 것. 고유 번호(해시, 예: `b270fdf`)가 붙어요 | 세이브 포인트 하나 |
| **스테이징(add)** | 다음 커밋에 넣을 변경을 고르는 단계 | 택배 상자에 물건 담기 (커밋 = 상자 봉인) |
| **브랜치(branch)** | 커밋을 이어 가는 "갈래"의 이름표. 실제로는 마지막 커밋을 가리키는 포인터예요 | 평행 세계 |
| **병합(merge)** | 다른 브랜치의 변경을 지금 브랜치로 합치기 | 두 평행 세계를 하나로 |
| **원격(remote)** | 다른 곳(GitHub)에 있는 같은 저장소. 이 저장소의 원격 이름은 `origin`이에요 | 클라우드 세이브 |
| **푸시(push) / 풀(pull)** | 내 커밋을 원격에 올리기 / 원격 커밋을 받아 오기 | 업로드 / 다운로드 |
| **태그(tag)** | 특정 커밋에 붙이는 고정 이름표 (예: `v1.0.0`). 브랜치와 달리 움직이지 않아요 | 책갈피 |

이 저장소의 원격 주소는 이렇게 확인해요.
```bash
git remote -v
# origin  https://github.com/AIP-1/blog-basic-AIGJ_01_007-firstblog.git (fetch)
# origin  https://github.com/AIP-1/blog-basic-AIGJ_01_007-firstblog.git (push)
```

### 하루 작업의 기본 흐름
```bash
git status                  # 지금 무엇이 바뀌었는지
git diff                    # 바뀐 내용 자세히
git add 파일1 파일2          # 커밋할 파일 고르기
git commit                  # 메시지를 쓰고 저장 (8장 형식)
git push                    # GitHub에 올리기
git log --oneline --graph   # 이력을 그림으로
```

### 브랜치가 "포인터"라는 것
```
커밋:   A ── B ── C
                  ↑
               develop      ← develop은 "C"를 가리키는 이름표일 뿐이에요

git switch -c feature/x 하면:
        A ── B ── C
                  ↑
         develop, feature/x  ← 같은 곳을 가리키는 이름표가 하나 더 생겨요

feature/x에서 커밋하면:
        A ── B ── C ── D
                  ↑    ↑
            develop   feature/x  ← 커밋한 브랜치의 이름표만 앞으로 가요
```

---

## 2. 브랜치 전략: Git Flow vs GitHub Flow

**브랜치 전략**은 "어떤 브랜치를 언제 만들고 어디로 합치나"에 대한 팀의 약속이에요. 약속이 없으면 누가 무엇을 어디에 합쳤는지 알 수 없고, 미완성 코드가 운영에 나가기 쉬워요.

| | **GitHub Flow** | **Git Flow** (이 프로젝트) |
|---|---|---|
| 기본 브랜치 | `main` 하나 | `main`(운영) + `develop`(개발 통합) |
| 작업 방식 | `main`에서 가지를 쳐서 작업 → PR → `main`에 병합 → 바로 배포 | `develop`에서 가지를 쳐서 작업 → `develop`에 모음 → `release`로 정리 → `main` |
| 긴급 수정 | 그냥 다른 작업과 같아요 | `hotfix` 브랜치가 따로 있어요 |
| 장점 | 단순해요. 하루에 여러 번 배포하는 팀에 맞아요 | 운영 버전과 개발 중인 버전이 분명히 나뉘어요. 버전 번호를 관리하기 좋아요 |
| 단점 | "언제 무엇이 배포됐나"를 버전으로 묶기 어려워요 | 브랜치가 많아서 처음엔 복잡해요 |

### 왜 이 프로젝트는 Git Flow를 쓰나요?
**`main`에 푸시하면 자동으로 운영 서버에 배포되기 때문이에요** (`.github/workflows/deploy.yml`의 `on: push: branches: [main]`).
GitHub Flow처럼 작업을 바로 `main`에 합치면, 만들다 만 기능도 곧장 https://ownlog.java21.net 에 나가요.
그래서 작업은 `develop`에 먼저 모으고, "이번에 내보낼 것"을 `release` 브랜치에서 확인한 뒤에만 `main`에 넣어요.

```
                 ┌──────── 운영 서버에 자동 배포 (deploy.yml)
                 │
main      ●──────●────────●──────●     ← release·hotfix만 들어와요
                /        /       /
release   ●────●        │       │
         /              │       │
hotfix  │        ●──●───┘       │      ← 운영 긴급 수정
       /                        │
develop ●───●───●───●───●───●───●──    ← 작업 브랜치가 모이는 곳 (ci.yml: 테스트만)
             \     /   \   /
feature       ●───●     ●─●             ← feature/*, fix/*, chore/*, docs/* ...
```

---

## 3. 이 저장소의 브랜치

| 브랜치 | 용도 | 어디서 만들고 → 어디로 합치나 | 푸시하면 |
|---|---|---|---|
| `main` | **운영되는 버전.** 태그 `vX.Y.Z`는 여기에만 | release·hotfix에서만 병합받아요 | 테스트 → **배포** (`deploy.yml`) |
| `develop` | 다음 릴리스를 모으는 통합 브랜치 | 작업 브랜치에서 병합받아요 | 테스트만 (`ci.yml`) |
| `feature/영문-이름` | 새 기능 | `develop` → `develop` | 테스트만 |
| `fix/영문-이름` | `develop`에서 찾은 버그 | `develop` → `develop` | 테스트만 |
| `chore/`·`docs/`·`refactor/`·`test/` | 설정·문서·리팩터링·테스트 | `develop` → `develop` | 테스트만 (※) |
| `release/X.Y.Z` | 릴리스 준비: 버전 올리기, CHANGELOG 정리 | `develop` → `main` **그리고** `develop` | 테스트만 |
| `hotfix/X.Y.Z` | **운영 중 긴급 수정** | `main` → `main` **그리고** `develop` | 테스트만 |

※ `ci.yml`의 푸시 대상은 `develop`, `feature/**`, `fix/**`, `chore/**`, `release/**`, `hotfix/**`예요. `docs/**`, `refactor/**`, `test/**` 브랜치는 **푸시만으로는** CI가 돌지 않고, PR을 열거나 `develop`에 병합해 푸시할 때 돌아요. 또 `*.md`, `docs/`만 바뀐 푸시는 CI·배포 모두 건너뛰어요(`paths-ignore`).

### 이름 규칙
- 영문 소문자와 `-`만 써요. 예: `feature/image-resize`, `hotfix/1.0.1`
- 한글 브랜치 이름은 셸이나 URL에서 깨지기 쉬워서 쓰지 않아요.

### release·hotfix가 "두 곳"으로 합쳐지는 이유
- `main`에 합쳐야 배포돼요.
- `develop`에도 합쳐야 다음 릴리스에 그 수정(버전 번호, CHANGELOG, 버그 수정)이 빠지지 않아요. 안 합치면 다음 릴리스 때 **고친 버그가 되살아나요.**

---

## 4. 실제 이력으로 보는 Git Flow

`git log --oneline --graph --all`로 본 이 저장소의 실제 흐름을 정리했어요 (2026-10-12 ~ 13).

```
태그:             v1.0.0          v1.0.1                          v1.1.0
main      ──────────●───────────────●───────────────────────────────●────
                   ↗ ↘             ↗ ↘                             ↗ ↘
release/1.0.0     ●   ↘   hotfix/1.0.1  ↘          release/1.1.0  ●   ↘
               ce98e63 ↘   ●──●          ↘                  17d4b0c    ↘
                 ↗      ↘  (main에서 출발)  ↘                ↗            ↘
develop  ●──────●────────●────●────●───────●───────●──────●─────────────●──
       e9df1d5         786bf3a  8200b23  cf8d7a3  84407b5  81a715c       440e5c1
          ↑                      ↑        ↑      (충돌 해결)   ↑
   chore/git-flow       docs/portfolio  chore/tester-account  feature/guide
```
화살표(↗ ↘)는 "이 브랜치로 병합"을 뜻해요. 정확한 모양은 `git log --oneline --graph --all`로 확인해요.

| 순서 | 일 | 실제 커밋 |
|---|---|---|
| 1 | **Git Flow 도입.** 그전까지는 브랜치 하나에 바로 커밋했어요. `chore/git-flow` 브랜치에서 규칙·CI·PR 템플릿·CHANGELOG를 만들고 `develop`에 병합 | `577ac72` → `e9df1d5 Merge branch 'chore/git-flow' into develop` |
| 2 | 지난 이력에 태그 `v0.1.0` ~ `v0.6.0`을 붙임 (단계별 완성 시점) | 예: `v0.1.0` → `7ef771b`, `v0.6.0` → `209cc45` |
| 3 | **release/1.0.0**: 버전을 `1.0.0`으로 올리고 CHANGELOG 정리 → `main` 병합 + 태그 `v1.0.0` → `develop`에도 병합 | `ce98e63` → `8faa32f`(main) → `786bf3a`(develop) |
| 4 | 그사이 `develop`에 문서·스크립트 작업이 쌓임 | `8200b23 docs/portfolio`, `cf8d7a3 chore/tester-account` |
| 5 | **hotfix/1.0.1**: 운영에서 타임랩스가 동작하지 않는 것처럼 보이는 문제 발견(트러블슈팅 13·14번). `main`(`8faa32f`)에서 브랜치를 만들어 2번 커밋 → `main` 병합 + 태그 `v1.0.1` | `6ae2e04`, `b270fdf` → `93a8223`(main) |
| 6 | hotfix를 `develop`에도 병합. 이때 **CHANGELOG 충돌**이 났어요 (7장) | `84407b5 Merge branch 'hotfix/1.0.1' into develop` |
| 7 | **feature/guide**: 기능 가이드 화면. `git worktree`로 다른 폴더에서 동시에 작업 (6장) | `87fb236` → `81a715c` |
| 8 | **release/1.1.0**: 버전 1.1.0 → `main` + 태그 `v1.1.0` → `develop` | `17d4b0c` → `5dd79fa`(main) → `440e5c1`(develop) |

> 핵심: hotfix는 `develop`이 아니라 **`main`에서** 만들었어요. `develop`에는 아직 배포하면 안 되는 작업(포트폴리오 문서, 테스터 스크립트)이 있었기 때문이에요. 운영 중인 코드에 **수정만** 얹어서 바로 내보낸 거예요.

직접 확인해 보세요.
```bash
git log --oneline --graph --all | head -40
git log --oneline v1.0.0..v1.0.1     # 1.0.0 → 1.0.1 사이 커밋
```

---

## 5. 병합 방식: `--no-ff`

`develop`에서 `feature/x`를 만들고, 그동안 `develop`에 아무 변화가 없었다면 Git은 기본으로 **fast-forward**(빨리 감기) 병합을 해요. 이름표만 앞으로 옮기는 거라 "병합했다"는 흔적이 남지 않아요.

```
fast-forward (기본):                 --no-ff (이 프로젝트):
develop ●──●──●──●                   develop ●──●───────────●  ← 병합 커밋
              ↑ 어느 커밋이                       \         /
                기능 작업이었는지 몰라요            ●──●──●   feature/x
```

`--no-ff`(no fast-forward)로 하면 항상 **병합 커밋**이 생겨서 다음이 쉬워져요.
- "이 커밋들은 `feature/guide` 작업이었다"가 이력에 남아요 (`Merge branch 'feature/guide' into develop`).
- 기능 하나를 통째로 되돌릴 때 병합 커밋 하나만 `git revert -m 1 <병합커밋>` 하면 돼요.

```bash
git switch develop
git merge --no-ff feature/guide
```

---

## 6. worktree: 여러 작업을 동시에

**문제:** 저장소 폴더는 한 번에 브랜치 하나만 펼쳐 둘 수 있어요. 기능 A를 만드는 중에 급한 B를 하려면 A를 커밋하거나 치워 두고 브랜치를 바꿔야 해요.

**`git worktree`**는 같은 저장소를 **다른 폴더에 다른 브랜치로** 하나 더 펼쳐 줘요. `.git` 이력은 함께 쓰고, 작업 폴더만 따로예요. 책 한 권을 두 군데 펼쳐 놓는 것과 비슷해요.

```
blog-basic-AIGJ_01_007-firstblog/                ← develop (원래 폴더)
blog-basic-AIGJ_01_007-firstblog-feature-guide/  ← feature/guide (worktree)
       ↘ 두 폴더 모두 같은 .git 이력을 써요 ↙
```

이 프로젝트에서는 `feature/guide`(기능 가이드 화면)를 worktree로 만들어, 다른 작업과 동시에 진행했어요. AI 작업자가 여러 개 동시에 일할 때도 각자 worktree를 써요.

```bash
# 만들기: 새 폴더 + 새 브랜치(develop에서 시작)
git worktree add ../blog-basic-AIGJ_01_007-firstblog-feature-guide -b feature/guide develop

git worktree list          # 지금 펼쳐진 폴더들

# 다 쓰고 나면 (병합한 뒤)
git worktree remove ../blog-basic-AIGJ_01_007-firstblog-feature-guide
```

주의할 점
- **같은 브랜치를 두 폴더에서 동시에 열 수 없어요.** (`'develop' is already checked out` 오류)
- worktree 폴더에는 `node_modules`, `.env`가 없어요. 실행하려면 `npm install`, `.env` 복사가 다시 필요해요.
- 두 폴더에서 동시에 백엔드를 켜면 포트(8080)가 겹쳐요.

---

## 7. 병합 충돌 해결 (실제 사례)

### 충돌이란
두 브랜치가 **같은 파일의 같은 부분**을 서로 다르게 고쳤을 때, Git이 어느 쪽이 맞는지 정하지 못하고 사람에게 묻는 상태예요.

### 실제 사례: hotfix/1.0.1 → develop 병합 때 CHANGELOG 충돌
상황
- `develop`의 CHANGELOG `[Unreleased]` 아래에는 그사이 쌓인 항목(테스터 계정 스크립트, 포트폴리오 문서 등)이 있었어요.
- `hotfix/1.0.1`은 `main`에서 출발해서 `[Unreleased]` 바로 아래에 `## [1.0.1] - 2026-10-13` 절을 넣었어요.
- 두 브랜치가 **같은 자리(`[Unreleased]` 바로 아래)** 를 서로 다르게 고쳐서 충돌이 났어요.

`git merge --no-ff hotfix/1.0.1`을 하면 파일에 이런 표시가 생겨요 (모양을 단순화했어요).
```
## [Unreleased]

<<<<<<< HEAD                      ← 지금 브랜치(develop)의 내용
### 추가
- 베타 테스터용 공용 계정 스크립트 ...
- 문서: 포트폴리오 초안 ...
=======                           ← 경계
## [1.0.1] - 2026-10-13            ← 합치려는 브랜치(hotfix/1.0.1)의 내용

### 수정
- 기록 우주 타임랩스가 ...
>>>>>>> hotfix/1.0.1
```

해결 방법: **둘 다 살리되 순서를 맞췄어요.** "아직 배포 안 된 것(`[Unreleased]`)"이 위, "이미 배포된 1.0.1"이 그 아래여야 Keep a Changelog 형식에 맞아요.
```
## [Unreleased]

### 추가
- 베타 테스터용 공용 계정 스크립트 ...      ← develop 쪽 내용 (아직 미배포)
- 문서: 포트폴리오 초안 ...

### 변경
- 트러블슈팅 7번 ...

## [1.0.1] - 2026-10-13                     ← hotfix 쪽 내용 (이미 배포됨)

### 수정
- 기록 우주 타임랩스가 ...

## [1.0.0] - 2026-10-12
```
결과는 병합 커밋 `84407b5`에서 볼 수 있어요: `git show --cc 84407b5 -- CHANGELOG.md`

### 충돌 해결 순서
```bash
git merge --no-ff hotfix/1.0.1     # CONFLICT (content): Merge conflict in CHANGELOG.md
git status                         # "both modified:" 로 표시된 파일 확인
# 1) 편집기로 파일을 열어 <<<<<<< ======= >>>>>>> 표시를 찾아요
# 2) 원하는 최종 모습으로 고치고 표시 세 줄을 모두 지워요
# 3) 빌드·테스트로 확인
git add CHANGELOG.md
git commit                         # 병합 커밋 완성 (메시지는 자동으로 채워져요)
```
- 겁이 나면 언제든 `git merge --abort`로 병합 전으로 돌아갈 수 있어요.
- "한쪽 것만 고르기"가 답인 경우는 드물어요. **두 변경의 의도를 모두 살리는 것**이 기본이에요.
- 코드 충돌이라면 해결 후 반드시 테스트를 다시 돌려요. 문법은 맞아도 동작이 틀릴 수 있어요.

---

## 8. 커밋 메시지: Conventional Commits

커밋 메시지 앞에 **종류(type)** 를 붙이는 약속이에요. 이력만 봐도 무엇이 기능이고 무엇이 수정인지 알 수 있고, 다음 버전 번호를 정할 때도 써요(9장).

```
<type>: <한국어 요약 (무엇을, 왜)>

- 본문: 바뀐 내용을 목록으로
- 요구사항 ID, 테스트 결과

Co-Authored-By: ...
```

| type | 뜻 | 이 저장소의 실제 예 |
|---|---|---|
| `feat` | 새 기능 | `87fb236 feat: 기능 가이드 화면 /guide` |
| `fix` | 버그 수정 | `6ae2e04 fix: 타임랩스를 쓴 순서대로 재생 (글이 몰려 있으면 동작하지 않던 문제)` |
| `docs` | 문서만 | `dccee67 docs: 포트폴리오 초안과 기술 면접 예상 질문 50` |
| `chore` | 설정·도구·잡일 | `577ac72 chore: Git Flow 브랜치 전략과 버전 관리 도입` |
| `refactor` | 동작은 같고 구조만 개선 | (아직 없음) |
| `perf` | 성능 개선 | (아직 없음) |
| `test` | 테스트만 | (아직 없음) |
| `ci` | CI 설정 | (아직 없음) |
| `style` | 공백·포맷 | (아직 없음) |
| `revert` | 되돌리기 | (아직 없음) |

본문이 있는 실제 예 (`6ae2e04`):
```
fix: 타임랩스를 쓴 순서대로 재생 (글이 몰려 있으면 동작하지 않던 문제)

- 시간 비례 재생 → 순서 기준: 별 하나에 0.45초(전체 3~10초), 빈 우주에서 한 개씩
- 운영 측정: ▶ 0.2초에 27개 중 24개가 한꺼번에 나타나고 이후 변화 없음 → 재현 후 수정
- 회귀 점검: 몇 초 안에 쓴 글만 있는 우주에서 하나씩 늘어나는지 (e2e/check-extra 1-2)
- 버전 1.0.1, CHANGELOG, 트러블슈팅 13번
```

하위 호환이 깨지는 변경(예: API 응답 형식 변경)은 `feat!:`처럼 `!`를 붙이고 본문에 `BREAKING CHANGE:`를 적어요. 지금까지 이 저장소에는 없었어요.

---

## 9. 버전 번호: SemVer

**SemVer(유의적 버전)** 는 `MAJOR.MINOR.PATCH` 세 숫자로 "얼마나 크게 바뀌었나"를 알려 주는 약속이에요.

| 자리 | 언제 올리나 (이 프로젝트 기준) | 예 |
|---|---|---|
| **MAJOR** | 기존 사용 방식이 깨질 때 (API 응답 형식, DB 데이터 호환) | 1.1.0 → 2.0.0 |
| **MINOR** | 기존을 깨지 않는 **새 기능** | 1.0.1 → 1.1.0 |
| **PATCH** | **버그 수정**만 | 1.0.0 → 1.0.1 |

- 앞자리를 올리면 뒷자리는 0으로 돌아가요 (1.0.1 → **1.1.0**, 1.0.2가 아니라).
- `0.x.y`는 "아직 첫 정식 버전 전, 무엇이든 바뀔 수 있음"이라는 뜻이에요.

### 이 저장소의 태그와 올린 이유
| 태그 | 내용 (`git tag -n`) | 왜 이 번호? |
|---|---|---|
| `v0.1.0` | 백엔드 REST API 38개 완성 (3단계) | 첫 동작하는 단위. 아직 정식 전이라 0.x |
| `v0.2.0` | 프론트엔드 화면 15개, MVP 기능 완성 (4단계) | 새 기능 → MINOR |
| `v0.3.0` | 수업 서버 첫 배포, README | 새 기능(배포) → MINOR |
| `v0.4.0` | 기록 유니버스 (타임캡슐, `[[글]]`, 기록 우주, 잔디) | 새 기능 → MINOR |
| `v0.5.0` | 기록 유니버스 확장 | 새 기능 → MINOR |
| `v0.6.0` | 보안 수정, 디자인 개편, 트러블슈팅 문서 | 수정 + 변경 묶음 → MINOR |
| `v1.0.0` | 첫 정식 버전 (발표·제출 버전) | "이제 안정된 공개 버전"이라는 선언 → 1.0.0 |
| `v1.0.1` | 타임랩스 순서 재생 | **버그 수정만** → PATCH |
| `v1.1.0` | 기능 가이드 화면, 베타 테스트 준비 | **새 기능**(`/guide`) → MINOR |

`v0.1.0` ~ `v0.6.0`은 Git Flow를 도입할 때(`chore/git-flow`) **지난 이력에 나중에 붙인** 태그예요.

### 버전은 세 곳을 함께 올려요
| 파일 | 방법 |
|---|---|
| `backend/build.gradle` | `version = '1.1.0'` 직접 수정 |
| `frontend/package.json` (+ `package-lock.json`) | `cd frontend && npm version 1.1.0 --no-git-tag-version` |
| `CHANGELOG.md` | `[Unreleased]` 내용을 `## [1.1.0] - 날짜` 아래로 옮기기 |

`--no-git-tag-version`을 붙이는 이유: 안 붙이면 npm이 멋대로 커밋과 태그를 만들어요. 태그는 `main`에서 직접 붙여요.

---

## 10. 변경 기록: Keep a Changelog

커밋 로그는 개발자용이라 사용자가 읽기 어려워요. **CHANGELOG**는 "이번 버전에서 사용자에게 무엇이 달라졌나"를 사람 말로 정리한 문서예요. 형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/)를 따라요.

규칙
- 맨 위에 `## [Unreleased]`: 작업을 `develop`에 합칠 때마다 여기에 한 줄씩 추가해요.
- 릴리스할 때 `[Unreleased]` 내용을 `## [X.Y.Z] - 날짜`로 옮기고, `[Unreleased]`는 비워 둬요.
- 분류: `추가`(Added) · `변경`(Changed) · `수정`(Fixed) · `보안`(Security) · `삭제`(Removed)
- 맨 아래에 버전별 비교 링크 (`[1.1.0]: .../compare/v1.0.1...v1.1.0`). 새 버전을 만들면 링크도 한 줄 추가해요.

```markdown
## [Unreleased]

## [1.1.0] - 2026-10-13

### 추가
- 📖 기능 가이드 화면 `/guide`: ...
```

---

## 11. 태그

태그는 "이 커밋이 1.1.0 버전이다"라는 고정 이름표예요. 브랜치는 커밋할 때마다 움직이지만 태그는 **움직이지 않아요.** 그래서 나중에 "1.0.0 때 코드"를 정확히 꺼내 볼 수 있어요.

| 종류 | 만드는 법 | 담기는 것 |
|---|---|---|
| 가벼운 태그 | `git tag v1.0.0` | 이름표만 |
| **주석 태그** (이 프로젝트) | `git tag -a v1.0.0 -m "v1.0.0: 한 줄 요약"` | 만든 사람, 날짜, 메시지 |

이 저장소의 태그는 모두 주석 태그예요 (`git tag -n`으로 메시지를 볼 수 있어요).

```bash
git tag -n                           # 태그와 메시지 목록
git show v1.0.1                      # 태그 정보와 그 커밋
git switch --detach v1.0.0           # 1.0.0 시점 코드 구경 (끝나면 git switch develop)
git push origin v1.1.0               # 태그는 따로 푸시해요 (또는 git push --tags)
```

- 태그는 **`main`의 병합 커밋에만** 붙여요. (`v1.1.0` → `5dd79fa Merge branch 'release/1.1.0' into main`)
- 한번 푸시한 태그는 **지우거나 옮기지 않아요.** 다른 사람(과 CHANGELOG 링크)이 그 태그를 믿고 있어요. 잘못 붙였다면 다음 PATCH 버전을 새로 내요.

---

## 12. PR 템플릿과 브랜치 보호

### PR(Pull Request)이란
"제 브랜치를 `develop`에 합쳐 주세요"라는 **요청서**예요. GitHub 화면에서 바뀐 코드를 보고, 의견을 남기고, CI 결과를 확인한 뒤 병합 버튼을 눌러요.

### PR 템플릿
`.github/pull_request_template.md`가 있으면 GitHub에서 PR을 열 때 본문이 자동으로 채워져요. 이 저장소의 템플릿 항목:

| 절 | 적는 것 |
|---|---|
| 무엇을 바꿨나요 / 왜 바꿨나요 | 한두 문장, 요구사항 ID |
| 변경 종류 | feat / fix / 보안 / refactor·perf / docs·chore·test 체크 |
| 확인한 것 | `./gradlew test`, `npm run lint`·`build`, e2e, 권한 케이스, 문서 동기화, CHANGELOG, 트러블슈팅 |
| DB·배포 영향 | 새 Flyway 마이그레이션, 배포 설정 변경 여부 |
| 스크린샷 | 화면 변경 시 |

> 현재 작업 환경에는 `gh`(GitHub CLI)가 없어서 AI가 PR을 직접 만들지 않아요. 대신 브랜치를 푸시하고, 검증을 마친 뒤 로컬에서 `--no-ff`로 병합하고, 템플릿 항목을 보고에 담아요 (CLAUDE.md 11장). GitHub 화면에서 PR로 보고 싶으면 브랜치만 푸시해 달라고 하면 돼요.

### 브랜치 보호(Branch protection)란
GitHub이 특정 브랜치에 **아무나, 아무렇게나** 푸시하지 못하게 막아 주는 설정이에요. 사람의 실수(예: `main`에 바로 푸시 = 바로 배포)를 시스템이 막아 줘요.

**이 설정은 저장소에 코드로 들어 있지 않아요.** 저장소 관리자(Admin)가 GitHub 화면에서 직접 켜야 해요. 지금 켜져 있는지는 저장소 주인이 아래 위치에서 확인해 주세요.

> GitHub 저장소 → **Settings** → **Branches** (또는 **Rules → Rulesets**) → `main`, `develop`에 규칙 추가

추천 설정
| 설정 | `main` | `develop` | 이유 |
|---|---|---|---|
| Require a pull request before merging | ✅ (선택) | 선택 | 직접 푸시 대신 PR로만 합치기 |
| Require status checks to pass (`test` 작업) | ✅ | ✅ | 테스트가 실패한 코드가 들어오지 않게 |
| Block force pushes | ✅ | ✅ | 이력이 지워지는 것 방지 |
| Restrict deletions | ✅ | ✅ | 브랜치 삭제 방지 |
| Require linear history | ❌ | ❌ | 우리는 `--no-ff` 병합 커밋을 쓰므로 켜면 안 돼요 |

주의: "PR 필수"를 켜면 지금처럼 **로컬에서 병합 후 푸시**하는 방식이 막혀요. 1인 개발이라면 ① PR 필수는 끄고 force push·삭제 금지만 켜거나, ② PR 필수를 켜고 GitHub 화면에서 병합하거나, ③ 관리자 우회(bypass)를 허용하는 것 중에서 골라요. Organization 저장소는 Organization 설정에 따라 메뉴가 보이지 않을 수 있어요 ([github-organization과-readme.md](github-organization과-readme.md) 참고).

---

## 13. 하지 말아야 할 것

| 하지 말 것 | 왜 위험한가 | 대신 |
|---|---|---|
| `main`·`develop`에 직접 커밋 | 검증 안 된 코드가 섞이고, `main`이면 바로 배포돼요 | 작업 브랜치 → `--no-ff` 병합 |
| `git push --force` | 원격의 커밋을 덮어써서 **남의 작업이 사라질 수 있어요** | 실수는 새 커밋으로 고치거나 `git revert` |
| 공유 브랜치 `rebase` | rebase는 커밋을 **새로 만들어** 해시가 바뀌어요. 이미 받아 간 사람의 이력과 어긋나요 | 공유 브랜치는 merge만 |
| 푸시한 커밋 `--amend`, `reset` 후 푸시 | 위와 같아요 (이력 다시 쓰기) | 푸시 전이라면 괜찮아요. 푸시 후엔 새 커밋 |
| 태그 지우기·옮기기 | 배포 기록과 CHANGELOG 링크가 틀어져요 | 새 PATCH 버전 |
| `.env`, 비밀번호, 배포 설명서 PDF 커밋 | 이력에 한번 들어가면 지워도 남아요 | `.gitignore`, GitHub Secrets |
| 한 커밋에 여러 기능 섞기 | 되돌리거나 원인을 찾기 어려워요 | 기능 하나 = 브랜치 하나 |

> 실수로 비밀값을 커밋해서 **푸시까지 했다면**, 파일을 지우는 것만으로는 부족해요. 이력에 남아 있으니 **그 비밀값 자체를 바꿔야**(DB 비밀번호 변경, JWT 비밀키 재발급) 해요.

---

## 14. 따라 하기 레시피

### A. 기능 시작하기
```bash
git switch develop
git pull                                   # 최신 develop 받기
git switch -c feature/image-resize         # 새 브랜치 (develop에서 출발)

# ... 작업 ...
git add 바뀐-파일들
git commit                                 # "feat: 업로드 이미지 크기 줄이기" 형식
git push -u origin feature/image-resize    # 처음 푸시할 때만 -u (이후엔 git push)
```

### B. 기능 마치기
```bash
# 1) 완료 기준 확인 (CLAUDE.md 7장)
cd backend && ./gradlew test && cd ..
cd frontend && npm run lint && npm run build && cd ..
# 화면 변경이면 node e2e/run-all.mjs <관련 점검>
# CHANGELOG.md [Unreleased]에 한 줄 추가, 관련 문서 갱신 후 커밋

# 2) develop에 병합
git switch develop
git pull
git merge --no-ff feature/image-resize
git push

# 3) 다 쓴 브랜치 정리
git branch -d feature/image-resize
git push origin --delete feature/image-resize
```

### C. 릴리스하기 (예: 1.1.0 → 1.2.0)
```bash
# 1) 무엇이 바뀌었나 보고 다음 버전 정하기 (feat 있으면 MINOR, fix만 있으면 PATCH)
git log --oneline v1.1.0..develop

# 2) release 브랜치
git switch develop && git pull
git switch -c release/1.2.0
#   - backend/build.gradle: version = '1.2.0'
#   - cd frontend && npm version 1.2.0 --no-git-tag-version
#   - CHANGELOG: [Unreleased] → ## [1.2.0] - 2026-MM-DD, 맨 아래 비교 링크 추가
#   - 테스트·lint·build
git commit -am "chore: 1.2.0 릴리스 준비 (버전 올리기, CHANGELOG 정리)"

# 3) main에 병합 + 태그 → 푸시하면 배포돼요
git switch main && git pull
git merge --no-ff release/1.2.0
git tag -a v1.2.0 -m "v1.2.0: 한 줄 요약"
git push origin main v1.2.0

# 4) develop에도 병합
git switch develop
git merge --no-ff release/1.2.0
git push

# 5) 정리 + 배포 확인
git branch -d release/1.2.0
curl https://ownlog.java21.net/api/topics     # 응답이 오면 배포 성공 (Actions 화면에서도 확인)
```

### D. 핫픽스하기 (운영 긴급 수정, 예: 1.2.0 → 1.2.1)
```bash
git switch main && git pull
git switch -c hotfix/1.2.1                    # main에서 출발! (develop 아님)
# ... 버그 수정 + 회귀 테스트 추가 ...
# 버전 세 곳을 1.2.1로, CHANGELOG에 ## [1.2.1] - 날짜 / ### 수정
git commit -am "fix: ..."

git switch main
git merge --no-ff hotfix/1.2.1
git tag -a v1.2.1 -m "v1.2.1: 한 줄 요약"
git push origin main v1.2.1                   # 배포

git switch develop
git merge --no-ff hotfix/1.2.1                # CHANGELOG 충돌이 나면 7장처럼
git push
git branch -d hotfix/1.2.1
```

---

## 15. FAQ

**Q. 지금 내가 어느 브랜치에 있는지 어떻게 알아요?**
`git status` 첫 줄이나 `git branch`(별표 `*`가 지금 브랜치)를 봐요.

**Q. 커밋 안 한 변경이 있는데 브랜치를 바꾸고 싶어요.**
가장 안전한 방법은 그 브랜치에 임시 커밋(`git commit -m "wip: 작업 중"`)을 하는 거예요. 아니면 6장의 worktree로 다른 폴더에서 작업해요.

**Q. 마지막 커밋 메시지를 잘못 썼어요.**
**아직 푸시 전이면** `git commit --amend`로 고쳐요. 푸시했다면 그대로 두는 게 나아요.

**Q. 실수로 `develop`에 직접 커밋했어요 (푸시 전).**
```bash
git switch -c feature/실수-옮기기     # 지금 커밋을 가진 새 브랜치를 만들고
git switch develop
git reset --hard origin/develop     # develop을 원격 상태로 되돌려요 (푸시 전이라 안전)
```
푸시까지 했다면 이력을 다시 쓰지 말고 그대로 두거나 `git revert`로 되돌리는 커밋을 만들어요.

**Q. `main`에 푸시했는데 배포가 안 돼요.**
`*.md`, `docs/`, `scripts/`만 바뀐 푸시는 배포하지 않게 되어 있어요(`deploy.yml`의 `paths-ignore`). 그 밖이라면 GitHub의 **Actions** 탭에서 실패한 단계를 확인해요. Actions 화면의 **Run workflow** 버튼(`workflow_dispatch`)으로 직접 배포할 수도 있어요.

**Q. `develop`에 푸시했는데 CI가 안 돌아요.**
문서만 바꿨다면 정상이에요 (`ci.yml`도 `**.md`, `docs/**`를 무시해요).

**Q. release 브랜치에서 버그를 찾았어요.**
release 브랜치에서 바로 고치고 커밋해요. 나중에 `develop`에도 병합하니까 수정이 사라지지 않아요.

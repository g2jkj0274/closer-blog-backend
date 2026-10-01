# c1oser.dev API 명세서

- 상태: 초안 (2026-10-01)
- 범위: MVP(P0). P1 이후 API는 12절에 방향만 적었다.
- 함께 읽을 문서: [기능 명세서](functional-spec.md), [MVP 범위](mvp-scope.md), [기술 스택 결정](tech-stack.md), [DB 설계](erd.md)
- MVP 범위 5절의 API 목록을 구체화한 문서다. 처음 초안(2026-09-30)에서 달라진 점은 13절에 있다.
- 구현 뒤에는 springdoc이 만드는 OpenAPI 문서가 기준이 된다. 그때까지는 이 문서가 프런트와의 계약이다.

## 1. 공통 규약

### 1.1 기본

| 항목 | 값 |
|---|---|
| 기본 경로 | `/api/v1`. 이 문서의 경로는 모두 이 아래다 |
| 형식 | 요청·응답 모두 JSON (`application/json; charset=utf-8`) |
| 필드 이름 | camelCase |
| 시각 | ISO-8601 UTC (`2026-10-01T03:20:00Z`) |
| ID | 숫자 |
| 성공 응답 | 감싸지 않는다. 자원 하나는 객체, 목록은 1.5절의 페이지 객체 |
| 만들기 성공 | `201` + `Location` 헤더 |
| 본문 없는 성공 | `204` |

### 1.2 인증

[기술 스택 3.3절](tech-stack.md#33-인증-jwt-액세스-토큰--리프레시-토큰)대로 액세스 토큰과 리프레시 토큰을 쓴다.

| 토큰 | 전달 | 수명 |
|---|---|---|
| 액세스 | `Authorization: Bearer <jwt>` | 15분 |
| 리프레시 | 쿠키 `refresh_token` (`HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth`) | 로그인 유지면 30일(`Max-Age`), 아니면 세션 쿠키 |

- 액세스 토큰은 HS256 JWT다. 클레임은 `sub`(사용자 id), `username`, `iat`, `exp`. 서명 키는 환경 변수 `JWT_SECRET`(32바이트 이상)이다.
- 로그인 없이 부를 수 있는 API는 `/auth/*` 다섯 개뿐이다. 나머지는 토큰이 없거나 틀리면 401이다.
- 리프레시 토큰을 쓰는 `/auth/refresh`, `/auth/logout`은 쿠키로만 사용자를 안다. `SameSite=Lax`라 다른 사이트에서 보낸 POST에는 쿠키가 실리지 않는다.
- CORS는 프런트 출처 하나만 허용하고 `Access-Control-Allow-Credentials: true`를 준다. 출처 값은 도메인 구성(기술 스택 5절 3번)이 정해지면 넣는다.
- 도움말(`/help`)과 약관(AUTH-06)은 프런트의 정적 화면이다. API가 없다.

### 1.3 권한

| 대상 | 읽기 | 쓰기 |
|---|---|---|
| 글 | 휴지통에 없고, 내 글이거나 `mode = 644` | 소유자만 |
| 휴지통의 글 | 소유자만 | 소유자만 |
| 폴더 | 로그인한 누구나. 안의 글은 위 규칙으로 거른다 | 소유자만 |
| 사람 | 로그인한 누구나. 이메일과 연락처는 본인에게만 | 본인만 |

**검사 순서는 "읽을 수 있는가 → 고칠 수 있는가"다.**

1. 대상이 없거나 읽을 수 없으면 **404**.
2. 읽을 수 있지만 소유자가 아니면 **403**.

비공개 글이나 남의 휴지통 글에 쓰기 요청을 보내도 1단계에서 404가 된다. 403은 "있다는 것을 이미 아는" 대상에만 나온다. 이 규칙은 post 도메인의 `PostAccessPolicy` 한 곳에 둔다.

**개수도 읽을 수 있는 글만 센다.** 폴더의 글 수, 사람의 글 수, 태그의 글 수, 검색 결과 수가 모두 그렇다.

### 1.4 오류

```json
{
  "code": "POST_NAME_TAKEN",
  "message": "같은 폴더에 pointer.md가 이미 있습니다.",
  "errors": []
}
```

- `code`: 프런트가 분기하는 키.
- `message`: 터미널 오류 줄에 그대로 찍을 수 있는 한국어 문장.
- `errors`: `VALIDATION_ERROR`일 때만 `{ "field", "reason" }` 목록이 들어 있다. 그 밖에는 빈 배열이다.

| 상태 | code | 언제 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 형식, 길이, 필수값, 이름 규칙, 폴더 깊이 위반 |
| 400 | `INVALID_PATH` | `GET /fs`의 경로 문법이 틀림 |
| 401 | `UNAUTHORIZED` | 토큰이 없음, 만료, 위조. 리프레시 토큰 재사용 |
| 401 | `INVALID_CREDENTIALS` | 아이디·이메일 또는 비밀번호가 틀림. 어느 쪽인지 알리지 않는다 |
| 403 | `FORBIDDEN` | 읽을 수 있지만 고칠 권한이 없음 |
| 404 | `NOT_FOUND` | 없음. 읽을 수 없는 글, 휴지통의 글도 같다 |
| 409 | `USERNAME_TAKEN`, `EMAIL_TAKEN` | 가입 중복 |
| 409 | `FOLDER_NAME_TAKEN`, `POST_NAME_TAKEN` | 같은 폴더 안 이름 중복 |
| 409 | `FOLDER_NOT_EMPTY` | `rmdir` 대상에 하위 폴더, 글, 휴지통 글이 있음 |
| 409 | `POST_VERSION_CONFLICT` | 다른 곳에서 먼저 고친 글을 저장함 |
| 423 | `ACCOUNT_LOCKED` | 로그인 잠금 중. `message`에 풀리는 시각 |
| 500 | `INTERNAL_ERROR` | 그 밖 |

프런트는 404를 "없는 경로" 화면(ERR-01)으로 보낸다. 오타, 지운 글, 비공개 글이 모두 같은 화면이 된다.

### 1.5 목록

긴 목록은 커서로 나눠 받는다 (기능 명세서 6절 "더 불러오기").

```
GET /users?size=20&cursor=eyJ...
```

```json
{ "items": [ ... ], "nextCursor": "eyJ..." }
```

- `size`: 기본 20, 최대 50. 마지막 페이지면 `nextCursor`가 `null`이다.
- 커서는 정렬 키와 id를 base64url로 감싼 문자열이다. 프런트는 해석하지 않고 그대로 돌려보낸다.
- 정렬 키가 바뀌는 목록(글 수 순, 최근 수정순)은 페이지 사이에 데이터가 바뀌면 항목이 겹치거나 빠질 수 있다. 프런트는 id로 중복을 걸러 낸다.

### 1.6 경로

| 이름 | 예 | 뜻 |
|---|---|---|
| `path` | `python/basic`, `python/pointer.md` | 소유자의 홈 기준 상대 경로. 홈은 `''` |
| `displayPath` | `~/python/pointer.md`, `/users/kim/python/pointer.md` | 화면에 보이는 절대 경로. **보는 사람 기준**이라 내 것이면 `~`, 남의 것이면 `/users/{username}`으로 시작한다 |

- 서버는 요청에서 절대 경로(`displayPath` 형식)만 받는다. `cd ..`, `cat pointer.md` 같은 상대 경로는 프런트가 현재 위치와 합쳐 절대 경로로 만든다 (MVP 범위 5절 "명령어 해석은 프런트가 한다").
- `/users/{내 아이디}/...`로 요청해도 응답의 `displayPath`는 `~`로 시작한다.

## 2. 패키지 구조

```
com.closer.blog
├── common              횡단 관심사. 도메인을 모른다
│   ├── config          SecurityConfig, CorsConfig, OpenApiConfig, SchedulingConfig
│   ├── error           ErrorCode, ApiException, GlobalExceptionHandler, ErrorResponse
│   ├── security        JwtProvider, @CurrentUser
│   └── web             CursorPage, CursorCodec, PathSyntax
│
│   ── 도메인: 엔티티를 갖고 쓰기를 맡는다 ──
├── user                User. 가입 시 사용자 생성, 프로필 수정
├── auth                AuthController /auth/*. RefreshToken, LoginAttemptPolicy
├── folder              Folder. FolderController /folders/*, /users/{username}/tree
├── tag                 Tag. 이름 정규화, 찾거나 만들기
├── post                Post, PostTag, PostAccessPolicy, PostVisibility
│                       PostController /posts/*, TrashController /trash/*
│                       UserPostController /users/{username}/posts, TrashPurgeScheduler
│
│   ── 조회: 테이블이 없고 여러 도메인을 읽기만 한다 ──
├── fs                  FsController /fs. 경로 → 폴더 또는 글
├── search              SearchController /search/*, TagQueryController /tags/*
└── profile             MeController /me/*, UserController /users, /users/{username}
```

각 패키지 안은 `controller → service → domain(엔티티, 저장소)` 방향이고, `dto`는 controller와 service가 함께 쓴다.

패키지 사이의 의존은 아래 방향만 허용한다. 순환이 없다.

| 패키지 | 의존하는 패키지 |
|---|---|
| user | — |
| tag | — |
| folder | user |
| auth | user, folder (가입할 때 홈 폴더를 만든다) |
| post | user, folder, tag |
| fs | user, folder, post |
| search | user, folder, post, tag |
| profile | user, folder, post |

- 다른 패키지는 그 패키지의 service로 부른다. 저장소를 직접 부르지 않는다.
- 예외로 search와 profile은 여러 테이블을 묶는 네이티브 쿼리를 자기 저장소에 둘 수 있다. 읽기 전용이고, 글을 걸러야 하면 반드시 `PostVisibility`의 조건을 쓴다 ([DB 설계 5.1절](erd.md#51-읽기-권한)).
- 이 규칙은 ArchUnit 같은 테스트로 고정하는 것을 권한다.

## 3. 엔드포인트 목록

| 패키지 | 메서드 | 경로 | 용도 | 명령어·화면 | 요구사항 |
|---|---|---|---|---|---|
| auth | POST | `/auth/signup` | 회원가입 | `adduser` | AUTH-01 |
| auth | GET | `/auth/availability` | 아이디·이메일 중복 확인 | 회원가입 화면 | AUTH-01 |
| auth | POST | `/auth/login` | 로그인 | `login` | AUTH-02, 03 |
| auth | POST | `/auth/refresh` | 액세스 토큰 재발급 | 앱 시작, 401 | AUTH-03, 05 |
| auth | POST | `/auth/logout` | 로그아웃 | `logout` | AUTH-04 |
| profile | GET | `/me` | 내 정보 | `whoami` | ME-01 |
| profile | PUT | `/me/profile` | 프로필 수정 | `config` | ME-02 |
| profile | GET | `/me/summary` | 홈 요약 | `neofetch` | SH-07 |
| profile | GET | `/users` | 사람 목록·찾기 | `ls /users`, `find @kim` | USER-01, 02 |
| profile | GET | `/users/{username}` | 프로필 | `cd /users/kim` | USER-03 |
| post | GET | `/users/{username}/posts` | 그 사람의 글 (최근 수정순) | `cd /users/kim` | USER-03 |
| folder | GET | `/users/{username}/tree` | 폴더 트리 | 왼쪽 트리 | DIR-05 |
| folder | POST | `/folders` | 폴더 만들기 | `mkdir` | DIR-01 |
| folder | GET | `/folders/{id}` | 폴더 안 목록 | `ls`, `cd` | DIR-02, 03, 06 |
| folder | DELETE | `/folders/{id}` | 빈 폴더 지우기 | `rmdir` | DIR-04 |
| fs | GET | `/fs` | 경로 → 폴더 또는 글 | `cd`, `ls`, `cat`, `vi` | DIR-02, POST-02, ERR-01 |
| post | POST | `/posts` | 글 만들기 | `touch` 뒤 첫 `:w` | POST-01, 04, TAG-01 |
| post | GET | `/posts/{id}` | 글 읽기 | `cat` | POST-02 |
| post | PATCH | `/posts/{id}` | 수정, 이름 변경, 이동, 공개 범위, 태그 | `:w`, `mv`, `chmod` | POST-05~07, TAG-01 |
| post | DELETE | `/posts/{id}` | 휴지통으로 | `rm` | POST-08 |
| post | GET | `/trash` | 휴지통 목록 | `cd ~/.trash` | POST-09 |
| post | POST | `/trash/{id}/restore` | 복구 | `u`, 휴지통 복구 | POST-08, 09 |
| post | DELETE | `/trash/{id}` | 완전 삭제 | 휴지통에서 `rm -f` | POST-09 |
| search | GET | `/search/content` | 내용 검색 | `grep` | SRCH-01, 03 |
| search | GET | `/search/names` | 이름·경로 검색 | `find` | SRCH-02, 03 |
| search | GET | `/tags` | 태그 자동완성 | 편집기 태그 입력 | TAG-01 |
| search | GET | `/tags/{name}/posts` | 태그별 글 목록 | 태그 클릭 | TAG-02 |

## 4. 공통 응답 객체

**UserRef**

```json
{ "username": "kim", "displayName": "김철수" }
```

**FolderSummary**: 폴더 카드와 트리 노드에 쓴다.

```json
{
  "id": 7,
  "name": "basic",
  "path": "python/basic",
  "displayPath": "~/python/basic",
  "postCount": 3,
  "folderCount": 0,
  "createdAt": "2026-09-30T02:00:00Z"
}
```

`postCount`는 그 폴더 바로 아래의 글 중 보는 사람이 읽을 수 있는 수다. `folderCount`는 바로 아래 하위 폴더 수다.

**PostSummary**: 목록에 쓴다.

```json
{
  "id": 42,
  "fileName": "pointer.md",
  "path": "python/pointer.md",
  "displayPath": "~/python/pointer.md",
  "title": "포인터를 그림으로 다시 이해하기",
  "tags": ["포인터", "예제"],
  "mode": 644,
  "owner": { "username": "kim", "displayName": "김철수" },
  "createdAt": "2026-09-30T02:00:00Z",
  "updatedAt": "2026-10-01T03:20:00Z"
}
```

**PostDetail**: 글 하나. PostSummary에 아래를 더한다.

```json
{
  "content": "# 포인터\n\n...",
  "folder": { "id": 2, "path": "python", "displayPath": "~/python" },
  "charCount": 1830,
  "readingMinutes": 4,
  "version": 3,
  "editable": true
}
```

- `readingMinutes`는 `ceil(charCount / 500)`이다. 본문이 비면 0이다.
- `editable`은 보는 사람이 소유자인지다. 프런트는 이 값으로 `vi`, `mv`, `rm` 버튼을 켠다.
- `version`은 저장할 때 그대로 돌려보낸다 (8절 `PATCH /posts/{id}`).

## 5. auth

### POST /auth/signup

회원가입. 같은 트랜잭션에서 홈 폴더도 만든다.

```json
{
  "username": "kim",
  "email": "kim@example.com",
  "password": "********",
  "termsAgreed": true,
  "mailOptIn": false
}
```

| 필드 | 규칙 |
|---|---|
| username | 필수. 영문·숫자 3~20자. 대문자는 소문자로 바꿔 저장한다 |
| email | 필수. 이메일 형식, 255자 이하 |
| password | 필수. 8자 이상, **UTF-8로 72바이트 이하**. BCrypt가 72바이트까지만 쓰기 때문이다. 한글은 한 글자가 3바이트다 |
| termsAgreed | `true`여야 한다 |
| mailOptIn | 선택. 기본 `false` |

비밀번호 확인 입력은 프런트가 비교한다. 서버로 보내지 않는다.

응답 `201`

```json
{ "id": 1, "username": "kim", "displayName": "kim", "joinedAt": "2026-10-01T03:20:00Z" }
```

오류: 400 `VALIDATION_ERROR`, 409 `USERNAME_TAKEN`, 409 `EMAIL_TAKEN`.

가입만 하고 로그인은 하지 않는다. 프런트가 이어서 `POST /auth/login`을 부른다 (MVP 범위 4절 시나리오 2).

### GET /auth/availability

회원가입 화면에서 입력하는 대로 중복을 확인한다. 둘 중 하나만 보낸다.

```
GET /auth/availability?username=kim
GET /auth/availability?email=kim@example.com
```

응답 `200`: `{ "available": false }`

형식이 틀리면 400 `VALIDATION_ERROR`다. 이메일 가입 여부가 드러나지만, 가입할 때 `EMAIL_TAKEN`으로 어차피 드러나는 정보다.

### POST /auth/login

```json
{ "login": "kim", "password": "********", "keepLoggedIn": true }
```

`login`에 `@`가 있으면 이메일(대소문자 무시), 없으면 아이디로 찾는다.

응답 `200` + `Set-Cookie: refresh_token=...`

```json
{
  "accessToken": "eyJ...",
  "expiresIn": 900,
  "user": { "id": 1, "username": "kim", "displayName": "김철수" }
}
```

| 상황 | 응답 |
|---|---|
| 계정 없음 | 401 `INVALID_CREDENTIALS`. 응답 시간으로 계정 유무가 드러나지 않게 가짜 해시로 BCrypt 비교를 한 번 한다 |
| 잠금 중 | 423 `ACCOUNT_LOCKED`. 비밀번호를 확인하지 않는다 |
| 비밀번호 틀림 | 401 `INVALID_CREDENTIALS`. 5번째면 15분 잠근다 ([DB 설계 4.1절](erd.md#41-users-user)) |
| 성공 | 실패 횟수를 0으로 되돌리고 토큰 가족을 새로 만든다 |

`keepLoggedIn`이 `true`면 쿠키에 `Max-Age=2592000`(30일)을 붙인다. `false`면 세션 쿠키로 주고, DB 만료는 24시간이다.

### POST /auth/refresh

본문이 없다. 쿠키의 리프레시 토큰으로 액세스 토큰을 새로 받는다.

응답 `200`: `POST /auth/login`과 같은 본문.

| 받은 토큰 | 처리 |
|---|---|
| 현재 토큰 | 회전한다. 새 `Set-Cookie`를 준다 |
| 30초 안에 회전된 토큰 | 다른 탭이 방금 회전한 경우다. 액세스 토큰만 주고 쿠키는 건드리지 않는다 |
| 30초보다 전에 회전된 토큰 | 재사용으로 보고 그 가족을 모두 폐기한다. 401 |
| 없음, 만료 | 401 |

401일 때는 쿠키를 지우는 `Set-Cookie`(`Max-Age=0`)를 함께 보낸다. 자세한 규칙은 [DB 설계 4.2절](erd.md#42-refresh_tokens-auth)에 있다.

프런트는 액세스 토큰을 메모리에만 둔다. 앱을 열 때, 그리고 다른 API가 401을 주었을 때 이 API를 부른다. 401을 준 원래 요청은 새 토큰으로 한 번만 다시 보낸다.

### POST /auth/logout

쿠키의 토큰 가족을 지우고 쿠키도 지운다. 액세스 토큰이 없어도 된다. 쿠키가 없거나 이미 지워졌어도 `204`다.

응답 `204`.

액세스 토큰은 남은 수명(최대 15분) 동안 유효하다. 프런트가 메모리에서 버리므로 따로 막지 않는다.

## 6. profile

### GET /me

```json
{
  "id": 1,
  "username": "kim",
  "displayName": "김철수",
  "email": "kim@example.com",
  "bio": "포인터 그림 그리는 사람",
  "contact": "github.com/kim",
  "joinedAt": "2026-09-30T02:00:00Z",
  "postCount": 12,
  "publicPostCount": 9,
  "folderCount": 5
}
```

- `postCount`: 휴지통을 뺀 내 글 전체. `publicPostCount`: 그중 644 (ME-01 "공개 글 수").
- `folderCount`: 홈을 뺀 내 폴더 수.

### PUT /me/profile

`config` 화면에서 저장한다. 세 필드를 모두 보내고, 보낸 값으로 바꾼다.

```json
{ "displayName": "김철수", "bio": "한 줄 소개", "contact": "github.com/kim" }
```

| 필드 | 규칙 |
|---|---|
| displayName | 1~40자. 앞뒤 공백은 잘라 낸다 |
| bio | 0~100자. `""`이면 비운다 |
| contact | 0~100자. `""`이면 비운다 |

응답 `200`: `GET /me`와 같다.

아이디, 이메일, 비밀번호 변경은 MVP 범위 밖이다.

### GET /me/summary

홈 화면의 `neofetch` 줄이다. P1에서 안 읽은 답글 수, 구독 수처럼 무거운 집계가 여기 붙으므로(SH-08) `GET /me`와 나눴다.

```json
{
  "username": "kim",
  "displayName": "김철수",
  "contact": "github.com/kim",
  "joinedAt": "2026-09-30T02:00:00Z",
  "folderCount": 5,
  "postCount": 12,
  "todayPostCount": 1
}
```

`todayPostCount`는 Asia/Seoul 기준 오늘 만든 글 수다. `Shell` 줄은 고정 문구라 프런트가 쓴다.

### GET /users

사람 목록과 사람 찾기.

```
GET /users?q=kim&sort=posts&size=20&cursor=
```

| 파라미터 | 값 |
|---|---|
| q | 선택. 1~20자. 아이디 또는 표시 이름에 부분 일치(대소문자 무시). `find @kim`이면 프런트가 `@`를 떼고 보낸다 |
| sort | `posts`(기본, 공개 글 많은 순) 또는 `joined`(최근 가입순) |

응답 `200`

```json
{
  "items": [
    {
      "username": "kim",
      "displayName": "김철수",
      "bio": "포인터 그림 그리는 사람",
      "postCount": 9,
      "folderCount": 4,
      "joinedAt": "2026-09-30T02:00:00Z"
    }
  ],
  "nextCursor": null
}
```

목록의 `postCount`는 **공개 글(644) 수**다. 보는 사람과 상관없이 같은 값이어야 정렬과 커서가 흔들리지 않는다. 그래서 내 카드에도 비공개 글은 세지 않는다.

### GET /users/{username}

다른 사람 블로그(화면 12)의 프로필 패널. 본인 아이디도 된다.

```json
{
  "username": "kim",
  "displayName": "김철수",
  "bio": "포인터 그림 그리는 사람",
  "joinedAt": "2026-09-30T02:00:00Z",
  "postCount": 9,
  "folderCount": 4,
  "isMe": false
}
```

- `postCount`는 보는 사람이 읽을 수 있는 글 수다. 남이 보면 공개 글 수, 본인이 보면 전체다.
- 이메일과 연락처는 넣지 않는다 (USER-03에 없다).
- `{username}`은 대소문자를 무시한다.

오류: 404.

## 7. folder, fs

### GET /users/{username}/tree

왼쪽 트리. 폴더만 담고 글은 개수로 준다. 전체 트리를 한 번에 준다.

```json
{
  "root": {
    "id": 1, "name": "", "path": "", "displayPath": "~",
    "postCount": 0, "folderCount": 1, "createdAt": "2026-09-30T02:00:00Z",
    "children": [
      {
        "id": 2, "name": "python", "path": "python", "displayPath": "~/python",
        "postCount": 7, "folderCount": 1, "createdAt": "2026-09-30T02:05:00Z",
        "children": [
          {
            "id": 5, "name": "basic", "path": "python/basic", "displayPath": "~/python/basic",
            "postCount": 3, "folderCount": 0, "createdAt": "2026-09-30T02:06:00Z",
            "children": []
          }
        ]
      }
    ]
  }
}
```

- 각 노드는 FolderSummary에 `children`을 더한 것이다. 자식은 이름순이다.
- 남의 트리도 폴더는 모두 보이고, `postCount`만 읽을 수 있는 글로 센다.

### POST /folders

`mkdir <이름>`. 현재 폴더 아래에 만든다.

```json
{ "parentId": 2, "name": "basic" }
```

| 필드 | 규칙 |
|---|---|
| parentId | 필수. 내 폴더. 홈이면 트리의 `root.id` |
| name | 필수. [DB 설계 3절](erd.md#이름-규칙)의 폴더 이름 규칙. 만들면 홈 아래 10단계를 넘지 않아야 한다 |

응답 `201`: FolderSummary.

오류: 400, 403 (남의 폴더), 404 (없는 폴더), 409 `FOLDER_NAME_TAKEN`.

### GET /folders/{id}

폴더 안 목록이다. `GET /fs`가 폴더를 가리킬 때도 같은 본문을 준다.

```
GET /folders/2?sort=name&size=50&cursor=
```

| 파라미터 | 값 |
|---|---|
| sort | `name`(기본, 이름순) 또는 `updated`(최근 수정순) |
| size, cursor | 글 목록에만 적용. `size` 기본 50 |

응답 `200`

```json
{
  "folder": {
    "id": 2, "name": "python", "path": "python", "displayPath": "~/python",
    "postCount": 7, "folderCount": 1, "createdAt": "2026-09-30T02:05:00Z",
    "owner": { "username": "kim", "displayName": "김철수" },
    "editable": true
  },
  "parent": { "id": 1, "displayPath": "~" },
  "folders": [ "FolderSummary..." ],
  "posts": { "items": [ "PostSummary..." ], "nextCursor": null }
}
```

- `folders`는 나눠 주지 않고 모두 준다.
- `sort=updated`일 때 글은 `updatedAt` 순이다. 폴더는 그 안에서 읽을 수 있는 글 중 가장 최근 `updatedAt` 순이고, 글이 없으면 `createdAt`으로 정렬한다. 읽을 수 없는 글은 정렬에도 쓰지 않는다.
- `editable`은 내 폴더인지다. 프런트는 이 값으로 `touch`, `mkdir`, `rmdir` 버튼을 켠다.
- 홈이면 `parent`가 `null`이다.
- 남의 폴더는 읽을 수 있는 글만 `posts`에 넣는다. 비공개 글만 있는 폴더는 빈 폴더와 똑같이 보인다 (DIR-06).
- Tab 자동완성(SH-05)은 이 응답의 `folders[].name`과 `posts.items[].fileName`으로 한다. 별도 API는 없다.

### DELETE /folders/{id}

`rmdir`. 비어 있을 때만 지운다.

응답 `204`.

| 오류 | 언제 |
|---|---|
| 400 `VALIDATION_ERROR` | 홈 폴더 |
| 403, 404 | 1.3절 |
| 409 `FOLDER_NOT_EMPTY` | 하위 폴더, 글, 휴지통 글 중 하나라도 있음. `message`에 남은 것을 적는다 (`"휴지통에 이 폴더의 글 2개가 있습니다. 비우거나 복구한 뒤 지우세요."`) |

### GET /fs

경로를 폴더나 글로 바꿔 준다. `cd`, `ls`, `cat`, `vi`가 모두 이 API로 시작한다.

```
GET /fs?path=~/python
GET /fs?path=~/python/pointer.md
GET /fs?path=/users/kim/spring/security.md
```

경로 문법

| 규칙 | 어기면 |
|---|---|
| `~` 또는 `/users/{username}`으로 시작한다 | 400 `INVALID_PATH` |
| 빈 구간(`//`), `.`, `..`, `.`으로 시작하는 구간이 없다 | 400 `INVALID_PATH` |
| 마지막 구간이 `.md`로 끝나면 글, 아니면 폴더. 끝의 `/`는 무시한다 | |

`~/.trash`는 프런트가 `GET /trash`로, `/users`는 `GET /users`로 부른다. 이 API는 받지 않는다(400).

응답 `200`: 폴더일 때

```json
{ "type": "folder", "folder": { ... }, "parent": { ... }, "folders": [ ... ], "posts": { ... } }
```

`type`을 뺀 나머지는 `GET /folders/{id}`의 기본 정렬 응답과 같다.

응답 `200`: 글일 때

```json
{ "type": "post", "post": { "PostDetail..." } }
```

오류: 404. 없는 사용자, 없는 폴더, 없는 글, 읽을 수 없는 글, 휴지통의 글이 모두 같다.

## 8. post

### 글쓰기 흐름

`touch pointer.md`는 서버를 부르지 않는다. 프런트가 현재 폴더 목록에서 이름 중복만 확인하고 편집기를 연다.

| 편집기 동작 | API |
|---|---|
| 첫 `:w` (Ctrl+S) | `POST /posts` |
| 그 뒤 `:w` | `PATCH /posts/{id}` (`version` 포함) |
| 첫 저장 전 `:q!` | 부르지 않는다. 글이 생기지 않는다 |
| 첫 저장 뒤 `:q!` | 부르지 않는다. 마지막 저장 상태가 남는다 |

열었다가 버린 빈 글이 남지 않게 하려고 이렇게 했다. 유닉스 `touch`와 달리 저장해야 파일이 생긴다는 것을 편집기 상태 바에 적는다.

### POST /posts

```json
{
  "folderId": 2,
  "fileName": "pointer.md",
  "title": "포인터를 그림으로 다시 이해하기",
  "content": "# 포인터\n\n...",
  "tags": ["포인터", "예제"],
  "mode": 644
}
```

| 필드 | 규칙 |
|---|---|
| folderId | 필수. 내 폴더 |
| fileName | 필수. [DB 설계 3절](erd.md#이름-규칙)의 파일 이름 규칙 |
| title | 선택. 200자 이하. 없으면 `""` |
| content | 선택. 마크다운 원문, front matter 없이 본문만. UTF-8 1MB 이하 |
| tags | 선택. 최대 10개, 각각 태그 이름 규칙을 따른다. 앞의 `#`은 떼고 받는다. 대소문자만 다른 것은 하나로 합친다. 순서를 지킨다 |
| mode | 선택. `644`(기본) 또는 `600`. 640은 P1이라 400 |

응답 `201`: PostDetail.

오류: 400, 403 (남의 폴더), 404 (없는 폴더), 409 `POST_NAME_TAKEN`.

### GET /posts/{id}

응답 `200`: PostDetail.

오류: 404 (없음, 읽을 수 없음, 휴지통).

### PATCH /posts/{id}

보낸 필드만 바꾼다. 여러 필드를 한 번에 바꿔도 된다.

| 필드 | 명령어 | 규칙 |
|---|---|---|
| title, content, tags | `:w` | `POST /posts`와 같다. `tags`는 통째로 바꾼다(`[]`면 모두 뗀다) |
| fileName | `mv a.md b.md` | 이름 변경 |
| folderId | `mv a.md python/` | 내 다른 폴더로 이동. `fileName`과 함께 보내면 옮기면서 이름도 바꾼다 |
| mode | `chmod 600 a.md` | 644 또는 600 |
| version | | 선택. 보내면 서버의 `version`과 같을 때만 저장한다 |

- 편집기의 `:w`는 항상 `version`을 보낸다. 다르면 409 `POST_VERSION_CONFLICT`다. 프런트는 "다른 곳에서 고쳐졌습니다"를 보여 주고, 다시 불러올지 덮어쓸지 묻는다. 덮어쓰기는 최신 `version`을 받아 다시 보내는 것이다.
- `mv`, `chmod`는 `version` 없이 보내도 된다. 한 필드만 바꾸므로 본문을 덮어쓸 위험이 없다.
- 실제로 바뀐 값이 있을 때만 `updatedAt`과 `version`이 바뀐다.
- `mv`의 대상이 이름인지 폴더인지는 프런트가 정한다. 대상이 `.md`로 끝나면 이름이다. 아니면 폴더 경로로 보고 `GET /fs`로 폴더 id를 얻는다.

응답 `200`: PostDetail.

오류: 400, 403 (남의 글, 또는 `folderId`가 남의 폴더), 404, 409 `POST_NAME_TAKEN`, 409 `POST_VERSION_CONFLICT`.

### DELETE /posts/{id}

`rm`. 휴지통으로 보낸다.

- 파일 이름을 입력하는 확인 창(POST-08)은 프런트가 띄운다. 서버는 확인 값을 받지 않는다.
- 지운 직후 `u`는 `POST /trash/{id}/restore`다. 프런트는 지운 글의 id를 기억해 둔다.

응답 `204`.

오류: 403, 404.

### GET /trash

내 휴지통. 최근에 지운 것부터.

```json
{
  "items": [
    {
      "id": 42,
      "fileName": "pointer.md",
      "title": "포인터를 그림으로 다시 이해하기",
      "originalDisplayPath": "~/python/pointer.md",
      "deletedAt": "2026-10-01T03:20:00Z",
      "purgeAt": "2026-10-31T03:20:00Z"
    }
  ],
  "nextCursor": null
}
```

`purgeAt`은 `deletedAt + 30일`이다. 실제로는 그 뒤 첫 예약 작업(매일 04:00)에서 지워진다.

### POST /trash/{id}/restore

휴지통의 글을 원래 폴더로 되돌린다. 본문은 없어도 된다. 원래 자리에 같은 이름의 글이 생겨 있으면 이름을 바꿔 복구한다.

```json
{ "fileName": "pointer-2.md" }
```

응답 `200`: PostDetail.

오류: 400, 404 (없음, 내 휴지통에 없음), 409 `POST_NAME_TAKEN`.

### DELETE /trash/{id}

휴지통에서 완전히 지운다. 되돌릴 수 없다. 살아 있는 글은 이 API로 지울 수 없고, 먼저 `rm`으로 휴지통에 보내야 한다.

응답 `204`.

오류: 404 (없음, 내 휴지통에 없음).

### GET /users/{username}/posts

그 사람의 글 전체를 폴더와 상관없이 최근 수정순으로 준다. 화면 12의 가운데 목록이다.

```
GET /users/kim/posts?size=20&cursor=
```

응답 `200`: `{ "items": [PostSummary...], "nextCursor": ... }`. 읽을 수 있는 글만 나온다.

오류: 404 (없는 사용자).

## 9. search

### GET /search/content

`grep <단어>`. 본문에서 대소문자를 무시한 부분 일치를 찾아 일치한 줄을 준다.

```
GET /search/content?q=포인터&scope=all&size=20&cursor=
```

| 파라미터 | 값 |
|---|---|
| q | 필수. 2~100자. 정규식이 아닌 글자 그대로다. 앞뒤 공백은 자른다 |
| scope | `all`(기본, 읽을 수 있는 모든 글) 또는 `mine` |

응답 `200`

```json
{
  "fileCount": 12,
  "items": [
    {
      "post": { "PostSummary..." },
      "matchCount": 3,
      "lines": [
        { "lineNo": 12, "text": "본문에서 포인터를 처음 만난 자리입니다.", "ranges": [[5, 8]] },
        { "lineNo": 28, "text": "그림으로 포인터를 다시 그려봤습니다.", "ranges": [[5, 8]] }
      ]
    }
  ],
  "nextCursor": null
}
```

- **정렬은 최근 수정순이다.** 일치 많은 순 정렬은 P1(SRCH-05)이다. DB가 정렬하고 커서로 나누므로, 페이지마다 그 페이지 글의 본문만 읽는다.
- `fileCount`는 일치한 글 수이고 첫 페이지에만 있다. 다음 페이지부터는 `null`이다.
- `matchCount`는 그 글 안의 일치 횟수다. `lines`는 일치한 줄 중 앞에서부터 최대 3줄이다. `lineNo`는 1부터 센다.
- 줄이 200자보다 길면 첫 일치 주변만 잘라 `text`에 넣고 잘린 쪽에 `…`를 붙인다.
- `ranges`는 `text` 안에서 일치한 구간 `[시작, 끝)`이다. 위치는 UTF-16 코드 단위로 센다. Java `String`과 JavaScript 문자열의 인덱스가 둘 다 이 단위라 프런트가 그대로 강조에 쓸 수 있다.
- 구현과 성능 한계는 [DB 설계 5.3절](erd.md#53-검색-pg_trgm)에 있다.

### GET /search/names

`find <이름>`. 파일 이름, 폴더 이름, 경로에서 대소문자를 무시한 부분 일치를 찾는다.

```
GET /search/names?q=pointer&scope=all
```

| 파라미터 | 값 |
|---|---|
| q | 필수. 1~100자 |
| scope | `all`(기본) 또는 `mine` |

응답 `200`

```json
{
  "folders": [
    { "id": 9, "name": "pointer", "displayPath": "/users/lee/pointer", "owner": { "username": "lee", "displayName": "이영희" } }
  ],
  "posts": [ "PostSummary..." ]
}
```

- 폴더는 `path`, 글은 `file_name` 또는 폴더의 `path`에서 찾는다.
- 이름이 검색어로 시작하는 것을 먼저 놓고, 그다음 최근 순이다(글은 `updatedAt`, 폴더는 `createdAt`).
- 폴더와 글 각각 최대 50개다. 커서 없이 한 번에 준다.
- 읽을 수 없는 글은 나오지 않는다. 폴더는 모두 나온다.

`find @kim`은 이 API가 아니라 `GET /users?q=kim`이다. 프런트가 `@`로 구분한다.

### GET /tags

편집기의 태그 입력 자동완성.

```
GET /tags?q=포인&size=10
```

| 파라미터 | 값 |
|---|---|
| q | 필수. 1~30자. 앞의 `#`은 뗀다. 이름의 앞부분 일치, 대소문자 무시 |
| size | 기본 10, 최대 20 |

응답 `200`: `{ "items": [ { "name": "포인터", "postCount": 12 } ] }`

`postCount`는 보는 사람이 읽을 수 있는 글 수다. 0인 태그는 빼고, 글 많은 순으로 준다. 커서는 없다.

### GET /tags/{name}/posts

태그를 눌렀을 때. `{name}`은 `#` 없이 URL 인코딩해 보내고, 대소문자를 무시한다.

```
GET /tags/%ED%8F%AC%EC%9D%B8%ED%84%B0/posts?scope=all&size=20&cursor=
```

| 파라미터 | 값 |
|---|---|
| scope | `all`(기본) 또는 `mine` |

응답 `200`

```json
{ "tag": { "name": "포인터" }, "items": [ "PostSummary..." ], "nextCursor": null }
```

최근 수정순이다. 태그가 없거나 읽을 수 있는 글이 없으면 빈 목록이다. 404를 주지 않는다.

## 10. 명령어와 API

명령어 해석은 프런트가 한다 (MVP 범위 5절 "전제").

| 명령어 | API |
|---|---|
| `pwd` | 없음. 프런트 상태 |
| `ls`, `ls -a` | 현재 폴더의 `GET /folders/{id}` (이미 받았으면 다시 부르지 않아도 된다) |
| `cd <경로>`, `cd ..`, `cd ~` | 절대 경로로 바꾼 뒤 `GET /fs?path=` |
| `cat <파일>` | `GET /fs?path=` → `type: post` |
| `vi <파일>` | `GET /fs?path=`로 받아 편집기를 연다. `:w`마다 `PATCH /posts/{id}` |
| `mkdir <이름>` | `POST /folders` |
| `touch <이름>.md` | 편집기를 연다. 첫 `:w`에 `POST /posts` |
| `mv <원본> <대상>` | `PATCH /posts/{id}` (`fileName`, `folderId`) |
| `chmod <권한> <파일>` | `PATCH /posts/{id}` (`mode`) |
| `rmdir <폴더>` | `DELETE /folders/{id}` |
| `rm <파일>` | 확인 창 → `DELETE /posts/{id}`. `u` → `POST /trash/{id}/restore` |
| `cd ~/.trash` | `GET /trash` |
| `~/.trash`에서 `rm -f <파일>` | `DELETE /trash/{id}`. 휴지통 밖에서는 "휴지통에서만 쓸 수 있습니다" 오류를 프런트가 낸다 |
| `find <이름>` | `GET /search/names` |
| `find @<아이디>` | `GET /users?q=` |
| `grep <단어>` | `GET /search/content` |
| `ls /users` | `GET /users` |
| `cd /users/<아이디>` | `GET /users/{username}`, `GET /users/{username}/posts`, `GET /users/{username}/tree` |
| `whoami` | `GET /me` |
| `config` | `GET /me` → `PUT /me/profile` |
| `login` | `POST /auth/login` |
| `logout` | `POST /auth/logout` |
| `/help` | 없음. 정적 화면 |
| 홈 (`neofetch`) | `GET /me/summary` |
| 왼쪽 트리 | `GET /users/{내 아이디}/tree`. 폴더를 만들거나 지우거나 글을 만들거나 옮기거나 지운 뒤 다시 받는다 |
| 태그 클릭 | `GET /tags/{name}/posts` |

## 11. 완료 기준 시나리오와 API

[MVP 범위 4절](mvp-scope.md#4-완료-기준)의 시나리오가 거치는 API다.

| # | 시나리오 | API |
|---|---|---|
| 1 | 게스트 접근 제어 | `POST /auth/refresh` → 401. 프런트가 로그인 화면으로 보낸다 |
| 2 | 가입 → 로그인 → 홈 | `POST /auth/signup` → `POST /auth/login` → `GET /me/summary`, `GET /users/{me}/tree` |
| 3 | mkdir → cd → touch → 저장 | `POST /folders` → `GET /fs` → `POST /posts` |
| 4 | cat | `GET /fs?path=~/python/pointer.md` |
| 5 | vi, mv | `PATCH /posts/{id}` (`version` 포함) → `PATCH /posts/{id}` (`fileName`) |
| 6 | chmod 600, 남이 볼 때 | `PATCH /posts/{id}` (`mode: 600`). 다른 회원의 `GET /fs`는 폴더면 빈 목록, 글이면 404. `GET /search/*` 결과와 개수에 나오지 않는다 |
| 7 | 다른 회원의 글 읽기 | `GET /users` → `GET /users/kim`, `/posts`, `/tree` → `GET /fs`. `PATCH`, `DELETE`는 403 |
| 8 | grep, find | `GET /search/content`, `GET /search/names` |
| 9 | rm, u, 휴지통 복구 | `DELETE /posts/{id}` → `POST /trash/{id}/restore` → `DELETE /posts/{id}` → `GET /trash` → `POST /trash/{id}/restore` |
| 10 | rmdir 거절 | `DELETE /folders/{id}` → 409 `FOLDER_NOT_EMPTY` |
| 11 | 오타 | `GET /fs?path=~/python/pointr.md` → 404 |
| 12 | logout 후 | `POST /auth/logout` → `POST /auth/refresh` 401 → 로그인 화면 |

구현 순서는 MVP 범위 5절의 "작업 순서"를 따른다. 1단계(auth, profile 일부)가 끝나면 시나리오 1, 2, 12가 통과한다.

## 12. P1 이후 API

지금 설계에 자리가 있는지만 확인했다. 세부는 그때 정한다.

| 기능 | API 방향 |
|---|---|
| 답글 (REPLY-01~03) | `POST /posts/{id}/replies`, `GET /posts/{id}/replies`. 답글도 글이라 PostDetail에 `replyTo`가 붙는다 |
| 구독·피드 (USER-04~06) | `PUT`/`DELETE /users/{username}/follow`, `GET /feed`, `GET /users?filter=following` |
| 알림 (NOTI-01~04) | `GET /notifications`, `POST /notifications/read-all`, `PUT /me/notification-settings` |
| 위키 링크·백링크 (LINK-01~02) | PostDetail에 `links`, `backlinks`. 삭제 영향 안내(POST-15)는 `GET /posts/{id}/delete-impact` |
| 링크 공개 640 (POST-13) | `POST /posts/{id}/share-link`, `GET /fs?path=&token=` |
| 복사 (POST-14) | `POST /posts/{id}/copy` |
| 북마크 (ME-04) | `PUT`/`DELETE /me/bookmarks/{kind}/{id}`, `GET /me/bookmarks` |
| 쓴 날·통계 (ME-03, 05) | `GET /me/activity`, `GET /me/stats` |
| 저장될 모습 (POST-12) | `GET /posts/{id}`에 `Accept: text/markdown`이면 front matter를 붙인 원문 |
| 검색 추리기 (SRCH-04~06) | `GET /search/content`에 `sort=matches`, `scope=following\|folder`, `owner=`, `tag=` |
| 이름 비슷한 글 제안 (ERR-02) | `GET /search/suggest?path=` |
| 비밀번호 찾기, 소셜 로그인 (AUTH-07~08) | `POST /auth/password-reset`, `GET /oauth2/authorization/{provider}` |

## 13. 설계 결정

| # | 항목 | 결정 | 이유 |
|---|---|---|---|
| 1 | 경로 조회 | `GET /fs` 하나로 폴더와 글을 모두 푼다 | `cd`, `ls`, `cat`이 같은 경로 문법을 쓴다. 프런트가 경로의 종류를 미리 알 필요가 없다 |
| 2 | 글 만들기 시점 | `touch`가 아니라 첫 `:w` | 열었다 버린 빈 글이 남지 않는다 |
| 3 | 글 수정 | `PATCH /posts/{id}` 하나 + `version` | 필드별 엔드포인트보다 단순하다. 두 탭의 동시 저장을 잡는다 |
| 4 | 휴지통 | 휴지통의 글은 `/trash/{id}`로만 다룬다. `/posts/{id}`는 살아 있는 글만 | 상태별로 자원이 나뉘어 "휴지통에 있음" 같은 상태 오류 코드가 필요 없다 |
| 5 | 완전 삭제 | 휴지통에서만. 살아 있는 글을 바로 지울 수 없다 | 기능 명세서 POST-09가 휴지통의 `rm -f`다. 실수로 되돌릴 수 없게 지우는 경로를 줄인다 |
| 6 | 권한 오류 | 읽을 수 없으면 404, 읽을 수 있지만 남의 것이면 403. 이 순서로 검사 | 존재 비노출. 403은 이미 보이는 대상에만 나온다 |
| 7 | 개수 | 읽을 수 있는 글만. 사람 목록은 공개 글 수 | 존재 비노출. 사람 목록은 보는 사람과 상관없는 값이어야 정렬이 흔들리지 않는다 |
| 8 | 목록 | 커서 페이지 | "더 불러오기"와 맞는다 |
| 9 | 내용 검색 정렬 | 최근 수정순 | DB가 정렬해야 커서가 성립한다. 일치 수 정렬은 P1 |
| 10 | 리프레시 | 회전 + 30초 유예 + 재사용 시 가족 폐기 | 여러 탭이 동시에 갱신해도 로그아웃되지 않는다 |
| 11 | 로그아웃 | 액세스 토큰 없이 쿠키로 | 액세스 토큰이 만료된 뒤에도 로그아웃할 수 있다 |
| 12 | 비밀번호 상한 | UTF-8 72바이트 | BCrypt 한계. 글자 수로 재면 한글 비밀번호가 500이 된다 |
| 13 | 프로필 수정 | `PUT /me/profile`로 세 필드를 통째로 | "안 보냄"과 "비움"을 구분할 필요가 없다 |
| 14 | 연락처·이메일 | 본인에게만 | USER-03 프로필에 없다 |
| 15 | `displayPath` | 서버가 보는 사람 기준으로 만든다 | 프런트가 소유자를 비교해 조립하지 않는다 |
| 16 | 가입 뒤 자동 로그인 | 하지 않는다 | 시나리오 2가 가입 → 로그인이다. 가입 응답에 토큰과 쿠키를 섞지 않는다 |
| 17 | 패키지 | 도메인(user, auth, folder, tag, post) + 조회(fs, search, profile) | 여러 도메인을 모으는 응답을 조회 패키지가 맡아 도메인 사이 순환이 없다 |

### 처음 초안(2026-09-30)과 달라진 점

| 초안 | 이 문서 |
|---|---|
| `PATCH /me` | `PUT /me/profile` |
| `GET /users/{username}`에 프로필 | 같음. 그 사람의 글 목록 `GET /users/{username}/posts`를 더했다 |
| `DELETE /posts/{id}?permanent=true` | `DELETE /trash/{id}` |
| `POST /posts/{id}/restore` | `POST /trash/{id}/restore` |
| — | `POST /auth/refresh`, `GET /auth/availability`, `GET /folders/{id}`, `GET /tags` 추가 |

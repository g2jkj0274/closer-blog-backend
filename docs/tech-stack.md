# c1oser.dev 기술 스택 결정

- 상태: 백엔드 적용 (2026-10-07). 처음 제안은 2026-09-30이다. 5절의 네 가지는 아직 확인이 필요하다.
- 상태 칸: **기존**은 저장소에 원래 있던 것, **적용**은 구현에 쓰고 있는 것, **확정**은 정했지만 아직 만들지 않은 것, **제안**과 **확인 필요**는 아직 정하지 않은 것이다.
- 함께 읽을 문서: [기능 명세서](functional-spec.md), [MVP 범위](mvp-scope.md), [DB 설계](erd.md), [API 명세서](api-spec.md)
- 범위: MVP(P0)를 만드는 데 필요한 스택. P1 이후에 필요한 것은 4절에 따로 적었다.

## 1. 선정 기준

1. **이미 정한 것을 따른다.** Spring Boot 4.1.1, Java 21, Gradle은 저장소에 있고, PostgreSQL, 토큰 인증, 분리된 SPA는 [MVP 범위 6절](mvp-scope.md#6-결정-사항)에서 확정했다.
2. **Spring Boot가 버전을 관리하는 것을 먼저 쓴다.** 버전을 직접 맞출 의존성을 줄인다.
3. **서비스가 동작하는 데 필요한 구성 요소를 늘리지 않는다.** MVP는 애플리케이션 하나와 DB 하나로 돌아간다. 검색 엔진, 캐시 서버, 메시지 큐는 두지 않는다. 모니터링과 부하 테스트 도구는 서비스 바깥에 따로 두며, 꺼져 있어도 서비스는 동작한다.

## 2. 결정 요약

### 백엔드 (이 저장소)

| 영역 | 결정 | 상태 |
|---|---|---|
| 언어·런타임 | Java 21 | 기존 |
| 프레임워크 | Spring Boot 4.1.1, Spring MVC | 기존 |
| 빌드 | Gradle 9.7.1 (Wrapper, Groovy DSL) | 기존 |
| 데이터베이스 | PostgreSQL 18 | 적용 |
| 데이터 접근 | Spring Data JPA (Hibernate 7.4) | 적용 |
| 스키마 관리 | Flyway | 적용 |
| 인증 | Spring Security 7.1. JWT 액세스 토큰 + DB에 저장하는 리프레시 토큰 | 적용 |
| 비밀번호 | BCrypt | 적용 |
| 입력 검증 | Bean Validation | 적용 |
| 검색 | PostgreSQL `pg_trgm` 인덱스 | 적용 |
| 예약 작업 | Spring `@Scheduled` | 적용 |
| API 문서 | springdoc-openapi 3.1 (Swagger UI) | 적용 |
| 테스트 | JUnit 6, MockMvc, Testcontainers 2.0, ArchUnit 1.5 | 적용 |
| 반복 코드 생성 | Lombok. 허용한 어노테이션만 쓴다 (3.12절) | 적용 |
| 상태 확인·메트릭 | Spring Boot Actuator + Micrometer Prometheus 레지스트리 | 적용 |

### 프런트엔드 (별도 저장소)

| 영역 | 결정 | 상태 |
|---|---|---|
| 언어 | TypeScript | 제안 |
| 프레임워크 | React + Vite (SPA) | 확인 필요 |
| 라우팅 | React Router | 제안 |
| 서버 상태 | TanStack Query | 제안 |
| 스타일 | Tailwind CSS. 시안의 색과 글꼴을 테마 토큰으로 옮긴다 | 제안 |
| 마크다운 렌더 | react-markdown + remark-gfm + rehype-sanitize | 제안 |
| 코드 강조 | Shiki | 제안 |
| 편집기 | CodeMirror 6 (마크다운 모드) | 제안 |
| 명령어 해석 | 직접 구현 | 제안 |
| 테스트 | Vitest, Playwright | 제안 |
| 패키지 관리 | npm | 제안 |

### 개발 환경·배포

| 영역 | 결정 | 상태 |
|---|---|---|
| 로컬 DB | Docker Compose. Spring Boot의 Docker Compose 지원으로 앱 실행 시 함께 뜬다 | 적용 |
| 설정 | `application.yaml`(로컬은 기본 프로필) + `prod` 프로필. 운영 비밀 값은 환경 변수로 받고 없으면 앱이 뜨지 않는다. `local` 프로필 파일은 두지 않았다 | 적용 |
| CI | GitHub Actions. `main` 푸시와 PR마다 빌드·테스트, Docker 이미지를 띄워 health 확인 | 적용 |
| 모니터링 | Prometheus(수집) + Grafana(대시보드). 로컬용 `compose.monitoring.yaml` | 적용 |
| 부하 테스트 | k6 | 확정 (아직 만들지 않음) |
| 백엔드 배포 | Docker 이미지 (`Dockerfile`). 레지스트리에 올리는 것은 호스팅이 정해진 뒤에 한다 | 적용 |
| 호스팅 | 정하지 않음 | 확인 필요 |
| 도메인 구성 | 프런트 `c1oser.dev`, API `api.c1oser.dev` | 확인 필요 |

## 3. 결정별 이유

### 3.1 데이터 접근: Spring Data JPA

글, 폴더, 태그, 사용자를 만들고 고치는 일이 대부분이라 JPA의 기본 기능으로 충분하다. JPA로 쓰기 불편한 두 가지는 네이티브 쿼리로 쓴다.

- 내용·이름 검색: 3.4절의 `pg_trgm` 조회.
- 여러 테이블을 묶는 집계: 사람 목록의 공개 글 수 정렬처럼 사용자, 폴더, 글을 함께 세는 조회.

폴더 트리는 네이티브 쿼리가 필요 없다. 폴더마다 홈 기준 경로(`path`)를 저장하고, 트리는 그 사람의 폴더를 모두 읽어 애플리케이션에서 조립한다. 한 사람의 폴더는 수십 개 수준이라 재귀 CTE를 쓰지 않는다 ([DB 설계 4.3절](erd.md#43-folders-folder)).

엔티티 매핑에서 두 가지를 지킨다. 본문(`TEXT`)에 `@Lob`을 붙이지 않는다. PostgreSQL에서 `@Lob String`은 large object로 매핑된다. 그리고 컬럼 타입을 `INTEGER`, `VARCHAR`, `TEXT`로 맞춰 `ddl-auto=validate`에서 타입 불일치가 나지 않게 한다 ([DB 설계 3절](erd.md#3-공통-규칙)).

| 버린 대안 | 이유 |
|---|---|
| MyBatis | 단순 CRUD까지 SQL을 직접 써야 한다 |
| jOOQ | 코드 생성 단계가 빌드에 추가된다. MVP 규모에서는 이득이 작다 |
| Spring Data JDBC | 글과 태그의 다대다 관계를 직접 다뤄야 한다 |

### 3.2 스키마 관리: Flyway

스키마 변경을 SQL 파일로 남긴다. 여러 PC에서 작업하므로 DB 구조를 코드와 함께 맞추는 수단이 필요하다. `pg_trgm` 확장 설치와 인덱스 생성도 마이그레이션에 넣는다. Hibernate의 `ddl-auto`는 `validate`로만 쓴다.

Liquibase는 XML·YAML 형식이 필요 없어서 고르지 않았다.

### 3.3 인증: JWT 액세스 토큰 + 리프레시 토큰

| 토큰 | 형태 | 보관 | 수명 |
|---|---|---|---|
| 액세스 | JWT (HS256 서명) | 프런트의 메모리 | 짧게 (15분) |
| 리프레시 | 임의 문자열. 서버는 해시만 DB에 저장 | `HttpOnly`, `Secure`, `SameSite=Lax` 쿠키 | "로그인 유지"를 켜면 30일, 끄면 브라우저를 닫을 때까지(DB에는 24시간) |

- 리프레시 토큰은 쓸 때마다 새로 발급한다(회전). 로그인 한 번에서 나온 토큰들을 한 가족으로 묶는다.
  - 이미 회전된 토큰이 30초 안에 다시 오면 다른 탭이 동시에 갱신한 것으로 보고 받아 준다. 탭 여러 개를 연 채 앱을 다시 열어도 로그아웃되지 않는다.
  - 30초가 지나 다시 오면 탈취로 보고 그 가족을 모두 폐기한다.
  - 로그아웃하면 가족을 지운다. 로그아웃은 쿠키만으로 처리해 액세스 토큰이 만료된 뒤에도 된다.
  - 자세한 규칙은 [DB 설계 4.2절](erd.md#42-refresh_tokens-auth)과 [API 명세서 5절](api-spec.md#5-auth)에 있다.
- 액세스 토큰을 `localStorage`에 두지 않는다. 마크다운을 렌더하는 서비스라 스크립트 주입에 대비한다.
- JWT 발급과 검증은 Spring Security에 포함된 Nimbus(`spring-boot-starter-security-oauth2-resource-server`)를 쓴다. 별도 JWT 라이브러리를 넣지 않는다.
- 로그인 시도 제한은 계정별 실패 횟수를 `users` 테이블에 기록해 처리한다. 5번 틀리면 15분 잠근다.
- 비밀번호는 Spring Security 기본인 BCrypt로 해시한다. Argon2는 추가 라이브러리(Bouncy Castle)가 필요해서 고르지 않았다. BCrypt는 72바이트까지만 쓰므로 비밀번호 상한을 글자 수가 아니라 UTF-8 72바이트로 검사한다.

수명 값(15분, 30일)은 시작값이다.

| 버린 대안 | 이유 |
|---|---|
| 서버 세션 + 쿠키 | 구현은 가장 단순하다. 다만 MVP 범위에서 토큰 방식으로 확정했고, 프런트와 API의 도메인이 나뉜다 |
| jjwt 라이브러리 | Spring Security에 같은 기능이 있다 |
| 리프레시 토큰도 JWT | 서버에서 무효로 만들 수 없다 |

### 3.4 검색: PostgreSQL `pg_trgm`

`grep`은 본문에서, `find`는 파일 이름과 경로에서 부분 일치를 찾는다. `pg_trgm`의 GIN 인덱스는 `ILIKE '%포인터%'` 같은 부분 일치를 인덱스로 처리하고 한글에도 동작한다. 일치한 줄과 줄 번호는 애플리케이션에서 본문을 줄 단위로 나눠 뽑는다. 결과는 DB가 최근 수정순으로 정렬해 페이지로 나누므로, 애플리케이션은 한 페이지에 든 글의 본문만 읽는다.

한계가 두 가지 있다. 자세한 내용은 [DB 설계 5.3절](erd.md#53-검색-pg_trgm)에 있다.

- 검색어가 3글자 이상이어야 인덱스를 쓴다. 2글자 검색어는 순차 조회가 된다. 한국어는 2글자 단어가 많아 허용하되 3.11절의 부하 테스트로 확인한다.
- DB의 로케일이 `C`면 한글이 trigram에서 빠질 수 있다. DB는 UTF-8 로케일로 만든다.

| 버린 대안 | 이유 |
|---|---|
| PostgreSQL 전문 검색 (`tsvector`) | 기본 설정에 한국어 형태소 분석이 없다. `grep`의 "글자 그대로 찾기"와도 맞지 않는다 |
| Elasticsearch, OpenSearch | 운영할 서버가 하나 더 생긴다. DB와 동기화도 필요하다 |

글이 많아져 검색이 느려지면 그때 검색 엔진을 검토한다.

### 3.5 마크다운: 서버는 원문만 저장한다

- 서버는 제목, 태그, 본문(마크다운 원문)을 컬럼으로 나눠 저장한다. front matter는 내보내기와 "저장될 모습"에서 조립한다.
- 렌더는 프런트가 한다. `rehype-sanitize`로 스크립트를 제거한다.
- 글자 수와 읽는 시간은 서버가 원문에서 계산한다.

### 3.6 예약 작업: `@Scheduled`

휴지통의 30일 자동 비우기와 만료된 리프레시 토큰 정리, 하루 한 번씩 두 가지뿐이라 Spring의 기본 스케줄러로 충분하다 ([DB 설계 7절](erd.md#7-예약-작업)). 서버를 한 대로 운영한다는 전제다. 여러 대가 되면 중복 실행을 막는 장치(ShedLock 등)를 추가한다.

### 3.7 API 문서: springdoc-openapi

프런트가 별도 저장소라 API 명세를 공유할 수단이 필요하다. 컨트롤러에서 OpenAPI 문서와 Swagger UI를 만든다. 3.1.1은 Spring Boot 4.1.0을 기준으로 빌드돼 있다. 운영 프로필에서는 끈다.

### 3.8 테스트: JUnit, MockMvc, Testcontainers

- 단위 테스트: JUnit 6 (Spring Boot 관리 버전).
- API 테스트: MockMvc로 요청과 권한을 검증한다.
- DB 테스트: Testcontainers로 실제 PostgreSQL을 띄운다. `pg_trgm`, 부분 고유 인덱스, 복합 외래 키, 정규식 `CHECK` 제약은 H2에서 똑같이 동작하지 않으므로 H2를 쓰지 않는다. 한글 검색어가 trigram 인덱스를 쓰는지도 이 테스트에서 `EXPLAIN`으로 확인한다 (`SearchTest`).
- 구조 테스트: ArchUnit으로 [API 명세서 2절](api-spec.md#2-패키지-구조)의 패키지 의존 방향과 계층 규칙을 고정한다 (`ArchitectureTest`).
- 흐름 테스트: [MVP 범위 4절](mvp-scope.md#4-완료-기준)의 완료 기준 시나리오를 API로 처음부터 끝까지 잇는다 (`MvpScenarioTest`).

Testcontainers는 Docker가 필요하다. CI(GitHub Actions의 Ubuntu 러너)에는 Docker가 들어 있다.

### 3.9 프런트엔드: React + Vite SPA

- 글을 읽으려면 로그인이 필요하므로 검색 엔진에 노출할 페이지가 랜딩과 `/help`뿐이다. 서버 렌더링이 주는 이점이 작아 SPA로 충분하다.
- 화면 전체가 명령 입력, 자동완성, 단축키로 움직이는 클라이언트 상태 중심의 앱이다.
- 빌드 결과가 정적 파일이라 배포가 단순하다.

| 영역 | 고른 이유 |
|---|---|
| TanStack Query | 폴더 목록, 글, 검색 결과 같은 서버 데이터를 캐시하고 다시 불러온다 |
| Tailwind CSS | 시안의 색 팔레트와 글꼴 2가지(JetBrains Mono, IBM Plex Sans KR)를 토큰으로 두고 화면마다 재사용한다 |
| CodeMirror 6 | MVP 편집기는 마크다운 원문 편집이다. 줄 번호, 단축키(`Ctrl+S`), 마크다운 강조를 제공한다 |
| 명령어 해석 직접 구현 | P0 명령어가 17줄이고 문법이 "명령어 + 인자 + 옵션"뿐이다. 파이프와 리다이렉트는 P2다 |
| Playwright | [MVP 범위 4절](mvp-scope.md#4-완료-기준)의 시나리오 12개를 자동으로 검증한다 |

| 버린 대안 | 이유 |
|---|---|
| Next.js | 서버 렌더링이 필요한 페이지가 거의 없다. 프런트용 서버를 하나 더 운영하게 된다 |
| Vue, Svelte | 기술적으로는 무방하다. 마크다운·편집기 라이브러리 선택지는 React 쪽이 가장 넓다 |
| 서버 템플릿 (Thymeleaf) | 명령 입력과 자동완성 같은 상호작용을 만들기 어렵다 |

P1의 블록 편집기에 쓸 라이브러리는 그때 정한다.

### 3.10 개발 환경·배포

- 로컬 DB는 Docker Compose로 띄운다. `spring-boot-docker-compose`가 앱을 실행할 때 `compose.yaml`의 PostgreSQL을 함께 띄우고 접속 정보를 넣어 준다. PC마다 DB를 따로 설치하지 않는다.
- 백엔드는 Docker 이미지로 배포한다. 어디에 올리든 같은 이미지를 쓴다.
- `.dev` 도메인은 브라우저가 HTTPS만 허용한다. 프런트와 API를 같은 사이트(`c1oser.dev`와 `api.c1oser.dev`)에 두면 리프레시 쿠키를 `SameSite=Lax`로 쓸 수 있다. API에는 프런트 출처만 허용하는 CORS 설정을 둔다.

### 3.11 모니터링과 부하 테스트: Prometheus, Grafana, k6

세 도구는 한 묶음으로 쓴다. k6가 부하를 걸고, Prometheus가 그동안의 서버 지표를 모으고, Grafana가 둘을 같은 시간축에 보여 준다.

| 도구 | 역할 | 연결 |
|---|---|---|
| Micrometer | 애플리케이션 지표를 만든다 | Actuator의 `/actuator/prometheus`로 노출한다 |
| Prometheus | 지표를 주기적으로 긁어 저장한다 | 앱의 `/actuator/prometheus`를 수집한다 |
| Grafana | 대시보드로 보여 준다 | Prometheus를 데이터 소스로 쓴다 |
| k6 | 시나리오대로 부하를 건다 | 결과를 Prometheus에 remote write로 보낸다 |

- **보는 지표**: 요청 수, 응답 시간(p95), 오류율, JVM 메모리와 GC, DB 커넥션 풀. Actuator가 기본으로 내는 지표라 코드를 추가하지 않는다.
- **부하 시나리오**: [API 명세서](api-spec.md)의 API 중 자주 불릴 것부터 만든다. 로그인, 폴더 목록, 글 읽기, 내용 검색, 글 저장 순이다. 내용 검색은 "검색 엔진 없이 `pg_trgm`으로 충분한가"(3.4절)를 확인하는 수단이기도 하다.
- **합격 기준**: k6의 threshold로 적어 두면 기준을 넘을 때 실행이 실패한다. 목표 수치는 아직 없다 (5절 4번).
- **위치**: k6 스크립트는 이 저장소의 `load-test/`에 JavaScript로 둔다. Prometheus와 Grafana는 `compose.monitoring.yaml`에 따로 정의해 필요할 때만 띄운다. 평소 개발에서 앱과 함께 뜨는 것은 PostgreSQL뿐이다.
- **실행 시점**: 부하 테스트는 푸시마다 돌리지 않는다. 검색처럼 성능이 걸린 기능을 넣은 뒤와 배포 전에 직접 돌린다.
- **보안**: `/actuator/prometheus`는 밖에서 열리지 않게 한다. 운영(`prod` 프로필)에서는 Actuator를 8081 포트로 분리했다(`application-prod.yaml`). 8081은 외부에 노출하지 않고, 공개하는 8080에는 Actuator가 없다.

k6, Prometheus, Grafana는 모두 공식 Docker 이미지(`grafana/k6`, `prom/prometheus`, `grafana/grafana`)로 실행한다. PC에 따로 설치하지 않는다.

| 버린 대안 | 이유 |
|---|---|
| JMeter | 시나리오가 XML이라 코드 리뷰와 버전 관리가 불편하다 |
| Gatling | 시나리오를 Java·Scala로 쓰고 별도 빌드가 필요하다. Grafana와 연결하려면 설정이 더 든다 |
| 상용 모니터링 서비스 | 비용이 든다. 호스팅이 정해지지 않았다 |

로그 수집(Loki 등), 분산 추적, PostgreSQL 자체 지표(postgres_exporter)는 넣지 않았다. 서버가 한 대인 MVP에서는 애플리케이션 지표와 로그 파일로 충분하다.

운영 환경에서 Prometheus와 Grafana를 어디에 둘지는 호스팅(5절 2번)이 정해져야 정할 수 있다.

### 3.12 반복 코드: Lombok (허용한 것만)

엔티티의 getter, JPA용 기본 생성자, 생성자 주입은 Lombok으로 만든다. 요청·응답 객체는 Java `record`로 만들므로 Lombok을 쓰지 않는다. 버전은 Spring Boot가 관리한다.

| 허용 | 쓰는 곳 |
|---|---|
| `@Getter` | 엔티티 |
| `@NoArgsConstructor(access = AccessLevel.PROTECTED)` | 엔티티. JPA만 쓰는 생성자 |
| `@RequiredArgsConstructor` | 서비스, 컨트롤러, 설정 등 생성자 주입을 받는 빈 |

| 금지 | 이유 |
|---|---|
| `@Data`, `@Setter` | 아무 곳에서나 값을 바꿀 수 있게 된다. 값은 의미 있는 메서드(`User.register`, `post.rename` 등)로만 바꾼다 |
| 엔티티의 `@ToString`, `@EqualsAndHashCode` | 지연 로딩 연관을 건드려 쿼리가 더 나가거나, 서로 참조하는 엔티티 사이에서 무한 반복이 생긴다 |
| `@Builder` | 정적 팩토리 메서드가 지키는 생성 규칙을 건너뛰게 된다 |
| `@AllArgsConstructor` | 필드 순서가 바뀌면 같은 타입 인자가 소리 없이 뒤바뀐다 |

생성자 매개변수에 `@Qualifier` 같은 어노테이션이 필요한 빈은 `@RequiredArgsConstructor`가 그 어노테이션을 옮기지 않으므로 생성자를 직접 쓴다 (`SecurityErrorHandler`).

## 4. MVP 뒤에 추가될 것

| 기능 | 필요한 것 |
|---|---|
| 소셜 로그인 (AUTH-08) | Spring Security OAuth2 Client. Google, GitHub, Kakao, Naver에 앱 등록 |
| 비밀번호 찾기, 메일 알림 (AUTH-07, NOTI-06) | 메일 발송 서비스 |
| 블록 편집기 (POST-10) | 블록 편집기 라이브러리 |
| 그래프 (LINK-03) | 그래프 시각화 라이브러리 |
| 가져오기·내보내기 (ME-07, 08) | zip 처리, GitHub API |
| 이미지 첨부 | 파일 저장소. 시안에 이미지 업로드가 없어 MVP에는 없다 |

## 5. 확인이 필요한 것

| # | 항목 | 제안 | 정해지지 않으면 |
|---|---|---|---|
| 1 | 프런트 프레임워크 | React + Vite | 프런트 저장소를 만들 수 없다. 백엔드 작업은 영향이 없다 |
| 2 | 호스팅 | 백엔드는 컨테이너를 돌릴 수 있는 곳, DB는 관리형 PostgreSQL, 프런트는 정적 호스팅 | 배포 전까지만 정하면 된다. 비용과 계정이 걸려 있어 제안만 했다 |
| 3 | 도메인 구성 | `c1oser.dev` + `api.c1oser.dev` | 쿠키와 CORS 설정 값이 달라진다 |
| 4 | 성능 목표 | 없음. 예상 사용자 수와 응답 시간 목표를 받아야 한다 | k6의 합격 기준을 적을 수 없다. 측정은 할 수 있다 |

## 6. 백엔드 의존성

`build.gradle`의 의존성이다. 처음 목록은 Maven Central에 Spring Boot 4.1.1용으로 올라와 있는 것을 확인했다 (2026-09-30).

```groovy
dependencies {
	implementation 'org.springframework.boot:spring-boot-starter-webmvc'
	implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
	implementation 'org.springframework.boot:spring-boot-starter-validation'
	implementation 'org.springframework.boot:spring-boot-starter-security'
	implementation 'org.springframework.boot:spring-boot-starter-security-oauth2-resource-server'
	implementation 'org.springframework.boot:spring-boot-starter-flyway'
	implementation 'org.springframework.boot:spring-boot-starter-actuator'
	implementation 'org.flywaydb:flyway-database-postgresql'
	implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1'
	runtimeOnly 'org.postgresql:postgresql'
	runtimeOnly 'io.micrometer:micrometer-registry-prometheus'
	compileOnly 'org.projectlombok:lombok'
	annotationProcessor 'org.projectlombok:lombok'
	developmentOnly 'org.springframework.boot:spring-boot-devtools'
	developmentOnly 'org.springframework.boot:spring-boot-docker-compose'

	testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
	testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'
	testImplementation 'org.springframework.boot:spring-boot-starter-security-test'
	testImplementation 'org.springframework.boot:spring-boot-testcontainers'
	testImplementation 'org.testcontainers:testcontainers-junit-jupiter'
	testImplementation 'org.testcontainers:testcontainers-postgresql'
	testImplementation 'com.tngtech.archunit:archunit-junit5:1.5.1'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

- 버전을 직접 적는 것은 springdoc과 ArchUnit 둘이다. 나머지는 Spring Boot가 관리한다 (PostgreSQL 드라이버 42.7.13, Flyway 12.4.0, Hibernate 7.4.5, Micrometer 1.17.1, Testcontainers 2.0.5, Lombok 1.18.46).
- k6, Prometheus, Grafana는 Gradle 의존성이 아니다. Docker 이미지로 실행한다.
- Lombok은 2026-10-06에, ArchUnit은 2026-10-07에 추가했다.

## 7. 개발 PC 요구사항

| 도구 | 필요 버전 | 이 PC |
|---|---|---|
| JDK | 21 | 21.0.6 |
| Docker | Compose와 Testcontainers가 동작하는 버전. k6, Prometheus, Grafana도 Docker로 실행한다 | 24.0.6 |
| Node.js | 프런트 작업 시 | 22.14.0 |

다른 PC에서 작업하려면 JDK 21과 Docker를 설치해야 한다. 설치 방법은 [Git 가이드](git-guide.md) 1절에 있다.

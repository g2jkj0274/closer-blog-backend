# closer-blog-backend
Closer Blog REST API Server

터미널처럼 쓰는 블로그 c1oser.dev의 백엔드다. Spring Boot 4.1.1, Java 21, PostgreSQL 18.

## 실행 방법

필요한 것: JDK 21, Docker Desktop (설치 방법은 [Git 가이드](docs/git-guide.md) 1절). 명령은 한 줄씩 입력한다.

**앱 실행.** IntelliJ에서 `BlogServerApplication`을 실행하거나 아래 명령을 쓴다.
개발 DB(`compose.yaml`의 PostgreSQL)가 꺼져 있으면 함께 뜨고, 앱을 꺼도 DB는 그대로 둔다.

```
./gradlew bootRun
```

| 주소 | 내용 |
|---|---|
| http://localhost:8080/api/v1 | API ([API 명세서](docs/api-spec.md)) |
| http://localhost:8080/swagger-ui/index.html | API 문서. 운영 프로필에서는 꺼진다 |
| http://localhost:8080/actuator/health | 상태 확인 |

**테스트.** Testcontainers가 테스트용 PostgreSQL을 따로 띄운다. 개발 DB는 건드리지 않는다.

```
./gradlew test
```

**모니터링 (필요할 때만).** 앱을 띄운 뒤 Prometheus와 Grafana를 띄운다. Grafana의 `c1oser.dev` 폴더에 `blog-server` 대시보드가 있다.

```
docker compose -f compose.monitoring.yaml up -d
```

| 주소 | 내용 |
|---|---|
| http://localhost:9090 | Prometheus |
| http://localhost:3000 | Grafana (보기는 로그인 없이, 고치려면 admin / admin) |

끌 때는 `docker compose -f compose.monitoring.yaml down`이다. 개발 DB는 다른 compose 프로젝트라 함께 꺼지지 않는다.

**배포 이미지.** `Dockerfile`은 운영 프로필(`prod`)로 뜨는 이미지를 만든다.

```
docker build -t closer-blog-server .
```

운영 프로필은 아래 환경 변수가 없으면 뜨지 않는다 (`src/main/resources/application-prod.yaml`).

| 환경 변수 | 값 |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://호스트:5432/closer_blog` |
| `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | DB 계정 |
| `JWT_SECRET` | 32바이트 이상의 무작위 문자열 |
| `CORS_ALLOWED_ORIGINS` | 프런트 주소. 여럿이면 쉼표로 구분한다 |

운영에서 API는 8080, health와 지표(Actuator)는 공개하지 않는 8081에 있다. 호스팅의 health check는 `:8081/actuator/health`를 본다.

**CI.** `main`에 푸시하거나 PR을 열면 GitHub Actions가 빌드·테스트하고, Docker 이미지를 띄워 health를 확인한다 (`.github/workflows/ci.yml`).

## 문서

- [작업 목록](docs/task-list.md): 끝낸 작업(끝낸 순서)과 남은 작업(할 순서)
- [기능 명세서](docs/functional-spec.md): 시안 기준 전체 요구사항과 우선순위
- [MVP 범위](docs/mvp-scope.md): MVP에 넣는 것과 빼는 것, 완료 기준, 백엔드 설계
- [기술 스택 결정](docs/tech-stack.md): 백엔드·프런트엔드·배포·모니터링 스택과 고른 이유, 버린 대안
- [DB 설계 (ERD)](docs/erd.md): 테이블, 제약, 인덱스, 첫 마이그레이션 DDL, 조회 규칙
- [API 명세서](docs/api-spec.md): 공통 규약, 패키지 구조, 엔드포인트별 요청·응답과 오류, 명령어와 API 대응
- [여러 PC에서 GitHub 프로젝트 작업하기](docs/git-guide.md): 새 PC 설정(Git, GitHub, JDK, Docker), pull, commit, push 방법과 자주 겪는 문제 해결

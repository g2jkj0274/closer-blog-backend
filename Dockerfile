# syntax=docker/dockerfile:1

# 백엔드 배포 이미지 (docs/tech-stack.md 3.10절). 어디에 올리든 이 이미지를 쓴다.
# 테스트는 CI가 돌리므로 여기서는 jar만 만든다.
#
#   docker build -t closer-blog-server .

# 1단계: jar를 만들고 레이어별로 푼다
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /builder

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY src src
# Gradle 배포판과 의존성을 빌드 사이에 캐시한다 (BuildKit)
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon \
    && cp build/libs/*.jar application.jar
# 의존성과 애플리케이션 코드를 다른 레이어로 나눈다. 코드만 바뀌면 의존성 레이어는 다시 받지 않는다
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# 2단계: 실행 이미지. JDK가 아니라 JRE만 담는다
FROM eclipse-temurin:21-jre
WORKDIR /application

# root가 아닌 사용자로 실행한다
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring

COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./

# 운영 프로필이 기본이다. 필요한 환경 변수는 application-prod.yaml에 적혀 있다
ENV SPRING_PROFILES_ACTIVE=prod
# 컨테이너 메모리 제한의 75%까지 힙으로 쓴다
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]

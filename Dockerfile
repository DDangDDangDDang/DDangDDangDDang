# 운영 배포용 이미지. 테스트는 CI에서 따로 돌리므로 여기서는 jar만 만든다.
#   docker build -t ddang-app .
#   docker run -p 8080:8080 --env-file .env ddang-app

# 1단계: jar 빌드
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 의존성 다운로드를 소스 변경과 분리해 Docker 레이어 캐시를 살린다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null

COPY src src
RUN ./gradlew bootJar --no-daemon -x test \
    && cp build/libs/*-SNAPSHOT.jar app.jar

# 2단계: 실행 이미지 (JRE만 포함)
FROM eclipse-temurin:17-jre
WORKDIR /app

# 힙 크기는 컨테이너 메모리 한도의 75%로 정하고, 메모리가 부족하면 바로 종료해 ECS가 새로 띄우게 한다.
ENV TZ=Asia/Seoul \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

RUN groupadd --system app && useradd --system --gid app app
COPY --from=build --chown=app:app /workspace/app.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

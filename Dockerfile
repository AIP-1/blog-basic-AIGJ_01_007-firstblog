# OwnLog 배포 이미지: React 빌드 결과를 Spring Boot가 함께 제공해요 (포트 하나로 화면 + API)

# 1) React 빌드
FROM node:24-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2) Spring Boot 빌드 (테스트는 GitHub Actions에서 따로 실행해요)
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /app/backend
COPY backend/gradlew backend/build.gradle backend/settings.gradle ./
COPY backend/gradle ./gradle
RUN ./gradlew dependencies --no-daemon > /dev/null   # 의존성만 먼저 받아서 캐시해요 (소스가 바뀌어도 다시 안 받음)
COPY backend/src ./src
COPY --from=frontend /app/frontend/dist ./src/main/resources/static
RUN ./gradlew bootJar --no-daemon

# 3) 실행 이미지 (JDK 대신 가벼운 JRE)
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --no-create-home ownlog && mkdir -p /app/uploads && chown ownlog /app/uploads
COPY --from=backend /app/backend/build/libs/*.jar app.jar
USER ownlog
ENV SPRING_PROFILES_ACTIVE=prod TZ=Asia/Seoul
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

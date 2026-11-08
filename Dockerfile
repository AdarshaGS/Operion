## Backend (Spring Boot) — build stage
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY gradlew build.gradle settings.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

## Backend — runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app

# Run as an unprivileged user. The app writes relative to its working directory - uploads at
# ./data/uploads and backups at ./data/backups (STORAGE_BASE_DIR / BACKUP_DIR) - so that directory
# is created up front and owned by this user; mount a named volume there to persist it.
RUN groupadd --system operion && useradd --system --gid operion --home-dir /app --shell /usr/sbin/nologin operion \
    && mkdir -p /app/data/uploads /app/data/backups \
    && chown -R operion:operion /app

COPY --from=build --chown=operion:operion /app/build/libs/*.jar app.jar

USER operion
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

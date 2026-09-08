# Multi-stage: build with the full JDK, ship a slim runtime.
# Used by Render (or Fly.io) in deployment phase 1b.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy the wrapper and build scripts first so dependency resolution is cached across
# source-only changes.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Never run as root.
RUN groupadd --system dailyforge && useradd --system --gid dailyforge dailyforge
USER dailyforge

COPY --from=build --chown=dailyforge:dailyforge /workspace/build/libs/*.jar app.jar

EXPOSE 8080

# Container-aware heap sizing; the free tiers are small and the default guess is not.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"

# The platform's own health check hits this too (deployment phase 1b).
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD ["sh", "-c", "wget -qO- http://localhost:8080/actuator/health || exit 1"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

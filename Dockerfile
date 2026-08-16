# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY gradle/libs.versions.toml gradle/libs.versions.toml

RUN chmod +x gradlew

COPY src src

# Populate dependency cache first for better layer reuse
RUN ./gradlew dependencies --no-daemon -q > /dev/null 2>&1 || true

RUN ./gradlew assemble --no-daemon

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENV SPRING_PROFILES_ACTIVE=docker
ENV TRADING_MODE=PAPER

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# ============================================================
# BUILD STAGE
# ============================================================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy dependency descriptor first for Docker layer caching
COPY pom.xml .

# Download Maven dependencies
RUN mvn -B dependency:go-offline

# Copy source
COPY src ./src

# Build production artifact
RUN mvn -B clean package -DskipTests


# ============================================================
# RUNTIME STAGE
# ============================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Bring Alpine packages to the latest patched versions
RUN apk upgrade --no-cache \
    && addgroup -S neelastack \
    && adduser -S neelastack -G neelastack

# Copy application
COPY --from=build /app/target/*.jar app.jar

# Secure ownership
RUN chown neelastack:neelastack app.jar

# Run as non-root
USER neelastack

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75"

# Container health check
HEALTHCHECK \
    --interval=30s \
    --timeout=5s \
    --start-period=40s \
    --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
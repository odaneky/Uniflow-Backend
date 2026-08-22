# Build and run the University LMS backend.
#
# Multi-stage so the runtime image carries a JRE and one jar rather than a JDK, Maven, and the
# entire dependency cache.
#
#   cd UniPro-Backend && docker build -f docker/Dockerfile -t university-lms:local .

# ---------- build ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

# Dependencies are resolved in their own layer so that a source-only change does not re-download
# the world on every rebuild.
COPY pom.xml ./pom.xml
RUN apk add --no-cache maven && mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- runtime ----------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Never run as root: a container escape should not begin with privileges.
RUN addgroup -S lms && adduser -S lms -G lms
USER lms

COPY --from=build --chown=lms:lms /build/target/*.jar /app/application.jar

EXPOSE 8080

# Container-aware heap sizing; the JVM otherwise reads the host's memory, not the cgroup limit.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/application.jar"]

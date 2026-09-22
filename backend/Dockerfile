# ---- build stage ----
# The tests are not part of the image build on purpose: they need the full dev dependency tree
# and they are run by `mvn test` and CI. `docker build` producing runnable artifacts is the goal.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Dependency resolution is cached separately from the sources, so editing code does not
# re-download the world.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- runtime stage ----
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=build /workspace/target/*.jar app.jar

# The upload root must be writable by the unprivileged user; compose mounts a volume here.
RUN mkdir -p /app/data/uploads && chown -R app:app /app

USER app
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
  CMD wget -qO- http://localhost:8080/api/health | grep -q '"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

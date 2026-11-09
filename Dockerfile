# Stage 1: build the jar with Maven. Uses the official Maven image instead of ./mvnw so a
# Windows checkout with CRLF line endings in the wrapper script cannot break the build.
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app

# Copy only the pom first: dependencies are downloaded once and cached until pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests need Postgres and Redis, which do not exist inside the build. They run locally (and in CI later).
RUN mvn -B -q package -DskipTests

# Stage 2: only a JRE and the jar. No Maven, no source code, no compiler in the image that runs.
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as an unprivileged user, not root.
RUN useradd --system --uid 10001 linkpulse
COPY --from=build /app/target/linkpulse-*.jar app.jar
USER linkpulse

# Free hosts give ~512 MB in total. Cap the heap at 70% of the container's memory so the JVM's
# own non-heap memory (metaspace, threads, code cache) still fits, and keep thread stacks small.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:+ExitOnOutOfMemoryError"

# The platform sets PORT; application.yml reads it. 8080 is only the local default.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# Multi-stage build: compile with a full JDK+Maven image, run on a slim JRE.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Warm the dependency cache in its own layer, invalidated only by pom.xml
# changes. go-offline is deliberately avoided: it also pulls every test-scope
# dependency (Testcontainers and friends) plus every plugin dependency, which
# is a lot of download for an image that never runs tests.
RUN mvn -B -q dependency:resolve
COPY src ./src
# maven.test.skip (not just skipTests) skips test *compilation* too, so the
# test-scope dependencies never need resolving in the image at all.
RUN mvn -B -q package -Dmaven.test.skip=true

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/clinic-booking-backend-*.jar app.jar
EXPOSE 8080
# Container-aware heap sizing: the JVM's default max heap is a fraction of
# host RAM, which is too tight on a 512MB free-tier instance once Hibernate,
# Tomcat and Flyway are all up. SerialGC keeps GC overhead small at this size.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+UseSerialGC", "-jar", "app.jar"]

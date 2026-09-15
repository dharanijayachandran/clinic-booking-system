# Multi-stage build: compile with a full JDK+Maven image, run on a slim JRE.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Cache dependency downloads in their own layer, invalidated only by pom.xml changes.
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/clinic-booking-backend-*.jar app.jar
EXPOSE 8080
# Container-aware heap sizing: the JVM's default max heap is a fraction of
# host RAM, which is too tight on a 512MB free-tier instance once Hibernate,
# Tomcat and Flyway are all up. SerialGC keeps GC overhead small at this size.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+UseSerialGC", "-jar", "app.jar"]

# Single-image build: the Angular app is compiled and baked into the Spring
# Boot jar as static resources, so the whole system runs as ONE service on ONE
# origin.
#
# That isn't only a free-tier concession, it's what keeps the auth design
# intact: cookies and the CSRF double-submit both depend on the browser
# treating app and API as the same origin, and a WebSocket upgrade survives
# far more reliably without a second proxy hop in front of it.
#
# docker-compose still runs backend and frontend as separate services for
# local development, where the Angular dev server's proxy provides the same
# same-origin guarantee with live reload.

FROM node:20-alpine AS frontend
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /app
COPY backend/pom.xml .
RUN mvn -B -q dependency:resolve
COPY backend/src ./src
# Spring Boot serves classpath:/static, so dropping the built SPA here bakes
# it straight into the jar.
COPY --from=frontend /frontend/dist/frontend/browser ./src/main/resources/static
RUN mvn -B -q package -Dmaven.test.skip=true

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=backend /app/target/clinic-booking-backend-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+UseSerialGC", "-jar", "app.jar"]

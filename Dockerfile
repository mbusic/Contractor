# 1. Build the Angular app
FROM node:22-slim AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2. Build the Spring Boot jar, with the Angular app in static/ so it's served from the jar
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /backend
COPY backend/ ./
COPY --from=frontend /frontend/dist/frontend/browser/ src/main/resources/static/
# bootJar doesn't run the tests
RUN ./gradlew bootJar --no-daemon

# 3. Runtime: only the JRE and the jar
FROM eclipse-temurin:21-jre
# Photos go to ./uploads, so /app/uploads - mount a volume there to keep them across deploys
WORKDIR /app
COPY --from=backend /backend/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

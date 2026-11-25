FROM node:22-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /app
COPY backend/pom.xml ./
RUN mvn -B dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /app/frontend/dist ./src/main/resources/static
RUN mvn -B verify

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S admissions && adduser -S admissions -G admissions
WORKDIR /app
COPY --from=backend --chown=admissions:admissions /app/target/admissions-1.0.0.jar ./app.jar
USER admissions
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]

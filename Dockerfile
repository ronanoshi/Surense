# syntax=docker/dockerfile:1
#
# Multi-stage build so `docker build` works from a clean clone without running Maven locally first.
# Runtime expects MySQL (or compatible JDBC URL) via Spring env overrides — see README.

FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build /build/target/customer-support-hub-0.0.1-SNAPSHOT.jar app.jar
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

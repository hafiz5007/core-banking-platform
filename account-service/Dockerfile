# Multi-stage build for the account-service.
# Build stage uses Maven + JDK 21; runtime uses a slim JRE 21.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# The parent pom declares every module, so Maven needs the whole reactor present even to build
# one service; .dockerignore keeps the context small.
COPY . .
RUN mvn -q -pl account-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/account-service/target/account-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]

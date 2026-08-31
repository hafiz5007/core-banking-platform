# Multi-stage build for the notification-service.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# The parent pom declares every module, so Maven needs the whole reactor present even to
# build one service. .dockerignore keeps the context small.
COPY . .
RUN mvn -q -pl notification-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/notification-service/target/notification-service-*.jar app.jar
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "app.jar"]

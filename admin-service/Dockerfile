# Multi-stage build for the admin-service.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# The parent pom declares every module, so Maven needs the whole reactor present even to
# build one service. .dockerignore keeps the context small.
COPY . .
RUN mvn -q -pl admin-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/admin-service/target/admin-service-*.jar app.jar
EXPOSE 8089
ENTRYPOINT ["java", "-jar", "app.jar"]

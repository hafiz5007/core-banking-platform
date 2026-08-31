# Multi-stage build for the api-gateway.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# The parent pom declares every module, so Maven needs the whole reactor present even to
# build one service. .dockerignore keeps the context small.
COPY . .
RUN mvn -q -pl api-gateway -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/api-gateway/target/api-gateway-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

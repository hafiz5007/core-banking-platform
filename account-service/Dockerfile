# Multi-stage build for the account-service.
# Build stage uses Maven + JDK 21; runtime uses a slim JRE 21.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# Copy the whole reactor so the module can resolve its parent + common-lib.
COPY pom.xml .
COPY common-lib common-lib
COPY account-service account-service
RUN mvn -q -pl account-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/account-service/target/account-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]

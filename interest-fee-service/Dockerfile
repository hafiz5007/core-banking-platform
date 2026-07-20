# Multi-stage build for the interest-fee-service.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common-lib common-lib
COPY interest-fee-service interest-fee-service
RUN mvn -q -pl interest-fee-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/interest-fee-service/target/interest-fee-service-*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]

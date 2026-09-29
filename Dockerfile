FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=build /workspace/target/xein-0.0.1-SNAPSHOT.jar /app/xein.jar
EXPOSE 8080 8443
ENTRYPOINT ["java", "-jar", "/app/xein.jar"]

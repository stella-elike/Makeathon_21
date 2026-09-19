# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Download the OpenTelemetry Java auto-instrumentation agent.
# This single jar attaches to the JVM and instruments Spring MVC, RestTemplate/
# WebClient, JDBC, logging frameworks, etc. automatically — no code changes.
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar /app/otel-javaagent.jar

COPY --from=build /app/target/*.jar /app/app.jar

ENTRYPOINT ["java", "-javaagent:/app/otel-javaagent.jar", "-jar", "/app/app.jar"]

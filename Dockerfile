# Stage 1: Build with Maven
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests --no-transfer-progress

# Stage 2: Minimal runtime
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN addgroup --system btl && adduser --system --ingroup btl btl
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN chown btl:btl app.jar
USER btl
EXPOSE 8080
HEALTHCHECK --interval=60s --timeout=30s --start-period=210s --retries=3 \
    CMD curl -f http://$(hostname -i):8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]

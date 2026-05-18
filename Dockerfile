# Stage 1: Build with Maven
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q 2>/dev/null || mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests --no-transfer-progress

# Stage 2: Minimal runtime
FROM eclipse-temurin:21-jre
RUN addgroup --system btl && adduser --system --ingroup btl btl
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN chown btl:btl app.jar
USER btl
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]

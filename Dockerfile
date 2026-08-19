# syntax=docker/dockerfile:1
# Multi-stage Dockerfile for Backend Service

# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy core-model & backend-service
COPY core-model/ core-model/
COPY backend-service/ backend-service/

RUN chmod +x backend-service/mvnw

WORKDIR /workspace/core-model
RUN ../backend-service/mvnw clean install -DskipTests

WORKDIR /workspace/backend-service
RUN ./mvnw clean package -DskipTests -Dcheckstyle.skip=true

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S erp && adduser -S erp -G erp
USER erp:erp

COPY --from=builder /workspace/backend-service/target/backend-service-*.jar app.jar

ENV JAVA_OPTS="-Xms256m -Xmx1024m -XX:+UseG1GC"
ENV SPRING_PROFILES_ACTIVE="dev"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]

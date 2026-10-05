# ============================================================
# BUILD STAGE
# ============================================================
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

COPY . .

RUN chmod +x gradlew

RUN ./gradlew clean bootJar -x test


# ============================================================
# RUNTIME STAGE
# ============================================================
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
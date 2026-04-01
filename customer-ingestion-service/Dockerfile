FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app
RUN apk add --no-cache dos2unix

COPY . .
RUN dos2unix gradlew && chmod +x gradlew
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build /app/build/libs/*.jar /app/app.jar

RUN chown -R appuser:appgroup /app
USER appuser

WORKDIR /app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

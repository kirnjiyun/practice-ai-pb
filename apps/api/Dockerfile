FROM gradle:8.14.3-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle bootJar --no-daemon
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S aipb && adduser -S aipb -G aipb
COPY --from=build /workspace/build/libs/aipb-api.jar app.jar
USER aipb
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

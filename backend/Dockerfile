FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY android/gradlew ./android/gradlew
COPY android/gradle/wrapper ./android/gradle/wrapper
COPY backend/build.gradle.kts backend/settings.gradle.kts ./backend/

RUN sed -i 's/\r$//' ./android/gradlew && chmod +x ./android/gradlew

COPY backend/src/main ./backend/src/main

RUN ./android/gradlew -p ./backend bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

COPY --from=build /workspace/backend/build/libs/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY backend/ .
RUN chmod +x mvnw && ./mvnw -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/backend-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

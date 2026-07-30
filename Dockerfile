FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY backend/ .

RUN chmod +x mvnw

RUN ./mvnw clean package -DskipTests

EXPOSE 8081

CMD ["java","-jar","target/backend-0.0.1-SNAPSHOT.jar"]
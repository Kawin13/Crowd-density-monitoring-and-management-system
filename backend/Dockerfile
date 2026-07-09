FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p uploads reports && chown -R spring:spring /app
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx512m", "-Xms256m", "-jar", "app.jar"]

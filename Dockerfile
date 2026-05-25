FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /app/target/uiApplications-1.0-SNAPSHOT.jar app.jar

EXPOSE 8080

CMD ["java", "-Dserver.address=0.0.0.0", "-Dserver.port=${PORT:-8080}", "-Dsokker.open-browser=false", "-jar", "app.jar"]

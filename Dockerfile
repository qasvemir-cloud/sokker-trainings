FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /app/target/classes ./target/classes
COPY --from=build /app/target/classpath.txt ./target/classpath.txt

EXPOSE 8080
CMD ["sh", "-c", "java -Dserver.address=0.0.0.0 -Dserver.port=${PORT:-8080} -Dsokker.open-browser=false -cp \"target/classes:$(cat target/classpath.txt)\" org.velja.app.sokker.SokkerManagerWebApp"]

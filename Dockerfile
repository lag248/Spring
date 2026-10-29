FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
ARG MODULE
COPY . .
RUN mvn -B -pl "${MODULE}" -am -DskipTests package && cp "${MODULE}"/target/*.jar /tmp/application.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /tmp/application.jar /app/application.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/application.jar"]

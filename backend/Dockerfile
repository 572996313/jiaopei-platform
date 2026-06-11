# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace/backend

ARG MAVEN_OPTS="-Xmx512m -XX:MaxMetaspaceSize=256m -Djava.awt.headless=true"
ENV MAVEN_OPTS=$MAVEN_OPTS

COPY backend/settings-docker.xml /root/.m2/settings.xml
COPY backend/pom.xml ./
RUN mvn -B -ntp -s /root/.m2/settings.xml -DskipTests \
    -Dmaven.wagon.http.retryHandler.count=5 \
    -Dmaven.wagon.httpconnectionManager.ttlSeconds=60 \
    dependency:go-offline \
  || mvn -B -ntp -s /root/.m2/settings.xml -DskipTests \
    -Dmaven.wagon.http.retryHandler.count=5 \
    dependency:go-offline

COPY backend/src ./src
RUN mvn -B -ntp -s /root/.m2/settings.xml -DskipTests \
    -Dmaven.wagon.http.retryHandler.count=5 \
    package

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

ENV SERVER_PORT=8080
ENV JAVA_OPTS=""

COPY --from=build /workspace/backend/target/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]

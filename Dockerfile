FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
COPY benchmark/java/MqProbe.java benchmark/java/MqProbe.java
RUN mvn -B -DskipTests package dependency:build-classpath -Dmdep.outputFile=target/probe-classpath.txt \
    && mkdir -p target/probes \
    && javac -cp "$(cat target/probe-classpath.txt)" -d target/probes benchmark/java/MqProbe.java

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /app/logs /app/.local && chown -R 10001:10001 /app
COPY --from=build /build/target/flashflow-1.0.0.jar /app/flashflow.jar
COPY --from=build /build/target/probes /app/probes
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=65","-jar","/app/flashflow.jar"]

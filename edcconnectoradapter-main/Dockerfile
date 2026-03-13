FROM maven:3.9.4-eclipse-temurin-21-alpine AS builder

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests


FROM eclipse-temurin:21


RUN groupadd -g 1001 simplgroup && useradd -u 1001 -g simplgroup -m simpluser


WORKDIR /home/simpluser

#copy the pipeline.variables.sh file for return microservice version in /status endpoint
COPY pipeline.variables.sh .

# the resulting absolute jar path must be also configured in values.yaml (see appJarPath)
COPY --from=builder /app/target/*.jar app.jar
RUN chown -R simpluser:simplgroup /home/simpluser


USER simpluser


ENTRYPOINT ["java", "-jar", "/home/simpluser/app.jar"]


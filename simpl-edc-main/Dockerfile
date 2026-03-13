FROM eclipse-temurin:21-jdk-alpine

COPY target/basic-connector.jar connector.jar
COPY otel/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar

ENV JAVA_TOOL_OPTIONS="-javaagent:/otel/opentelemetry-javaagent.jar"

RUN mkdir /files && chmod 775 /files
RUN adduser -u 8877 -D dockerUser
RUN chown -R dockerUser:dockerUser /files

USER dockerUser

ENTRYPOINT ["java", "-javaagent:/otel/opentelemetry-javaagent.jar", "-jar", "/connector.jar"]

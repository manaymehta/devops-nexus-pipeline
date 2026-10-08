# Runtime-only image: the jar is built (and later downloaded from Nexus) BEFORE
# `docker build`, so the image always contains the exact artifact that was tested.
#   docker build --build-arg JAR_FILE=target/devops-app-1.0.0.jar -t devops-app:1.0.0 .
FROM eclipse-temurin:21-jre-alpine

# Don't run the app as root inside the container.
RUN addgroup -S app && adduser -S app -G app
WORKDIR /opt/app

ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
RUN chown app:app app.jar
USER app

EXPOSE 8080

# Docker marks the container "unhealthy" if Spring Boot's health check stops answering.
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/opt/app/app.jar"]

# Imagen mínima para inscription-service — usada por e2e-tests y docker-compose.e2e.yml
# Requiere: mvn package -DskipTests -pl inscription-service antes de construir
FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
  && apt-get install -y --no-install-recommends wget \
  && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY target/inscription-service-*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]

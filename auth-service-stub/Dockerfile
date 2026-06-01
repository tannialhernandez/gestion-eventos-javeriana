# Imagen mínima para auth-service-stub — login demo reemplazable por IdP real.
# Requiere: mvn -f auth-service-stub/pom.xml package -DskipTests
FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
  && apt-get install -y --no-install-recommends wget \
  && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY target/auth-service-stub-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]

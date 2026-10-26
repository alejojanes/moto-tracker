# Compila y empaqueta el backend. Uso: docker build -t moto-tracker .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY tracker-domain/pom.xml tracker-domain/
COPY tracker-application/pom.xml tracker-application/
COPY tracker-adapter-traccar/pom.xml tracker-adapter-traccar/
COPY tracker-adapter-persistence/pom.xml tracker-adapter-persistence/
COPY tracker-adapter-web/pom.xml tracker-adapter-web/
COPY tracker-adapter-telegram/pom.xml tracker-adapter-telegram/
COPY tracker-bootstrap/pom.xml tracker-bootstrap/
RUN mvn -B -q dependency:go-offline -DexcludeReactor=true || true
COPY . .
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /src/tracker-bootstrap/target/moto-tracker.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "app.jar"]

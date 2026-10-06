# syntax=docker/dockerfile:1.7
# Kontekst — repo ildizi
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY olima-backend/pom.xml olima-backend/
COPY olima-backend/src olima-backend/src
# .m2 kesh mount'i: har deploy'da bog'liqliklar qayta yuklanmaydi.
# Testlar CI'da (Testcontainers bilan) ishlaydi — bu yerda Docker-in-Docker yo'q.
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -B -q package -pl olima-backend -am -DskipTests \
    && cp olima-backend/target/olima-backend-*.jar /app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S -G app app \
    && mkdir -p /data/documents && chown -R app:app /data
COPY --from=build /app.jar app.jar
USER app
ENV DOCUMENT_STORAGE_PATH=/data/documents \
    SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080 8081
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://127.0.0.1:${MANAGEMENT_PORT:-8081}/actuator/health/liveness | grep -q UP || exit 1
ENTRYPOINT ["java","-jar","/app/app.jar"]

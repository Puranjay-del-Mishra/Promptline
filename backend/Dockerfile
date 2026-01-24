# ---- build stage ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY . .

RUN set -eux; \
    mvn -DskipTests clean package; \
    echo "=== listing /app ==="; ls -la; \
    echo "=== listing /app/build ==="; ls -la build; \
    JAR="$(ls -1 build/*.jar | grep -v '\.original$' | head -n 1)"; \
    echo "Built jar: $JAR"; \
    test -f "$JAR"; \
    cp "$JAR" /app/app.jar

# ---- run stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]

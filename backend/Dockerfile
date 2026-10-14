# syntax=docker/dockerfile:1
FROM eclipse-temurin:21.0.12.1_1-jdk-noble AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml checkstyle.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
# Las pruebas corren en CI (necesitan Docker para Testcontainers); aquí solo se empaqueta.
RUN ./mvnw -B -q package -DskipTests -Djacoco.skip=true \
 && java -Djarmode=tools -jar target/formulario-edi-backend.jar extract --destination extracted

FROM eclipse-temurin:21.0.12.1_1-jre-noble
LABEL org.opencontainers.image.title="formulario-edi-backend" \
      org.opencontainers.image.description="API del Formulario Web EDI/EDID" \
      org.opencontainers.image.authors="Oscar Dueñas" \
      org.opencontainers.image.documentation="README.md del repositorio formulario-web"
RUN groupadd --system app && useradd --system --gid app --home-dir /app --no-create-home app \
 && mkdir -p /app /data/rules && chown -R app:app /app /data/rules
WORKDIR /app
COPY --from=build --chown=app:app /workspace/extracted/lib ./lib
COPY --from=build --chown=app:app /workspace/extracted/formulario-edi-backend.jar ./app.jar
USER app
ENV RULES_DIR=/data/rules \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
VOLUME ["/data/rules"]
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health/liveness HTTP/1.0\r\n\r\n' >&3 && grep -q UP <&3"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

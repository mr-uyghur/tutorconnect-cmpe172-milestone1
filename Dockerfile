# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Dependencies first so this layer is cached until pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app \
 && mkdir /data && chown app:app /data
WORKDIR /app
COPY --from=build --chown=app:app /build/target/tutorconnect-1.0.0.jar app.jar
USER app
# Bind all interfaces (app default is 127.0.0.1); SQLite file lives on the /data volume.
ENV SERVER_ADDRESS=0.0.0.0 \
    TUTORCONNECT_DB_URL=jdbc:sqlite:/data/tutorconnect.db
VOLUME /data
EXPOSE 8080
# The JRE image has no guaranteed curl/wget; bash's /dev/tcp needs no extra packages.
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
 CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /health HTTP/1.0\\r\\n\\r\\n' >&3 && head -n1 <&3 | grep -q ' 200 '"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

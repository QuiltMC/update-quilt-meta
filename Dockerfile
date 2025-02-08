ARG JAVA_VERSION=21
ARG VERSION

FROM eclipse-temurin:${JAVA_VERSION} AS builder

ENV VERSION=${VERSION}

WORKDIR /src
COPY . .
RUN ./gradlew shadowJar

FROM eclipse-temurin:${JAVA_VERSION} AS runtime

WORKDIR /app
COPY entrypoint.sh .

COPY --from=builder /src/build/libs/*-all.jar app.jar

ENTRYPOINT ["/app/entrypoint.sh"]

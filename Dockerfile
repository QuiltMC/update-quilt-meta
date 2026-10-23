ARG JAVA_VERSION=25

FROM eclipse-temurin:${JAVA_VERSION} AS builder
ARG VERSION
ENV VERSION=${VERSION}

WORKDIR /src
COPY . .
RUN ./gradlew shadowJar

FROM eclipse-temurin:${JAVA_VERSION} AS runtime

WORKDIR /app

COPY --from=builder /src/build/libs/*-all.jar update-quilt-meta.jar

ENTRYPOINT ["java", "-jar", "/app/update-quilt-meta.jar"]

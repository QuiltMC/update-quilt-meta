ARG JAVA_VERSION=21

FROM eclipse-temurin:${JAVA_VERSION} AS builder
ARG VERSION
ENV VERSION=${VERSION}

WORKDIR /src
COPY . .
RUN ./gradlew shadowJar

FROM eclipse-temurin:${JAVA_VERSION} AS runtime

WORKDIR /app

COPY --from=builder /src/build/libs/*-all.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

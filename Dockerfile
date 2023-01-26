FROM amazoncorretto:17 as builder

WORKDIR /builder
COPY . .
RUN ./gradlew shadowJar

FROM amazoncorretto:17

WORKDIR /app
COPY entrypoint.sh .
ENTRYPOINT ["/app/entrypoint.sh"]

ARG git_sha="development"
ENV GIT_SHA=$git_sha

COPY --from=builder /builder/build/libs/*.jar app.jar
# ---- build: compile the code and collect the runtime dependency jars ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -DskipTests compile dependency:copy-dependencies \
        -DincludeScope=runtime -DoutputDirectory=target/lib

# ---- runtime: a small Java runtime, same classes started three different ways ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=build /build/target/classes /app/classes
COPY --from=build /build/target/lib /app/lib
ENTRYPOINT ["java", "-cp", "/app/classes:/app/lib/*"]
CMD ["com.example.matchaggregator.MatchAggregatorApplication"]
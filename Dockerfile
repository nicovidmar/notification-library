# Build tools stay in this stage. No Maven Wrapper or host target/ is required.
FROM maven:3.10.0-eclipse-temurin-21-noble AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
COPY examples ./examples
RUN mvn -B -ntp clean verify \
    && mkdir -p target/demo-classes \
    && javac --release 21 -encoding UTF-8 \
       -cp target/notification-library-0.1.0-SNAPSHOT.jar \
       -d target/demo-classes examples/*.java

# The library has no external runtime dependencies; demos are separate classes.
FROM eclipse-temurin:21-jre-noble AS runtime
WORKDIR /app
COPY --from=build /build/target/notification-library-0.1.0-SNAPSHOT.jar ./lib/notification-library.jar
COPY --from=build /build/target/demo-classes ./demos
USER 10001:10001
ENTRYPOINT ["java", "-cp", "/app/lib/notification-library.jar:/app/demos"]
CMD ["EmailDemo"]

FROM gradle:jdk23-graal as gradle

COPY ./ ./

RUN gradle installDist

FROM openjdk:23-slim

WORKDIR /app

COPY --from=gradle /home/gradle/build/install/countryDaysTrackerBotKotlin/ ./

RUN apt-get update && apt-get install -y curl && apt-get clean && rm -rf /var/lib/apt/lists/*
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl --fail http://localhost:81/health || exit 1

JAVA_OPTS="--add-opens=java.base/java.lang=ALL-UNNAMED"
exec java $JAVA_OPTS -cp "$CLASSPATH" MainKt "$@"
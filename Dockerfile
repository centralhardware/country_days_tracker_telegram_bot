FROM gradle:jdk21-graal as gradle

COPY ./ ./

RUN gradle fatJar

FROM findepi/graalvm:java21

WORKDIR /znatokiBot

COPY --from=gradle /home/gradle/build/libs/countryDaysTrackerBotKotlin-1.0-SNAPSHOT-standalone.jar .

RUN apt-get update && apt-get install -y curl && apt-get clean && rm -rf /var/lib/apt/lists/*
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl --fail http://localhost:81/health || exit 1


CMD ["java", "-jar", "countryDaysTrackerBotKotlin-1.0-SNAPSHOT-standalone.jar" ]


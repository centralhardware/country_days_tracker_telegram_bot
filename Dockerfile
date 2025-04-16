FROM gradle:jdk22-graal as gradle

COPY ./ ./

RUN gradle installDist

FROM findepi/graalvm:java22

WORKDIR /app

COPY --from=gradle /home/gradle/build/install/country_days_tracker_bot/ ./

RUN apt-get update && apt-get install -y curl && apt-get clean && rm -rf /var/lib/apt/lists/*
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl --fail http://localhost:81/health || exit 1

CMD ["./bin/country_days_tracker_bot", "--add-opens", "java.base/java.lang=ALL-UNNAMED"]

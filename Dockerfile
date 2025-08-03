FROM gradle:jdk24-graal as gradle

COPY ./ ./

RUN gradle installDist

FROM openjdk:24-slim

WORKDIR /app

COPY --from=gradle /home/gradle/build/install/countryDaysTrackerBotKotlin/ ./

CMD ["./bin/countryDaysTrackerBotKotlin", "--add-opens", "java.base/java.lang=ALL-UNNAMED"]

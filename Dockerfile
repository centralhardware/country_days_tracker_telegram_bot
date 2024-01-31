FROM maven:3.9.4-amazoncorretto-21 as maven

COPY ./ ./

RUN gradle fatJar

FROM openjdk:21-slim

WORKDIR /znatokiBot

COPY --from=maven build/libs/countryDaysTrackerBotKotlin-1.0-SNAPSHOT-standalone.jar .

CMD ["java", "-jar", "countryDaysTrackerBotKotlin-1.0-SNAPSHOT-standalone.jar" ]


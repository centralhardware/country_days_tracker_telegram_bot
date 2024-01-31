FROM gradle:jdk21-alpine as gradle

COPY ./ ./

RUN gradle fatJar
RUN cd build/libs/
RUN pwd

FROM openjdk:21-slim

WORKDIR /znatokiBot

COPY --from=gradle build/libs/countryDaysTrackerBotKotlin-1.0-SNAPSHOT-standalone.jar .

CMD ["java", "-jar", "countryDaysTrackerBotKotlin-1.0-SNAPSHOT-standalone.jar" ]


FROM maven:3.9.4-amazoncorretto-21 as maven

COPY ./ ./

RUN mvn package

FROM openjdk:21-slim

WORKDIR /znatokiBot

COPY --from=maven target/countryDaysTrackerTelegramBot-1.0-SNAPSHOT.jar .

CMD ["java", "-jar", "countryDaysTrackerTelegramBot-1.0-SNAPSHOT.jar" ]

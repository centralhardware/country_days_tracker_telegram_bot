FROM gradle:jdk22-graal as gradle

COPY ./ ./

RUN gradle shadowJar

FROM findepi/graalvm:java22

WORKDIR /znatokiBot

COPY --from=gradle /home/gradle/build/libs/shadow-1.0-SNAPSHOT-all.jar .

RUN apt-get update && apt-get install -y curl && apt-get clean && rm -rf /var/lib/apt/lists/*
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl --fail http://localhost:81/health || exit 1


CMD ["java", "--add-opens", "java.base/java.lang=ALL-UNNAMED", "-jar", "shadow-1.0-SNAPSHOT-all.jar" ]


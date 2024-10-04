plugins {
    kotlin("jvm") version "2.0.20"
}

group = "me.centralhardware"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("dev.inmo:tgbotapi:17.0.0")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.3.1")
    implementation("com.clickhouse:clickhouse-jdbc:0.6.4")
    implementation("org.lz4:lz4-java:1.8.0")
    implementation("org.slf4j:slf4j-simple:2.0.16")
    implementation("org.ocpsoft.prettytime:prettytime:5.0.9.Final")
    implementation("com.github.seratch:kotliquery:1.9.0")
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.6")
    implementation("ch.qos.logback:logback-core:1.5.7")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

tasks {
    val fatJar = register<Jar>("fatJar") {
        dependsOn.addAll(listOf("compileJava", "compileKotlin", "processResources")) // We need this for Gradle optimization to work
        archiveClassifier.set("standalone") // Naming the jar
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        manifest { attributes(mapOf("Main-Class" to "MainKt")) } // Provided we set it up in the application plugin configuration
        val sourcesMain = sourceSets.main.get()
        val contents = configurations.runtimeClasspath.get()
            .map { if (it.isDirectory) it else zipTree(it) } +
                sourcesMain.output
        from(contents)
    }
    build {
        dependsOn(fatJar) // Trigger fat jar creation during build
    }
}
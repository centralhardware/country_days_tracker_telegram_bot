import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.0.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "me.centralhardware"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

val ktorVersion = "2.3.12";

dependencies {
    implementation("dev.inmo:tgbotapi:18.2.2")
    implementation("com.github.centralhardware:telegram-bot-commons:f55a30ee2b")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.4")
    implementation("com.clickhouse:clickhouse-jdbc:0.6.5")
    implementation("org.lz4:lz4-java:1.8.0")
    implementation("org.ocpsoft.prettytime:prettytime:5.0.9.Final")
    implementation("com.github.seratch:kotliquery:1.9.0")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

tasks {
    named<ShadowJar>("shadowJar") {
        archiveBaseName.set("shadow")
        mergeServiceFiles()
        manifest {
            attributes(mapOf("Main-Class" to "MainKt"))
        }
    }
}
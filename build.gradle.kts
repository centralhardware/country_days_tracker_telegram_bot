plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    application
}

group = "me.centralhardware"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("MainKt")
    applicationDefaultJvmArgs = listOf(
        "--add-opens=java.base/java.lang=ALL-UNNAMED"
    )
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

val ktorVersion = "3.1.2";
val clickhouseVersion = "0.8.5"

dependencies {
    implementation("dev.inmo:tgbotapi:24.0.2")
    implementation("com.github.centralhardware:ktgbotapi-commons:6ef1dde4fe")
    implementation("com.github.centralhardware:ktgbotapi-restrict-access-middleware:33a3f2e3d4")
    implementation("com.clickhouse:clickhouse-jdbc:$clickhouseVersion")
    implementation("com.clickhouse:clickhouse-http-client:$clickhouseVersion")
    implementation("org.lz4:lz4-java:1.8.0")
    implementation("com.github.seratch:kotliquery:1.9.1")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
}

tasks.test {
    useJUnitPlatform()
}

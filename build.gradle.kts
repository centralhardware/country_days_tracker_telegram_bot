plugins {
    kotlin("jvm") version "2.0.21"
}

group = "me.centralhardware"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://nexus.inmo.dev/repository/maven-releases/")
}

val ktorVersion = "2.3.12";

dependencies {
    implementation("dev.inmo:tgbotapi:18.2.2-branch_18.2.2-build2465")
    implementation("com.github.centralhardware:telegram-bot-commons:1e503cc156")
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
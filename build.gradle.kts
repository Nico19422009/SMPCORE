plugins {
    java
}

group = "com.smpcore"
version = "1.0.0-SNAPSHOT"

description = "Paper plugin to configure SMP server core profile and features."

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.processResources {
    filesMatching(listOf("plugin.yml", "paper-plugin.yml")) {
        expand(mapOf("projectVersion" to project.version))
    }
}

tasks.jar {
    archiveBaseName.set("smpcore")
}

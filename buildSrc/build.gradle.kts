plugins {
    java
    `kotlin-dsl`
    id("io.github.gradle-nexus.publish-plugin") version "1.0.0"
    id("org.sonarqube") version "5.0.0.4638"
}

repositories {
    mavenCentral()
}

dependencies {
}

plugins {
    kotlin("jvm") version "1.8.20"
    id("java-library")
    id("maven-publish")
}

group = "com.kylecorry"
version = "0.1.0"

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = group.toString()
                artifactId = "tasks"
                version = version
                from(components["java"])
            }
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.2")
    testImplementation("org.junit.platform:junit-platform-runner:1.9.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}
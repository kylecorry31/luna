plugins {
    kotlin("jvm") version "2.2.21"
    id("java-library")
    id("com.vanniktech.maven.publish") version "0.34.0"
}

val versionName = "1.1.0"

group = "com.kylecorry"
version = versionName

mavenPublishing {
    coordinates("com.kylecorry", "luna", versionName)

    pom {
        name.set("Luna")
        description.set("A library of useful Kotlin utilities.")
        url.set("https://github.com/kylecorry31/luna")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("kylecorry31")
                name.set("Kyle Corry")
                email.set("kylecorry31@gmail.com")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/kylecorry31/luna.git")
            developerConnection.set("scm:git:ssh://github.com:kylecorry31/luna.git")
            url.set("https://github.com/kylecorry31/luna")
        }
    }

    publishToMavenCentral()
    signAllPublications()
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("org.junit.platform:junit-platform-runner:1.10.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
}

tasks.test {
    useJUnitPlatform()
}

// Setup javadocs
tasks.withType<Javadoc> {
    // Add the sources to the javadoc task
    source = sourceSets.main.get().allJava
    classpath += files(sourceSets.main.get().compileClasspath)
    // Only include public and protected classes
    include("**/public/**", "**/protected/**")
}

kotlin {
    jvmToolchain(11)
}
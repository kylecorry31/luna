import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.21"
    id("java-library")
    id("com.vanniktech.maven.publish") version "0.37.0"
}

val versionName = "2.4.1"

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
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

configurations.named("testCompileClasspath") {
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
}

configurations.named("testRuntimeClasspath") {
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
}

tasks.test {
    useJUnitPlatform()
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.named<KotlinCompile>("compileTestKotlin") {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

tasks.named<JavaCompile>("compileTestJava") {
    javaCompiler = javaToolchains.compilerFor {
        languageVersion = JavaLanguageVersion.of(17)
    }
    sourceCompatibility = "17"
    targetCompatibility = "17"
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
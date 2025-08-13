plugins {
    kotlin("jvm") version "2.2.0"
}

group = rootProject.group
version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testImplementation(gradleTestKit())
    // Kotlin compiler embeddable for unit tests using compiler utils
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.0")
}

kotlin {
    jvmToolchain(24)
}

tasks.test {
    useJUnitPlatform()
    dependsOn(":plugin:jar")
}

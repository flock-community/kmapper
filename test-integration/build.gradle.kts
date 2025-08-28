plugins {
    kotlin("jvm") version "2.2.20-RC"
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
    implementation(gradleTestKit())
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.20-RC")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
    dependsOn(":compiler-plugin:jar")
    dependsOn(":gradle-plugin:publishToMavenLocal")
    dependsOn(":compiler-plugin:publishToMavenLocal")
    dependsOn(":compiler-runtime:publishToMavenLocal")
}

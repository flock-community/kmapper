plugins {
    kotlin("jvm") version "2.2.20-RC"
    id("maven-publish")
}

group = rootProject.group
version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(kotlin("compiler-embeddable"))
}

kotlin {
    jvmToolchain(17)
}


publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
plugins {
    kotlin("jvm") version "2.2.0"
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

plugins {
    kotlin("jvm")
    id("com.gradleup.kctf").version("2.2.20-RC-0.0.0")
}

group = rootProject.group
version = rootProject.version

kotlin {
    jvmToolchain(21)
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        resources.setSrcDirs(listOf("resources"))
    }
    test {
        java.setSrcDirs(listOf("test"))
        resources.setSrcDirs(listOf("testResources"))
    }
}
dependencies {
    testImplementation("com.gradleup.kctf:kctf-runtime:2.2.20-RC-0.0.0")
}

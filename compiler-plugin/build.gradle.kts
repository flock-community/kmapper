plugins {
    kotlin("jvm") version "2.2.20-RC"
    id("maven-publish")
    id("org.jetbrains.dokka")
    signing
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

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaGeneratePublicationHtml)
}


publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(javadocJar)
            
            pom {
                name.set("KMapper Compiler Plugin")
                description.set("Kotlin compiler plugin for KMapper")
                url.set("https://github.com/flock-community/kmapper")
                
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
                
                developers {
                    developer {
                        id.set("flock-community")
                        name.set("Flock Community")
                        email.set("info@flock.community")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/flock-community/kmapper.git")
                    developerConnection.set("scm:git:ssh://github.com:flock-community/kmapper.git")
                    url.set("https://github.com/flock-community/kmapper")
                }
            }
        }
    }
}

signing {
    val signingKey = System.getenv("GPG_PRIVATE_KEY")
    val signingPassword = System.getenv("GPG_PASSPHRASE")
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["maven"])
    }
}
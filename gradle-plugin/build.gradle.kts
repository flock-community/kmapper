plugins {
    kotlin("jvm")
    id("com.github.gmazzo.buildconfig") version "5.6.5"
    id("java-gradle-plugin")
    id("maven-publish")
    id("org.jetbrains.dokka")
    signing
}

version = rootProject.version

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
    implementation(kotlin("gradle-plugin-api"))

    testImplementation(kotlin("test-junit5"))
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaGeneratePublicationHtml)
}

buildConfig {
    packageName(project.group.toString())

    buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${rootProject.group}\"")

    val pluginProject = project(":compiler-plugin")
    buildConfigField("String", "KOTLIN_PLUGIN_GROUP", "\"${pluginProject.group}\"")
    buildConfigField("String", "KOTLIN_PLUGIN_NAME", "\"${pluginProject.name}\"")
    buildConfigField("String", "KOTLIN_PLUGIN_VERSION", "\"${pluginProject.version}\"")

    val annotationsProject = project(":compiler-annotations")
    buildConfigField(
        type = "String",
        name = "ANNOTATIONS_LIBRARY_COORDINATES",
        expression = "\"${annotationsProject.group}:${annotationsProject.name}:${annotationsProject.version}\""
    )
}

gradlePlugin {
    plugins {
        create("FlockPlugin") {
            id = rootProject.group.toString()
            displayName = "FlockPlugin"
            description = "FlockPlugin"
            implementationClass = "community.flock.kmapper.gradle.plugin.FlockGradlePlugin"
        }
    }
}

afterEvaluate {
    publishing {
        publications.withType<MavenPublication> {
            artifact(javadocJar)
            
            pom {
                name.set("KMapper Gradle Plugin")
                description.set("Gradle plugin for KMapper")
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
        sign(publishing.publications)
    }
}

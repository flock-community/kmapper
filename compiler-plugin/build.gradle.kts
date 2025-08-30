import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.20-RC"
    id("maven-publish")
    id("org.jetbrains.dokka")
    id("com.gradleup.kctf").version("2.2.20-RC-0.0.1-SNAPSHOT-1424b19d1cb13443bc979332d1104333061ca5fd")
    signing
}

group = rootProject.group
version = rootProject.version

dependencies {
    compileOnly(kotlin("compiler-embeddable"))
    testImplementation("com.gradleup.kctf:kctf-runtime:2.2.20-RC-0.0.1-SNAPSHOT-1424b19d1cb13443bc979332d1104333061ca5fd")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation(project(":compiler-runtime"))
}


kotlin {
    jvmToolchain(17)
    compilerOptions.freeCompilerArgs.add("-Xcontext-parameters")
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

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs.add("-opt-in=org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
}

val kMapperRuntimeClasspath: Configuration by configurations.creating { isTransitive = false }

tasks.withType<Test> {
    dependsOn(kMapperRuntimeClasspath)
    inputs
        .dir(layout.projectDirectory.dir("src/test/data"))
        .withPropertyName("testData")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    workingDir = rootDir
    useJUnitPlatform()
    systemProperty("runtime.classpath", kMapperRuntimeClasspath.asPath)
}

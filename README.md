# KMapper

A Kotlin compiler plugin that automatically generates mapping methods for annotated classes.

## Overview

KMapper is a Kotlin compiler plugin that provides code generation capabilities through annotations. When you annotate a class with `@Flock`, the plugin automatically generates a `flock()` method that returns a formatted string representation of the class.

## Features

- **Kotlin 2.0+ Support**: Built with K2 compiler support (Kotlin 2.2.20-RC)
- **Annotation-Driven**: Simple `@Flock` annotation to trigger code generation
- **Zero Runtime Dependencies**: Pure compile-time code generation
- **IR-Based Generation**: Uses Kotlin's IR (Intermediate Representation) for robust code generation

## Requirements

- Kotlin 2.2.20-RC or later
- JVM 17+
- Gradle build system

## Installation

### Using Gradle

Add the plugin to your project's `build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.2.20-RC"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xplugin=path/to/kmapper-plugin.jar")
    }
}
```

## Usage

### Basic Example

1. Define the `@Flock` annotation in your project:

```kotlin
package community.flock.kmapper

@Target(AnnotationTarget.CLASS)
annotation class Flock
```

2. Annotate your classes:

```kotlin
import community.flock.kmapper.Flock

@Flock
class User {
    override fun toString(): String = "User"
}
```

3. Use the generated `flock()` method:

```kotlin
fun main() {
    val user = User()
    println(user.flock()) // Output: "FLOCK User"
}
```

### Generated Code

For a class annotated with `@Flock`, the plugin automatically generates:

```kotlin
fun flock(): String {
    return "FLOCK ${ClassName}"
}
```

## Project Structure

```
kmapper/
├── plugin/                           # Compiler plugin implementation
│   └── src/main/kotlin/community/flock/kmapper/compiler/
│       ├── KMapperCompilerPluginRegistrar.kt    # Main plugin registrar
│       ├── FlockExtension.kt                    # IR generation extension
│       ├── FlockIrVisitor.kt                    # IR visitor for transformations
│       ├── FlockFirExtensionRegistrar.kt        # FIR extension registrar
│       ├── FlockFirDeclarationGenerationExtension.kt # FIR declaration generation
│       └── keys.kt                              # Plugin configuration keys
├── tests/                            # Test suite
│   └── src/test/kotlin/community/flock/kmapper/
│       ├── CompilerPluginFunctionalTest.kt     # Functional tests
│       └── CompilerPluginRegistarTest.kt       # Unit tests
└── README.md                         # This file
```

## Development

### Building the Plugin

```bash
./gradlew build
```

### Running Tests

```bash
./gradlew test
```

The test suite includes:
- **Functional Tests**: End-to-end compilation and execution tests
- **Unit Tests**: Plugin registration and configuration tests

### Architecture

KMapper uses Kotlin's compiler plugin architecture with two main components:

1. **FIR Extensions**: Handle frontend processing and declaration generation
2. **IR Extensions**: Perform the actual code generation in the backend

The plugin registers both extensions to support the K2 compiler frontend while maintaining compatibility.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Run the test suite
6. Submit a pull request

## Technical Details

- **Group**: `community.flock`
- **Version**: `1.0-SNAPSHOT`
- **Kotlin Version**: `2.2.20-RC`
- **JVM Toolchain**: 21
- **Plugin API**: Uses Kotlin's `@OptIn(ExperimentalCompilerApi::class)`

## License

This project is part of the community.flock ecosystem.

## Support

For issues and questions, please use the GitHub issue tracker.
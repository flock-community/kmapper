# KMapper

A Kotlin compiler plugin that automatically generates mapping methods between data classes using a fluent DSL.

## Overview

KMapper is a Kotlin compiler plugin that provides code generation capabilities for mapping between data classes. It uses a fluent DSL syntax with the `mapper` extension function to transform objects from one type to another, with compile-time validation to ensure all required constructor parameters are mapped.

![KMapper Demo](static/demo.gif)

## Features

- **Kotlin 2.0+ Support**: Built with K2 compiler support (Kotlin 2.2.20-RC)
- **Fluent DSL**: Intuitive mapping syntax with `to::property map value`
- **Compile-time Validation**: Ensures all required constructor parameters are mapped
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
    id("community.flock.kmapper") version "0.0.0-SNAPSHOT"
}

repositories {
    mavenCentral()
    maven(url="https://oss.sonatype.org/content/repositories/snapshots/")
}
```

## Usage

### Basic Example

1. Import the mapper function:

```kotlin
import community.flock.kmapper.mapper
```

2. Define your data classes:

```kotlin
data class User(val firstName: String, val lastName: String)
data class UserDto(val name: String, val age: Int)
```

3. Use the mapper DSL to transform objects:

```kotlin
fun main() {
    val user = User("John", "Doe")
    val userDto = user.mapper<UserDto> {
        to::age map 25
        to::name map "${user.firstName} ${user.lastName}"
    }
    println(userDto) // Output: UserDto(name=John Doe, age=25)
}
```

### Generated Code

The plugin automatically generates the mapping implementation at compile time, replacing the `mapper` function call with the actual object construction code.

# Assignment-Based DSL Syntax Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Change KMapper DSL from `to::age map value` to `age = value` while preserving all existing functionality.

**Architecture:** Make the target type (TO) the lambda receiver so properties resolve naturally. Use `FirAssignExpressionAltererExtension` to transform val-assignments into `__mapField()` marker calls. `FirCallChecker` validates completeness. IR extracts markers to build constructors.

**Tech Stack:** Kotlin K2 Compiler Plugin APIs (FIR + IR), Kotlin 2.3.10

**Design doc:** `docs/plans/2026-03-05-assignment-based-dsl-design.md`

---

### Task 1: Update Runtime

**Files:**
- Modify: `compiler-runtime/src/main/kotlin/community/flock/kmapper/Runtime.kt`

**Step 1: Read the current runtime file**

Read `compiler-runtime/src/main/kotlin/community/flock/kmapper/Runtime.kt` to confirm current state.

**Step 2: Replace the runtime**

Write the new runtime:

```kotlin
package community.flock.kmapper

@Target(AnnotationTarget.FUNCTION)
annotation class KMapper

@Target(AnnotationTarget.FUNCTION)
annotation class KMapperInternal

fun generated(): Nothing = error("Mapper was not generated")

@KMapper
fun <TO, FROM> FROM.mapper(block: (TO.(it: FROM) -> Unit)? = null): TO = generated()

fun <T> T.ignore() {}

@KMapperInternal
fun __mapField(name: String, value: Any?) {}
```

Key changes:
- Remove `Mapper` class entirely
- Remove `KProperty0` import
- Change lambda receiver from `Mapper<TO, FROM>` to `TO`
- Add top-level `ignore()` extension on `T`
- Add `__mapField()` as assignment-alteration target
- Add `@KMapperInternal` annotation

**Step 3: Verify compilation**

Run: `./gradlew :compiler-runtime:build`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add compiler-runtime/
git commit -m "refactor: change mapper lambda receiver to TO type, remove Mapper class"
```

---

### Task 2: Create FirAssignExpressionAltererExtension

This is the most experimental task. The alterer intercepts val-property assignments inside mapper lambdas and transforms them into `__mapField("name", value)` function calls.

**Files:**
- Create: `compiler-plugin/src/main/kotlin/community/flock/kmapper/compiler/fir/KMapperAssignAlterer.kt`
- Modify: `compiler-plugin/src/main/kotlin/community/flock/kmapper/compiler/KMapperFirExtensionRegistrar.kt`

**Step 1: Create the assign alterer**

Create `compiler-plugin/src/main/kotlin/community/flock/kmapper/compiler/fir/KMapperAssignAlterer.kt`:

```kotlin
package community.flock.kmapper.compiler.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.extensions.FirAssignExpressionAltererExtension
import org.jetbrains.kotlin.fir.references.toResolvedPropertySymbol

class KMapperAssignAlterer(session: FirSession) : FirAssignExpressionAltererExtension(session) {

    override fun transformVariableAssignment(
        variableAssignment: FirVariableAssignment
    ): FirStatement? {
        // 1. Check if this is an assignment to a val property
        val propertySymbol = variableAssignment.calleeReference
            .toResolvedPropertySymbol() ?: return null

        if (propertySymbol.isVar) return null

        // 2. Check if the property belongs to a data class
        val ownerClassId = propertySymbol.callableId.classId ?: return null
        val ownerClass = session.symbolProvider
            .getClassLikeSymbolByClassId(ownerClassId) ?: return null

        // 3. Build a FirFunctionCall to __mapField("propertyName", rValue)
        //    The exact FIR builder API:
        //    - Resolve __mapField from community.flock.kmapper package
        //    - Pass property name as FirLiteralExpression (String constant)
        //    - Pass variableAssignment.rValue as the second argument
        //
        //    Reference: Kotlin assign-plugin source at
        //    plugins/assign-plugin/assign-plugin-k2/src/org/jetbrains/kotlin/assignment/plugin/k2/
        //    for examples of building FirFunctionCall in an alterer.
        //
        //    NOTE: This is the most experimental part. The exact builder API
        //    needs to be verified against the Kotlin 2.3.10 compiler source.
        //    Start by examining FirAssignExpressionAltererExtension implementations
        //    in the Kotlin repo for the correct builder pattern.

        val propertyName = propertySymbol.name.asString()
        val rValue = variableAssignment.rValue

        // Build: __mapField("propertyName", rValue)
        // Implementation approach:
        // - Use buildFunctionCall { } from FIR builders
        // - Look up __mapField symbol via session.symbolProvider
        //   using CallableId(FqName("community.flock.kmapper"), Name.identifier("__mapField"))
        // - Set arguments: [FirLiteralExpression(propertyName), rValue]
        return buildMapFieldCall(propertyName, rValue)
    }

    private fun buildMapFieldCall(propertyName: String, rValue: FirExpression): FirFunctionCall {
        // TODO: Implement using FIR builder API
        // Look at Kotlin assign-plugin for reference implementation
        TODO("Implement FIR function call builder")
    }
}
```

**Important research note:** Study the Kotlin assign-plugin source at `plugins/assign-plugin/assign-plugin-k2/` in the Kotlin compiler repo. It demonstrates the exact pattern for building `FirFunctionCall` from a `FirAssignExpressionAltererExtension`. Key file: `FirBackingFieldAssignmentTransformer.kt` or similar.

**Step 2: Register the alterer in FirExtensionRegistrar**

Modify `compiler-plugin/src/main/kotlin/community/flock/kmapper/compiler/KMapperFirExtensionRegistrar.kt`:

```kotlin
package community.flock.kmapper.compiler

import KMapperFirMappingChecker
import community.flock.kmapper.compiler.fir.KMapperAssignAlterer
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class KMapperFirExtensionRegistrar(val collector: MessageCollector) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::KMapperAssignAlterer
        +{ session: FirSession -> KMapperFirMappingChecker.Extension(collector, session) }
    }
}
```

**Step 3: Verify compilation**

Run: `./gradlew :compiler-plugin:build`
Expected: BUILD SUCCESSFUL (the alterer itself compiles, even if buildMapFieldCall is a TODO)

**Step 4: Commit**

```bash
git add compiler-plugin/src/main/kotlin/community/flock/kmapper/compiler/fir/KMapperAssignAlterer.kt
git add compiler-plugin/src/main/kotlin/community/flock/kmapper/compiler/KMapperFirExtensionRegistrar.kt
git commit -m "feat: add FirAssignExpressionAltererExtension for mapper val-assignments"
```

**Step 5: Implement buildMapFieldCall**

After studying the Kotlin assign-plugin source, implement the `buildMapFieldCall` method. The implementation must:
1. Look up `__mapField` function symbol via `session.symbolProvider.getTopLevelCallableSymbols()`
2. Build a `FirFunctionCall` using FIR builder DSL
3. Set the two arguments (string constant + rValue expression)
4. Set proper source information from the original assignment

**Step 6: Write a minimal integration test to verify alteration works**

Add a test in `KMapperTest.kt` (temporarily, to validate the alterer):

```kotlin
@Test
fun shouldCompile_newSyntax_basic() {
    IntegrationTest(options)
        .file("App.kt") {
            $$"""
            |package sample
            |
            |import community.flock.kmapper.mapper
            |
            |data class User(val firstName: String, val lastName: String, val age: Int)
            |data class UserDto(val name: String, val age: Int)
            |
            |fun main() {
            |  val user = User("John", "Doe", 99)
            |  val userDto: UserDto = user.mapper {
            |    name = "${'$'}{it.firstName} ${'$'}{it.lastName}"
            |  }
            |  println(userDto)
            |}
            |
            """.trimMargin()
        }
        .compileSuccess { output ->
            assertTrue(
                output.contains("UserDto(name=John Doe, age=99)"),
                "Expected UserDto(name=John Doe, age=99) in output"
            )
        }
}
```

**Step 7: Run the test**

Run: `./gradlew :test-integration:test --tests "*shouldCompile_newSyntax_basic"`
Expected: PASS (this validates the full pipeline: FIR alteration + IR extraction)

**Step 8: Commit**

```bash
git add compiler-plugin/ test-integration/
git commit -m "feat: implement FIR assign alterer with __mapField transformation"
```

---

### Task 3: Update FIR Checker for New Syntax

**Files:**
- Modify: `compiler-plugin/src/main/kotlin/community/flock/kmapper/compiler/fir/KMapperFirMappingChecker.kt` (currently at default package — note the import in KMapperFirExtensionRegistrar uses `import KMapperFirMappingChecker`)

**Step 1: Read the current checker**

Read `KMapperFirMappingChecker.kt` to confirm current mapping extraction logic.

**Step 2: Update mapping extraction**

Change the mapping extraction from `map`/`ignore` FirFunctionCalls to `__mapField`/`ignore` calls:

```kotlin
// Replace the mapping extraction block (around lines 65-86) with:
val mapping = function.arguments.firstOrNull().let { it as? FirAnonymousFunctionExpression }
    ?.let { arg ->
        arg.anonymousFunction.body
            ?.statements?.filterIsInstance<FirFunctionCall>()
            ?.mapNotNull { call ->
                val functionName = call.calleeReference.toResolvedBaseSymbol()?.name?.asString()
                when (functionName) {
                    "__mapField" -> {
                        // Extract field name from first string argument
                        val fieldNameStr = (call.arguments.getOrNull(0) as? FirLiteralExpression)
                            ?.value as? String ?: return@mapNotNull null
                        val valueExpr = call.arguments.getOrNull(1) ?: return@mapNotNull null
                        Field(
                            name = Name.identifier(fieldNameStr),
                            type = valueExpr.resolvedType,
                            hasDefaultValue = false,
                            fields = valueExpr.resolvedType.resolveConstructorFields()
                        )
                    }
                    "ignore" -> {
                        // Extract property name from the extension receiver (a getter call)
                        val receiver = call.extensionReceiver
                        val propSymbol = receiver?.toReference(session)
                            ?.toResolvedPropertySymbol()
                        propSymbol?.let { symbol ->
                            Field(
                                name = symbol.name,
                                type = symbol.resolvedReturnType,
                                hasDefaultValue = false,
                                fields = emptyList()
                            )
                        }
                    }
                    else -> null
                }
            }
    }
    ?: emptyList()
```

**Step 3: Update the type argument extraction**

The type arguments on the mapper call stay the same (TO is index 0, FROM is index 1). No changes needed here.

**Step 4: Verify compilation**

Run: `./gradlew :compiler-plugin:build`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add compiler-plugin/
git commit -m "refactor: update FIR checker to validate __mapField/ignore calls"
```

---

### Task 4: Update IR Visitor

**Files:**
- Modify: `compiler-plugin/src/main/kotlin/community/flock/kmapper/compiler/ir/KMapperIrBuildMapperVisitor.kt`

**Step 1: Read the current IR visitor**

Read `KMapperIrBuildMapperVisitor.kt` to confirm current mapping extraction (lines 63-74).

**Step 2: Update mapping extraction in createMapperImplementationFromCall**

Replace the mapping extraction block:

```kotlin
// OLD (lines 63-74): extract from map/ignore IrCalls
val mapping = callArgument?.function
    ?.body.let { it as? IrBlockBody }
    ?.statements?.filterIsInstance<IrCall>().orEmpty()
    .associate { call ->
        val callName = call.symbol.owner.name
        val field = call.arguments.getOrNull(1) as? IrPropertyReference ?: error("...")
        val fieldName = field.symbol.owner.name
        when (callName.identifier) {
            "map" -> fieldName to call.arguments.getOrNull(2)
            "ignore" -> fieldName to null
            else -> error("Unknown mapping type: $callName")
        }
    }

// NEW: extract from __mapField/ignore IrCalls
val mapping = callArgument?.function
    ?.body.let { it as? IrBlockBody }
    ?.statements?.filterIsInstance<IrCall>().orEmpty()
    .associate { call ->
        val functionName = call.symbol.owner.name.identifier
        when (functionName) {
            "__mapField" -> {
                val nameConst = call.arguments[0] as? IrConst<*>
                    ?: error("__mapField first arg must be a string constant")
                val fieldName = Name.identifier(nameConst.value as String)
                fieldName to call.arguments[1]
            }
            "ignore" -> {
                // Extension receiver is a property getter call on the TO receiver
                val getterCall = call.extensionReceiver as? IrCall
                    ?: error("ignore() receiver must be a property access")
                val fieldName = getterCall.symbol.owner.correspondingPropertySymbol?.owner?.name
                    ?: error("Cannot extract property name from ignore() receiver")
                fieldName to null
            }
            else -> error("Unknown mapping type in mapper lambda: $functionName")
        }
    }
```

**Step 3: Update the `it` remapper**

The remapper currently replaces references to the lambda's `it` parameter. With the new signature `TO.(FROM) -> Unit`, the lambda has:
- `this` (dispatch receiver) = TO type (not used in value expressions)
- Parameter 0 = FROM (the `it`)

Update the remapper to find the correct parameter:

```kotlin
val remapper = object : IrElementTransformerVoid() {
    override fun visitGetValue(expression: IrGetValue): IrExpression {
        val transformedGetValue = super.visitGetValue(expression)
        // The 'it' parameter is the first Regular parameter of the lambda
        val itParamSymbol = callArgument?.function?.parameters
            ?.firstOrNull { it.kind == IrParameterKind.Regular }
            ?.symbol
        if (expression.symbol == itParamSymbol) {
            return receiverArgument.deepCopyWithSymbols()
        }
        return transformedGetValue
    }
}
```

This should be similar to the current code (lines 79-90) — verify the parameter kind matching still works with the new lambda signature.

**Step 4: Verify compilation**

Run: `./gradlew :compiler-plugin:build`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add compiler-plugin/
git commit -m "refactor: update IR visitor to extract __mapField/ignore calls"
```

---

### Task 5: Update Integration Tests

**Files:**
- Modify: `test-integration/src/test/kotlin/community/flock/kmapper/KMapperTest.kt`

**Step 1: Update tests with lambda syntax**

The following tests use mapper lambdas and need syntax updates:

| Test | Change |
|------|--------|
| `shouldCompile_happyFlow` | `to::name map "..."` → `name = "..."` |
| `shouldCompile_nestedEqualClasses` | `to::name map "..."` → `name = "..."` |
| `shouldCompile_deepNestedEqualClasses` | `to::name map "..."` → `name = "..."` |
| `shouldFail_missingParameterAge` | `to::name map "..."` → `name = "..."` |
| `shouldSuccess_nullableFieldsIgnoreNullableField` | `to::lastName map null` → `lastName = null` |
| `shouldSuccess_overwriteValue` | `to::firstName map "HELLO"` → `firstName = "HELLO"` |
| `shouldSuccess_ignoreValue` | `to::firstName.ignore()` → `firstName.ignore()` |

Also update imports in test source strings. The `import community.flock.kmapper.mapper` stays. Add `import community.flock.kmapper.ignore` where `ignore()` is used.

Tests that use `mapper()` with NO lambda stay unchanged:
- `shouldCompile_identicalClasses`
- `shouldCompile_valueClass`
- `shouldCompile_unknownInFrom`
- `shouldCompile_complexList`
- `shouldCompile_getField`
- `shouldCompile_defaultField`
- `shouldCompile_equalEnums`
- `shouldFail_notEqualEnums`
- `shouldSuccess_nestedEnumMapping`
- `shouldFail_valueClassap`
- `shouldFail_valueClassDeepMap`
- `shouldSuccess_nullableFieldsFromNullable`
- `shouldSuccess_nullableFieldsToNullable`
- `shouldSuccess_defaultValues`
- `shouldSuccess_stringLists`
- `shouldFail_differentLists`

**Step 2: Example updated test**

```kotlin
@Test
fun shouldCompile_happyFlow() {
    IntegrationTest(options)
        .file("App.kt") {
            $$"""
            |package sample
            |
            |import community.flock.kmapper.mapper
            |
            |data class User(val firstName: String, val lastName: String, val age: Int)
            |data class Id(val id: Int)
            |data class UserDto(val name: String, val age: Int)
            |
            |fun main() {
            |  val user = User("John", "Doe", 99)
            |  val userDto:UserDto = user.mapper {
            |    name = "${'$'}{it.firstName} ${'$'}{user.lastName}"
            |  }
            |  println(userDto)
            |}
            |
            """.trimMargin()
        }
        .compileSuccess { output ->
            assertTrue(
                output.contains("UserDto(name=John Doe, age=99)"),
                "Expected UserDto(name=John Doe, age=99) in output"
            )
        }
}
```

**Step 3: Run all tests**

Run: `./gradlew :test-integration:test`
Expected: ALL PASS

**Step 4: Commit**

```bash
git add test-integration/
git commit -m "test: update integration tests to assignment-based DSL syntax"
```

---

### Task 6: Update README

**Files:**
- Modify: `README.md`

**Step 1: Update all code examples**

Replace old syntax with new syntax throughout the README:
- Basic example: `to::age map ...` → `age = ...`
- Ignore example: `to::active.ignore()` → `active.ignore()`
- Import section: add `import community.flock.kmapper.ignore` where needed

**Step 2: Commit**

```bash
git add README.md
git commit -m "docs: update README examples to assignment-based DSL syntax"
```

---

## Risk Notes

### FirAssignExpressionAltererExtension (Task 2)

This is the highest-risk task. Key uncertainties:

1. **Does `FirAssignExpressionAltererExtension` exist in Kotlin 2.3.10?** The Kotlin assign-plugin uses this API, but it may be internal. Verify by checking the compiler-embeddable classpath.

2. **Building FirFunctionCall:** The exact FIR builder API for constructing function calls programmatically needs to be learned from the Kotlin assign-plugin source. Reference: `kotlin/plugins/assign-plugin/assign-plugin-k2/`.

3. **Context detection:** The alterer receives only the `FirVariableAssignment`, not the enclosing context. Current approach: transform ALL val-assignments to data class properties. This is broader than needed but safe since `__mapField` is only meaningful inside mapper lambdas (and would cause a compilation error elsewhere). If this is too broad, refine by checking the assignment's source position against known mapper lambda ranges.

4. **Fallback:** If `FirAssignExpressionAltererExtension` is not available in the public API, investigate:
   - Using `FirStatusTransformerExtension` to make target properties temporarily mutable
   - Using the Kotlin assign-plugin as a dependency and annotating target properties
   - Generating per-target-type builder classes via `FirDeclarationGenerationExtension`

### Lambda Receiver Change (Tasks 1, 4)

Changing from `Mapper<TO, FROM>` to `TO` as the lambda receiver affects how the IR visitor finds parameters. Verify that:
- The `it` parameter is correctly identified in the lambda's IR representation
- The remapper correctly replaces `it` references with the actual receiver
- The TO receiver (`this`) references in `ignore()` calls are handled (only property name extraction needed, not actual execution)

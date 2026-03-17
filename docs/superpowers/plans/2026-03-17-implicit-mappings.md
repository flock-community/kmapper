# Implicit Type Mappings Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add implicit numeric widening and value class wrapping/unwrapping to kmapper's auto-mapping.

**Architecture:** Extend the FIR validation phase (`deepEqual` chain) with a widening table and value class detection, then extend IR code generation to emit conversion calls (`toXxx()`), property access (unwrap), and constructor calls (wrap).

**Tech Stack:** Kotlin compiler plugin (FIR + IR), Gradle integration tests

**Spec:** `docs/superpowers/specs/2026-03-17-implicit-mappings-design.md`

**Important notes:**
- Use `classOrNull?.owner` throughout the IR phase (not `getClass()`) to match existing codebase patterns.
- `Field.equals` is overridden — must also override `hashCode` (hash on `name` only) to maintain the contract.
- The `primaryEqual` nullable guard must be removed since `nullableEqual` in the `deepEqual` chain already handles nullable compatibility. Widening comparisons should use non-null projected types.
- Tasks 2 and 4 both modify the `construct` method's `when` block. Task 4 must build on Task 2's changes. The final combined `when` block is shown in Task 4.
- In `Field.equals`, `this` is the source (from `readableProperties()`) and `other` is the target (from constructor params). Widening direction: `this.type` widens to `other.type`.

---

### Task 1: Add numeric widening to FIR validation

**Files:**
- Modify: `compiler-plugin/src/main/kotlin/community/flock/kmapper/compiler/fir/KmapperFirLogic.kt`
- Test: `test-integration/src/test/kotlin/community/flock/kmapper/KMapperTest.kt`

- [ ] **Step 1: Write failing test for Int → Long widening**

Add to `KMapperTest.kt`:

```kotlin
@Test
fun shouldCompile_intToLongWidening() {
    IntegrationTest(options)
        .file("App.kt") {
            $$"""
            |package sample
            |
            |import community.flock.kmapper.mapper
            |
            |data class Source(val id: Int, val name: String)
            |data class Target(val id: Long, val name: String)
            |
            |fun main() {
            |  val source = Source(id=42, name="test")
            |  val target:Target = source.mapper()
            |  println(target)
            |}
            |
            """.trimMargin()
        }
        .compileSuccess { output ->
            assertTrue(
                output.contains("Target(id=42, name=test)"),
                "Expected Target(id=42, name=test) in output"
            )
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :test-integration:test --tests "community.flock.kmapper.KMapperTest.shouldCompile_intToLongWidening" --info`
Expected: FAIL with "Missing mapping for: id"

- [ ] **Step 3: Add widening table and modify `primaryEqual` in FIR logic**

In `KmapperFirLogic.kt`, add the widening table and update `primaryEqual`. Remove the duplicated nullable check (line 43). Remove the nullable equality guard entirely — `nullableEqual` in the `deepEqual` chain already handles nullable compatibility before `primaryEqual` is called, so `primaryEqual` should compare non-null projected types:

```kotlin
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.StandardClassIds

private val WIDENING_TABLE: Map<ClassId, Set<ClassId>> = mapOf(
    StandardClassIds.Byte to setOf(StandardClassIds.Short, StandardClassIds.Int, StandardClassIds.Long, StandardClassIds.Float, StandardClassIds.Double),
    StandardClassIds.Short to setOf(StandardClassIds.Int, StandardClassIds.Long, StandardClassIds.Float, StandardClassIds.Double),
    StandardClassIds.Int to setOf(StandardClassIds.Long, StandardClassIds.Float, StandardClassIds.Double),
    StandardClassIds.Long to setOf(StandardClassIds.Float, StandardClassIds.Double),
    StandardClassIds.Float to setOf(StandardClassIds.Double),
)

private fun primaryEqual(to: Field, from: Field): Boolean {
    if (!to.type.isPrimitive || !from.type.isPrimitive) return false
    if (to.type == from.type) return true
    // Check widening: from type can widen to target type (compare without nullable markers)
    val fromClassId = (from.type as? ConeClassLikeType)?.lookupTag?.classId ?: return false
    val toClassId = (to.type as? ConeClassLikeType)?.lookupTag?.classId ?: return false
    return WIDENING_TABLE[fromClassId]?.contains(toClassId) == true
}
```

- [ ] **Step 4: Run test — expect it still fails (IR phase doesn't support widening yet)**

Run: `./gradlew :test-integration:test --tests "community.flock.kmapper.KMapperTest.shouldCompile_intToLongWidening" --info`
Expected: Still FAIL — FIR validation now passes (no "Missing mapping" error) but IR field matching rejects the type mismatch. This is expected; Task 2 completes the implementation.

- [ ] **Step 5: Commit FIR widening changes**

```bash
git add compiler-plugin/src/main/kotlin/community/flock/kmapper/compiler/fir/KmapperFirLogic.kt test-integration/src/test/kotlin/community/flock/kmapper/KMapperTest.kt
git commit -m "feat: add numeric widening table to FIR validation"
```

---

### Task 2: Add numeric widening to IR code generation

**Files:**
- Modify: `compiler-plugin/src/main/kotlin/community/flock/kmapper/compiler/ir/KMapperIrLogic.kt`
- Modify: `compiler-plugin/src/main/kotlin/community/flock/kmapper/compiler/ir/KMapperIrBuildMapperVisitor.kt`
- Test: `test-integration/src/test/kotlin/community/flock/kmapper/KMapperTest.kt`

- [ ] **Step 1: Add widening table and update `Field` in IR logic**

In `KMapperIrLogic.kt`, add a widening table, update `Field.equals`, and add `hashCode` override:

```kotlin
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.StandardClassIds

val IR_WIDENING_TABLE: Map<ClassId, Set<ClassId>> = mapOf(
    StandardClassIds.Byte to setOf(StandardClassIds.Short, StandardClassIds.Int, StandardClassIds.Long, StandardClassIds.Float, StandardClassIds.Double),
    StandardClassIds.Short to setOf(StandardClassIds.Int, StandardClassIds.Long, StandardClassIds.Float, StandardClassIds.Double),
    StandardClassIds.Int to setOf(StandardClassIds.Long, StandardClassIds.Float, StandardClassIds.Double),
    StandardClassIds.Long to setOf(StandardClassIds.Float, StandardClassIds.Double),
    StandardClassIds.Float to setOf(StandardClassIds.Double),
)

fun isWideningAllowed(fromType: IrType, toType: IrType): Boolean {
    val fromClassId = fromType.makeNotNull().classOrNull?.owner?.classId ?: return false
    val toClassId = toType.makeNotNull().classOrNull?.owner?.classId ?: return false
    return IR_WIDENING_TABLE[fromClassId]?.contains(toClassId) == true
}

// Note: `this` is the source field (from readableProperties), `other` is the target field (constructor param).
// Widening direction: this.type -> other.type. This asymmetry is intentional.
data class Field(val name: Name, val type: IrType) {
    override fun equals(other: Any?): Boolean {
        if (other !is Field) return false
        if (name != other.name) return false
        if (type.makeNotNull() == other.type.makeNotNull()) return true
        return isWideningAllowed(fromType = type, toType = other.type)
    }

    override fun hashCode(): Int = name.hashCode()
}
```

- [ ] **Step 2: Update IR visitor to generate `toXxx()` conversion calls**

In `KMapperIrBuildMapperVisitor.kt`:

**a)** Modify `createMapperImplementationFromCall` at line ~133 to detect widening and generate conversion calls. Replace the existing block:

```kotlin
fromTypeArgument.readableProperties()
    .find { it == field }
    ?.let { sourceField ->
        val property = builder.irGetPropertyByName(
            receiver = receiverArgument,
            propertyName = field.name
        )
        // Widening: source type differs from target, needs toXxx() conversion
        if (property != null && sourceField.type.makeNotNull() != field.type.makeNotNull()
            && isWideningAllowed(sourceField.type, field.type)) {
            builder.irWideningCall(property, field.type)
        } else {
            property
        }
    }
    ?: toShape.constructor.defaultValue(index)
```

**b)** Add private helper method `irWideningCall` in the class body (near `construct` and `irGetPropertyByName`):

```kotlin
private fun IrBuilder.irWideningCall(receiver: IrExpression, targetType: IrType): IrExpression {
    val targetClassId = targetType.makeNotNull().classOrNull?.owner?.classId
        ?: error("Cannot resolve target class for widening")
    val conversionName = "to${targetClassId.shortClassName.asString()}"
    val sourceClass = receiver.type.makeNotNull().classOrNull?.owner
        ?: error("Cannot resolve source class for widening")
    val conversionFunction = sourceClass.declarations
        .filterIsInstance<IrSimpleFunction>()
        .firstOrNull { it.name.asString() == conversionName }
        ?: error("No $conversionName function found on ${sourceClass.name}")
    return irCall(conversionFunction.symbol).apply {
        dispatchReceiver = receiver
    }
}
```

**c)** Update `construct` method's `when` block (line ~244) to handle widening in nested data classes:

```kotlin
when {
    type.run { isPrimitiveType() || isString() } -> {
        if (property.type.makeNotNull() != type.makeNotNull()
            && isWideningAllowed(property.type, type)) {
            irWideningCall(property, type)
        } else {
            property
        }
    }
    else -> construct(
        property,
        toShape = toShape.fields[index].type.convertShape(),
        fromShape = fromShape.fields[index].type.convertShape()
    )
}
```

- [ ] **Step 3: Run Int → Long test to verify it passes**

Run: `./gradlew :test-integration:test --tests "community.flock.kmapper.KMapperTest.shouldCompile_intToLongWidening" --info`
Expected: PASS with output "Target(id=42, name=test)"

- [ ] **Step 4: Add remaining widening tests**

Add to `KMapperTest.kt`:

```kotlin
@Test
fun shouldCompile_byteToLongWidening() {
    IntegrationTest(options)
        .file("App.kt") {
            $$"""
            |package sample
            |
            |import community.flock.kmapper.mapper
            |
            |data class Source(val value: Byte, val name: String)
            |data class Target(val value: Long, val name: String)
            |
            |fun main() {
            |  val source = Source(value=7, name="test")
            |  val target:Target = source.mapper()
            |  println(target)
            |}
            |
            """.trimMargin()
        }
        .compileSuccess { output ->
            assertTrue(
                output.contains("Target(value=7, name=test)"),
                "Expected Target(value=7, name=test) in output"
            )
        }
}

@Test
fun shouldCompile_shortToIntWidening() {
    IntegrationTest(options)
        .file("App.kt") {
            $$"""
            |package sample
            |
            |import community.flock.kmapper.mapper
            |
            |data class Source(val value: Short, val name: String)
            |data class Target(val value: Int, val name: String)
            |
            |fun main() {
            |  val source = Source(value=7, name="test")
            |  val target:Target = source.mapper()
            |  println(target)
            |}
            |
            """.trimMargin()
        }
        .compileSuccess { output ->
            assertTrue(
                output.contains("Target(value=7, name=test)"),
                "Expected Target(value=7, name=test) in output"
            )
        }
}

@Test
fun shouldCompile_intToDoubleWidening() {
    IntegrationTest(options)
        .file("App.kt") {
            $$"""
            |package sample
            |
            |import community.flock.kmapper.mapper
            |
            |data class Source(val value: Int, val name: String)
            |data class Target(val value: Double, val name: String)
            |
            |fun main() {
            |  val source = Source(value=42, name="test")
            |  val target:Target = source.mapper()
            |  println(target)
            |}
            |
            """.trimMargin()
        }
        .compileSuccess { output ->
            assertTrue(
                output.contains("Target(value=42.0, name=test)"),
                "Expected Target(value=42.0, name=test) in output"
            )
        }
}

@Test
fun shouldCompile_floatToDoubleWidening() {
    IntegrationTest(options)
        .file("App.kt") {
            $$"""
            |package sample
            |
            |import community.flock.kmapper.mapper
            |
            |data class Source(val value: Float, val name: String)
            |data class Target(val value: Double, val name: String)
            |
            |fun main() {
            |  val source = Source(value=3.14f, name="test")
            |  val target:Target = source.mapper()
            |  println(target)
            |}
            |
            """.trimMargin()
        }
        .compileSuccess { output ->
            assertTrue(
                output.contains("name=test"),
                "Expected name=test in output"
            )
        }
}

@Test
fun shouldCompile_intToNullableLongWidening() {
    IntegrationTest(options)
        .file("App.kt") {
            $$"""
            |package sample
            |
            |import community.flock.kmapper.mapper
            |
            |data class Source(val value: Int, val name: String)
            |data class Target(val value: Long?, val name: String)
            |
            |fun main() {
            |  val source = Source(value=42, name="test")
            |  val target:Target = source.mapper()
            |  println(target)
            |}
            |
            """.trimMargin()
        }
        .compileSuccess { output ->
            assertTrue(
                output.contains("Target(value=42, name=test)"),
                "Expected Target(value=42, name=test) in output"
            )
        }
}

@Test
fun shouldFail_longToIntNarrowing() {
    IntegrationTest(options)
        .file("App.kt") {
            $$"""
            |package sample
            |
            |import community.flock.kmapper.mapper
            |
            |data class Source(val value: Long, val name: String)
            |data class Target(val value: Int, val name: String)
            |
            |fun main() {
            |  val source = Source(value=42L, name="test")
            |  val target:Target = source.mapper()
            |  println(target)
            |}
            |
            """.trimMargin()
        }
        .compileFail { output ->
            assertTrue(
                output.contains("Missing mapping for: value"),
                "Expected Missing mapping for: value"
            )
        }
}

@Test
fun shouldFail_doubleToFloatNarrowing() {
    IntegrationTest(options)
        .file("App.kt") {
            $$"""
            |package sample
            |
            |import community.flock.kmapper.mapper
            |
            |data class Source(val value: Double, val name: String)
            |data class Target(val value: Float, val name: String)
            |
            |fun main() {
            |  val source = Source(value=3.14, name="test")
            |  val target:Target = source.mapper()
            |  println(target)
            |}
            |
            """.trimMargin()
        }
        .compileFail { output ->
            assertTrue(
                output.contains("Missing mapping for: value"),
                "Expected Missing mapping for: value"
            )
        }
}
```

- [ ] **Step 5: Run all widening tests**

Run: `./gradlew :test-integration:test --info`
Expected: All tests pass

- [ ] **Step 6: Commit IR widening and tests**

```bash
git add compiler-plugin/src/main/kotlin/community/flock/kmapper/compiler/ir/KMapperIrLogic.kt compiler-plugin/src/main/kotlin/community/flock/kmapper/compiler/ir/KMapperIrBuildMapperVisitor.kt test-integration/src/test/kotlin/community/flock/kmapper/KMapperTest.kt
git commit -m "feat: add numeric widening IR code generation and tests"
```

---

### Task 3: Add value class unwrap/wrap to FIR validation

**Files:**
- Modify: `compiler-plugin/src/main/kotlin/community/flock/kmapper/compiler/fir/KmapperFirLogic.kt`
- Test: `test-integration/src/test/kotlin/community/flock/kmapper/KMapperTest.kt`

- [ ] **Step 1: Write failing test for value class unwrapping**

Add to `KMapperTest.kt`:

```kotlin
@Test
fun shouldCompile_valueClassUnwrap() {
    IntegrationTest(options)
        .file("App.kt") {
            $$"""
            |package sample
            |
            |import community.flock.kmapper.mapper
            |
            |@JvmInline
            |value class Id(val id: Int)
            |data class Source(val id: Id, val name: String)
            |data class Target(val id: Int, val name: String)
            |
            |fun main() {
            |  val source = Source(id=Id(42), name="test")
            |  val target:Target = source.mapper()
            |  println(target)
            |}
            |
            """.trimMargin()
        }
        .compileSuccess { output ->
            assertTrue(
                output.contains("Target(id=42, name=test)"),
                "Expected Target(id=42, name=test) in output"
            )
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :test-integration:test --tests "community.flock.kmapper.KMapperTest.shouldCompile_valueClassUnwrap" --info`
Expected: FAIL with "Missing mapping for: id"

- [ ] **Step 3: Add `valueClassEqual` to FIR logic**

In `KmapperFirLogic.kt`, add `valueClassEqual` and update `deepEqual`:

```kotlin
import org.jetbrains.kotlin.fir.declarations.utils.isInline

context(session: FirSession, collector: MessageCollector)
infix fun Field.deepEqual(other: Field): Boolean =
    name == other.name &&
        nullableEqual(this, other) &&
        (primaryEqual(this, other) || valueClassEqual(this, other) || enumsEqual(this, other) || collectionsEqual(this, other) || fieldsEqual(this, other))

context(session: FirSession, collector: MessageCollector)
private fun valueClassEqual(to: Field, from: Field): Boolean {
    val toClass = to.type.toRegularClassSymbol(session)
    val fromClass = from.type.toRegularClassSymbol(session)
    val toIsInline = toClass?.isInline == true
    val fromIsInline = fromClass?.isInline == true

    if (!toIsInline && !fromIsInline) return false

    fun FirRegularClassSymbol.singleInnerType(): ConeKotlinType? {
        val constructor = constructors(session).firstOrNull() ?: return null
        val params = constructor.valueParameterSymbols
        if (params.size != 1) return null  // Excludes multi-field value classes
        return params.first().resolvedReturnType
    }

    return when {
        // Unwrap: source is value class, target is plain
        fromIsInline && !toIsInline -> {
            val innerType = fromClass!!.singleInnerType() ?: return false
            innerType == to.type
        }
        // Wrap: target is value class, source is plain
        toIsInline && !fromIsInline -> {
            val innerType = toClass!!.singleInnerType() ?: return false
            innerType == from.type
        }
        // Value-to-value: both are value classes with same inner type
        else -> {
            val toInner = toClass!!.singleInnerType() ?: return false
            val fromInner = fromClass!!.singleInnerType() ?: return false
            toInner == fromInner
        }
    }
}
```

- [ ] **Step 4: Run test — expect still fails (IR phase doesn't support value classes yet)**

Run: `./gradlew :test-integration:test --tests "community.flock.kmapper.KMapperTest.shouldCompile_valueClassUnwrap" --info`
Expected: Still FAIL — FIR validation passes but IR field matching rejects the type mismatch. Task 4 completes the implementation.

- [ ] **Step 5: Commit FIR value class changes**

```bash
git add compiler-plugin/src/main/kotlin/community/flock/kmapper/compiler/fir/KmapperFirLogic.kt test-integration/src/test/kotlin/community/flock/kmapper/KMapperTest.kt
git commit -m "feat: add value class wrap/unwrap to FIR validation"
```

---

### Task 4: Add value class unwrap/wrap to IR code generation

**Files:**
- Modify: `compiler-plugin/src/main/kotlin/community/flock/kmapper/compiler/ir/KMapperIrLogic.kt`
- Modify: `compiler-plugin/src/main/kotlin/community/flock/kmapper/compiler/ir/KMapperIrBuildMapperVisitor.kt`
- Test: `test-integration/src/test/kotlin/community/flock/kmapper/KMapperTest.kt`

- [ ] **Step 1: Add value class detection helpers to IR logic**

In `KMapperIrLogic.kt`, add helpers using `classOrNull?.owner` (matching existing codebase patterns):

```kotlin
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.util.constructors

fun IrType.isValueClass(): Boolean =
    classOrNull?.owner?.isValue == true

fun IrType.valueClassInnerType(): IrType? {
    val irClass = classOrNull?.owner ?: return null
    if (!irClass.isValue) return null
    val constructor = irClass.constructors.firstOrNull() ?: return null
    val params = constructor.parameters.filter { it.kind == IrParameterKind.Regular }
    if (params.size != 1) return null
    return params.first().type
}
```

Update `Field.equals` to also handle value class type pairs (builds on Task 2's version):

```kotlin
// Note: `this` is the source field (from readableProperties), `other` is the target field (constructor param).
data class Field(val name: Name, val type: IrType) {
    override fun equals(other: Any?): Boolean {
        if (other !is Field) return false
        if (name != other.name) return false
        val thisType = type.makeNotNull()
        val otherType = other.type.makeNotNull()
        if (thisType == otherType) return true
        if (isWideningAllowed(fromType = type, toType = other.type)) return true
        // Unwrap: source is value class, target is inner type
        if (type.isValueClass() && type.valueClassInnerType()?.makeNotNull() == otherType) return true
        // Wrap: target is value class, source is inner type
        if (other.type.isValueClass() && other.type.valueClassInnerType()?.makeNotNull() == thisType) return true
        // Value-to-value: both value classes with same inner type
        if (type.isValueClass() && other.type.isValueClass()) {
            val thisInner = type.valueClassInnerType()?.makeNotNull()
            val otherInner = other.type.valueClassInnerType()?.makeNotNull()
            if (thisInner != null && thisInner == otherInner) return true
        }
        return false
    }

    override fun hashCode(): Int = name.hashCode()
}
```

- [ ] **Step 2: Add value class IR generation to the visitor**

In `KMapperIrBuildMapperVisitor.kt`:

**a)** Update `createMapperImplementationFromCall` at line ~133 (replace the block from Task 2 with this combined version that handles both widening and value classes):

```kotlin
fromTypeArgument.readableProperties()
    .find { it == field }
    ?.let { sourceField ->
        val property = builder.irGetPropertyByName(
            receiver = receiverArgument,
            propertyName = field.name
        )
        when {
            // Widening: Int -> Long etc.
            property != null && isWideningAllowed(sourceField.type, field.type) ->
                builder.irWideningCall(property, field.type)
            // Unwrap: value class -> inner type
            property != null && sourceField.type.isValueClass() && !field.type.isValueClass() ->
                builder.irValueClassUnwrap(property, sourceField.type)
            // Wrap: inner type -> value class
            property != null && !sourceField.type.isValueClass() && field.type.isValueClass() ->
                builder.irValueClassWrap(property, field.type)
            // Value-to-value: different value classes with same inner type
            property != null && sourceField.type.isValueClass() && field.type.isValueClass()
                && sourceField.type.makeNotNull() != field.type.makeNotNull() ->
                builder.irValueClassWrap(
                    builder.irValueClassUnwrap(property, sourceField.type),
                    field.type
                )
            else -> property
        }
    }
    ?: toShape.constructor.defaultValue(index)
```

**b)** Add private helper methods in the class body (near `irWideningCall`):

```kotlin
private fun IrBuilder.irValueClassUnwrap(receiver: IrExpression, valueClassType: IrType): IrExpression {
    val irClass = valueClassType.classOrNull?.owner ?: error("Cannot resolve value class")
    val property = irClass.declarations
        .filterIsInstance<IrProperty>()
        .firstOrNull { it.getter != null }
        ?: error("No property found in value class ${irClass.name}")
    return irCall(property.getter!!.symbol).apply {
        dispatchReceiver = receiver
    }
}

private fun IrBuilder.irValueClassWrap(receiver: IrExpression, targetType: IrType): IrExpression {
    val irClass = targetType.classOrNull?.owner ?: error("Cannot resolve value class for wrapping")
    val constructor = irClass.constructors.firstOrNull()
        ?: error("No constructor found for value class ${irClass.name}")
    return irCallConstructor(constructor.symbol, emptyList()).apply {
        arguments[0] = receiver
    }
}
```

**c)** Update `construct` method's `when` block (final combined version including Task 2's widening changes):

```kotlin
when {
    type.run { isPrimitiveType() || isString() } -> {
        when {
            // Unwrap value class to primitive
            property.type.isValueClass() && !type.isValueClass() ->
                irValueClassUnwrap(property, property.type)
            // Widening
            property.type.makeNotNull() != type.makeNotNull()
                && isWideningAllowed(property.type, type) ->
                irWideningCall(property, type)
            else -> property
        }
    }
    // Wrap primitive into value class
    type.isValueClass() && !property.type.isValueClass() ->
        irValueClassWrap(property, type)
    // Value-to-value: different value classes, same inner type
    type.isValueClass() && property.type.isValueClass()
        && type.makeNotNull() != property.type.makeNotNull() ->
        irValueClassWrap(irValueClassUnwrap(property, property.type), type)
    else -> construct(
        property,
        toShape = toShape.fields[index].type.convertShape(),
        fromShape = fromShape.fields[index].type.convertShape()
    )
}
```

- [ ] **Step 3: Run unwrap test to verify it passes**

Run: `./gradlew :test-integration:test --tests "community.flock.kmapper.KMapperTest.shouldCompile_valueClassUnwrap" --info`
Expected: PASS with output "Target(id=42, name=test)"

- [ ] **Step 4: Add remaining value class tests**

Add to `KMapperTest.kt`:

```kotlin
@Test
fun shouldCompile_valueClassWrap() {
    IntegrationTest(options)
        .file("App.kt") {
            $$"""
            |package sample
            |
            |import community.flock.kmapper.mapper
            |
            |@JvmInline
            |value class Id(val id: Int)
            |data class Source(val id: Int, val name: String)
            |data class Target(val id: Id, val name: String)
            |
            |fun main() {
            |  val source = Source(id=42, name="test")
            |  val target:Target = source.mapper()
            |  println(target)
            |}
            |
            """.trimMargin()
        }
        .compileSuccess { output ->
            assertTrue(
                output.contains("Target(id=Id(id=42), name=test)"),
                "Expected Target(id=Id(id=42), name=test) in output"
            )
        }
}

@Test
fun shouldCompile_valueClassToValueClass() {
    IntegrationTest(options)
        .file("App.kt") {
            $$"""
            |package sample
            |
            |import community.flock.kmapper.mapper
            |
            |@JvmInline
            |value class SourceId(val id: Int)
            |data class Source(val id: SourceId, val name: String)
            |
            |@JvmInline
            |value class TargetId(val id: Int)
            |data class Target(val id: TargetId, val name: String)
            |
            |fun main() {
            |  val source = Source(id=SourceId(42), name="test")
            |  val target:Target = source.mapper()
            |  println(target)
            |}
            |
            """.trimMargin()
        }
        .compileSuccess { output ->
            assertTrue(
                output.contains("Target(id=TargetId(id=42), name=test)"),
                "Expected Target(id=TargetId(id=42), name=test) in output"
            )
        }
}

@Test
fun shouldCompile_valueClassUnwrapToNullable() {
    IntegrationTest(options)
        .file("App.kt") {
            $$"""
            |package sample
            |
            |import community.flock.kmapper.mapper
            |
            |@JvmInline
            |value class Id(val id: Int)
            |data class Source(val id: Id, val name: String)
            |data class Target(val id: Int?, val name: String)
            |
            |fun main() {
            |  val source = Source(id=Id(42), name="test")
            |  val target:Target = source.mapper()
            |  println(target)
            |}
            |
            """.trimMargin()
        }
        .compileSuccess { output ->
            assertTrue(
                output.contains("Target(id=42, name=test)"),
                "Expected Target(id=42, name=test) in output"
            )
        }
}

@Test
fun shouldFail_nullableValueClassToNonNullable() {
    IntegrationTest(options)
        .file("App.kt") {
            $$"""
            |package sample
            |
            |import community.flock.kmapper.mapper
            |
            |@JvmInline
            |value class Id(val id: Int)
            |data class Source(val id: Id?, val name: String)
            |data class Target(val id: Int, val name: String)
            |
            |fun main() {
            |  val source = Source(id=Id(42), name="test")
            |  val target:Target = source.mapper()
            |  println(target)
            |}
            |
            """.trimMargin()
        }
        .compileFail { output ->
            assertTrue(
                output.contains("Missing mapping for: id"),
                "Expected Missing mapping for: id"
            )
        }
}

@Test
fun shouldFail_valueClassWideningCompose() {
    IntegrationTest(options)
        .file("App.kt") {
            $$"""
            |package sample
            |
            |import community.flock.kmapper.mapper
            |
            |@JvmInline
            |value class Id(val id: Int)
            |data class Source(val id: Id, val name: String)
            |data class Target(val id: Long, val name: String)
            |
            |fun main() {
            |  val source = Source(id=Id(42), name="test")
            |  val target:Target = source.mapper()
            |  println(target)
            |}
            |
            """.trimMargin()
        }
        .compileFail { output ->
            assertTrue(
                output.contains("Missing mapping for: id"),
                "Expected Missing mapping for: id"
            )
        }
}
```

- [ ] **Step 5: Update existing `shouldFail_valueClassap` to expect success**

Replace the existing test at line ~396 (rename + change from `compileFail` to `compileSuccess`):

```kotlin
@Test
fun shouldSuccess_valueClassUnwrapMap() {
    IntegrationTest(options)
        .file("App.kt") {
            $$"""
            |package sample
            |
            |import community.flock.kmapper.mapper
            |
            |@JvmInline
            |value class Id(val id: Int)
            |data class User(val id: Id, val name: String)
            |
            |data class UserDto(val id: Int, val name: String)
            |
            |fun main() {
            |  val user = User(id=Id(1), name="John Doe")
            |  val res:UserDto = user.mapper()
            |  println(res)
            |}
            |
            """.trimMargin()
        }
        .compileSuccess { output ->
            assertTrue(
                output.contains("UserDto(id=1, name=John Doe)"),
                "Expected UserDto(id=1, name=John Doe) in output"
            )
        }
}
```

- [ ] **Step 6: Run all tests**

Run: `./gradlew :test-integration:test --info`
Expected: All tests pass (including all existing tests — no regressions)

- [ ] **Step 7: Commit value class IR and tests**

```bash
git add compiler-plugin/src/main/kotlin/community/flock/kmapper/compiler/ir/KMapperIrLogic.kt compiler-plugin/src/main/kotlin/community/flock/kmapper/compiler/ir/KMapperIrBuildMapperVisitor.kt test-integration/src/test/kotlin/community/flock/kmapper/KMapperTest.kt
git commit -m "feat: add value class wrap/unwrap IR generation and tests"
```

---

### Task 5: Final verification

- [ ] **Step 1: Run full test suite and verify no regressions**

Run: `./gradlew test --info`
Expected: All tests pass across all modules

- [ ] **Step 2: Commit any final fixes if needed**

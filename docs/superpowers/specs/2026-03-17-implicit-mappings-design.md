# Implicit Type Mappings for KMapper

## Summary

Add implicit type mappings to kmapper so that compatible types can be automatically mapped without explicit conversion. Two categories:

1. **Numeric widening** — numeric promotions (some precision-lossy, see table)
2. **Value class wrapping/unwrapping** — automatic wrap, unwrap, and value-to-value mapping

These two categories do **not** compose — value class inner types must match exactly.

## Numeric Widening

### Widening Table

| Source | Allowed Targets | Notes |
|--------|----------------|-------|
| Byte   | Short, Int, Long, Float, Double | All lossless except Float (>24-bit values) |
| Short  | Int, Long, Float, Double | All lossless except Float (>24-bit values) |
| Int    | Long, Float, Double | Long is lossless. Float loses precision for values > ~16M. Double is lossless. |
| Long   | Float, Double | Both precision-lossy for large values (Float >24 bits, Double >53 bits) |
| Float  | Double | Lossless |

These match JVM's standard widening conversions. Precision-lossy conversions (e.g., `Int` → `Float`) are included because they are widely accepted in practice and match Java's implicit widening behavior.

Narrowing (e.g., `Long` → `Int`) is **not** allowed.

**Note:** `String` is not a primitive in Kotlin FIR's `isPrimitive` sense, so it is unaffected by the `primaryEqual` changes.

### FIR Phase Changes (`KmapperFirLogic.kt`)

Modify `primaryEqual` to check the widening table when exact type equality fails. If the source type can widen to the target type, return `true`.

```kotlin
private val WIDENING_TABLE: Map<ClassId, Set<ClassId>> = mapOf(
    StandardClassIds.Byte to setOf(StandardClassIds.Short, StandardClassIds.Int, StandardClassIds.Long, StandardClassIds.Float, StandardClassIds.Double),
    StandardClassIds.Short to setOf(StandardClassIds.Int, StandardClassIds.Long, StandardClassIds.Float, StandardClassIds.Double),
    StandardClassIds.Int to setOf(StandardClassIds.Long, StandardClassIds.Float, StandardClassIds.Double),
    StandardClassIds.Long to setOf(StandardClassIds.Float, StandardClassIds.Double),
    StandardClassIds.Float to setOf(StandardClassIds.Double),
)
```

Also fix the duplicated nullable check on lines 42-43 while modifying this function.

### IR Phase Changes (`KMapperIrBuildMapperVisitor.kt`)

**Field matching:** The IR-phase `Field.equals` (in `KMapperIrLogic.kt`) compares types via `type.makeNotNull()`. This will reject widened type pairs. Modify the field matching logic to also check the widening table when exact type equality fails, so that an `Int` source field can match a `Long` target field.

**Conversion call generation:** Currently, primitives are passed through directly (line ~244: `type.run { isPrimitiveType() || isString() } -> property`). Add a check: when source and target primitive types differ (widening case), generate a call to the appropriate `toXxx()` function instead of a direct property reference:

1. Compare source field type to target field type
2. If they differ and the pair is in the widening table, resolve `toXxx()` on the source type (e.g., `Int.toLong()`)
3. Generate IR call: `it.field.toLong()` instead of `it.field`

## Value Class Wrapping/Unwrapping

### FIR Phase Changes (`KmapperFirLogic.kt`)

Add a new `valueClassEqual` function to the `deepEqual` chain (after `primaryEqual`, before `enumsEqual`):

```
deepEqual = name match
  && nullableEqual
  && (primaryEqual || valueClassEqual || enumsEqual || collectionsEqual || fieldsEqual)
```

Placement rationale: `primaryEqual` checks `isPrimitive` on both sides, so a value class (not primitive) falls through correctly. `valueClassEqual` is checked before `enumsEqual` and `fieldsEqual` because value classes have a specific single-field structure that should be matched intentionally rather than accidentally via `fieldsEqual`.

`valueClassEqual` logic:
- **Unwrap**: Source is a value class → extract its single constructor parameter type → check exact match with target type
- **Wrap**: Target is a value class → extract its single constructor parameter type → check exact match with source type
- **Value-to-value**: Both are value classes → check their single constructor parameter types match exactly

Detection: Use `isInline` on `FirRegularClassSymbol` to identify value classes. Exclude multi-field value classes (Valhalla preview) by requiring exactly one constructor parameter.

**Nullable handling:** Nullability is already checked by `nullableEqual` before `valueClassEqual` runs. This means:
- `Id` → `Int?` works (non-nullable to nullable, allowed by `nullableEqual`)
- `Id?` → `Int` fails (nullable to non-nullable, blocked by `nullableEqual`)
- `Id?` → `Int?` works (both nullable, allowed by `nullableEqual`)

### IR Phase Changes (`KMapperIrBuildMapperVisitor.kt`)

**Field matching:** Same as widening — modify field matching to recognize value class wrap/unwrap pairs.

**Code generation:**
- **Unwrap**: Access the single declared property of the value class dynamically (resolve via the class's single constructor parameter, do not hardcode property names). Example output: `it.id.id`
- **Wrap**: Generate constructor call with the source value as argument. Example output: `Id(it.id)`
- **Value-to-value**: Unwrap then wrap. Example output: `TargetId(it.sourceId.id)`

## Behavior Changes

**Breaking change:** The existing test `shouldFail_valueClassap` (line ~397 in `KMapperTest.kt`) expects that `value class Id(Int)` → `Int` mapping fails. This spec changes that behavior to succeed. The test must be updated from `compileFail` to `compileSuccess` with value verification.

## Non-Goals

- No composing value class unwrap/wrap with numeric widening (e.g., `value class Id(val id: Int)` → `Long` is NOT allowed)
- No narrowing conversions
- No string/enum coercion

## Testing

All tests in existing `KMapperTest.kt`.

### Numeric Widening Tests
- Byte → Short, Int, Long, Float, Double
- Short → Int, Long, Float, Double
- Int → Long, Float, Double
- Long → Float, Double
- Float → Double
- Int → Long? (widening + nullable target)
- Negative: Long → Int (should fail)
- Negative: Double → Float (should fail)

### Value Class Tests
- Unwrap: `data class A(val id: Id)` → `data class B(val id: Int)` where `Id` wraps `Int`
- Wrap: `data class A(val id: Int)` → `data class B(val id: Id)`
- Value-to-value: `SourceId` → `TargetId` where both wrap `Int`
- Unwrap in nested: `data class A(val inner: Inner)` where `Inner` has value class field → target with plain type
- Unwrap in List: `List<SourceWithId>` → `List<TargetWithInt>` where Id wraps Int
- Nullable: `Id` → `Int?` (should work), `Id?` → `Int` (should fail)
- Negative: `data class A(val id: Id)` → `data class B(val id: Long)` (no compose with widening)
- Update existing `shouldFail_valueClassap` test to expect success

## Files to Modify

1. `compiler-plugin/src/main/kotlin/community/flock/kmapper/compiler/fir/KmapperFirLogic.kt` — widening table, `valueClassEqual`, update `deepEqual`, fix duplicated nullable check
2. `compiler-plugin/src/main/kotlin/community/flock/kmapper/compiler/ir/KMapperIrBuildMapperVisitor.kt` — IR generation for widening calls and value class wrap/unwrap
3. `compiler-plugin/src/main/kotlin/community/flock/kmapper/compiler/ir/KMapperIrLogic.kt` — update `Field.equals` to handle widening and value class type pairs
4. `test-integration/src/test/kotlin/community/flock/kmapper/KMapperTest.kt` — new test cases, update `shouldFail_valueClassap`

# Implicit Type Mappings for KMapper

## Summary

Add implicit type mappings to kmapper so that compatible types can be automatically mapped without explicit conversion. Two categories:

1. **Numeric widening** — safe/generally-safe numeric promotions
2. **Value class wrapping/unwrapping** — automatic wrap, unwrap, and value-to-value mapping

These two categories do **not** compose — value class inner types must match exactly.

## Numeric Widening

### Widening Table

| Source | Allowed Targets |
|--------|----------------|
| Byte   | Short, Int, Long, Float, Double |
| Short  | Int, Long, Float, Double |
| Int    | Long, Float, Double |
| Long   | Float, Double |
| Float  | Double |

Narrowing (e.g., `Long` → `Int`) is **not** allowed.

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

### IR Phase Changes (`KMapperIrBuildMapperVisitor.kt`)

When field types don't match exactly but widening is allowed, generate a conversion call:
- Resolve the appropriate `toXxx()` function on the source type (e.g., `toLong()`, `toDouble()`)
- Generate an IR call: `it.field.toLong()` instead of `it.field`

## Value Class Wrapping/Unwrapping

### FIR Phase Changes (`KmapperFirLogic.kt`)

Add a new `valueClassEqual` function to the `deepEqual` chain (after `primaryEqual`, before `enumsEqual`):

```
deepEqual = name match
  && nullableEqual
  && (primaryEqual || valueClassEqual || enumsEqual || collectionsEqual || fieldsEqual)
```

`valueClassEqual` logic:
- **Unwrap**: Source is a value class → extract inner type → check exact match with target type
- **Wrap**: Target is a value class → extract inner type → check exact match with source type
- **Value-to-value**: Both are value classes → check their inner types match exactly

Detection: Use `isInline` on the class symbol to identify value classes.

### IR Phase Changes (`KMapperIrBuildMapperVisitor.kt`)

- **Unwrap**: Generate property access on the value class instance (e.g., `it.id.id`)
- **Wrap**: Generate constructor call (e.g., `Id(it.id)`)
- **Value-to-value**: Unwrap then wrap (e.g., `TargetId(it.sourceId.id)`)

## Non-Goals

- No composing value class unwrap/wrap with numeric widening (e.g., `value class Id(val id: Int)` → `Long` is NOT allowed)
- No narrowing conversions
- No string/enum coercion

## Testing

All tests in existing `KMapperTest.kt`.

### Numeric Widening Tests
- Byte → Short, Int, Long, Float, Double
- Short → Int, Long
- Int → Long, Float, Double
- Long → Float, Double
- Float → Double
- Negative: Long → Int (should fail)

### Value Class Tests
- Unwrap: `data class A(val id: Id)` → `data class B(val id: Int)` where `Id` wraps `Int`
- Wrap: `data class A(val id: Int)` → `data class B(val id: Id)`
- Value-to-value: `SourceId` → `TargetId` where both wrap `Int`
- Negative: `data class A(val id: Id)` → `data class B(val id: Long)` (no compose with widening)

## Files to Modify

1. `compiler-plugin/src/main/kotlin/community/flock/kmapper/compiler/fir/KmapperFirLogic.kt` — widening table, `valueClassEqual`, update `deepEqual`
2. `compiler-plugin/src/main/kotlin/community/flock/kmapper/compiler/ir/KMapperIrBuildMapperVisitor.kt` — IR generation for widening calls and value class wrap/unwrap
3. `test-integration/src/test/kotlin/community/flock/kmapper/KMapperTest.kt` — new test cases

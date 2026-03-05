# Assignment-Based DSL Syntax Design

## Goal

Change the KMapper DSL syntax from property-reference + infix style to natural assignment style while keeping all existing functionality.

## Syntax Change

```kotlin
// OLD
val userDto: UserDto = user.mapper {
    to::age map it.age.toString()
    to::name map "${it.firstName} ${it.lastName}"
    to::active.ignore()
}

// NEW
val userDto: UserDto = user.mapper {
    age = it.age.toString()
    name = "${it.firstName} ${it.lastName}"
    active.ignore()
}
```

The no-arg `user.mapper()` for identical classes remains unchanged.

## Architecture

### Runtime (`compiler-runtime`)

The `Mapper` class is removed. The mapper function signature changes so `TO` is the lambda receiver:

```kotlin
@KMapper
fun <TO, FROM> FROM.mapper(block: (TO.(it: FROM) -> Unit)? = null): TO = generated()

fun <T> T.ignore() {}

@KMapperInternal
fun __mapField(name: String, value: Any?) {}
```

Key change: `TO` is the lambda receiver. Inside the lambda, `age` resolves to `UserDto.age` (a `val` property of the target data class).

### FIR Phase

**A. `FirAssignExpressionAltererExtension`** -- Intercepts val-property assignments inside mapper lambdas. Transforms `age = value` (assignment to `UserDto.age`, a val) into `__mapField("age", value)` (a function call). This prevents the "val cannot be reassigned" compiler error.

**B. `FirCallChecker`** (updated) -- Validates mapping completeness on the mapper call. Inspects the lambda body for `__mapField()` calls and `ignore()` calls to determine which fields are mapped.

### IR Phase

`KMapperIrBuildMapperVisitor.createMapperImplementationFromCall()` changes mapping extraction:

- **Old:** Extract from `IrCall` statements (`map`/`ignore` infix calls) via `IrPropertyReference`
- **New:** Extract from `IrCall` to `__mapField` (field name from string arg, value from second arg) + `IrCall` to `ignore()` (field name from extension receiver getter)

Constructor building, nested mapping, enum mapping, list handling, and `it`-remapping logic remain unchanged.

### Design Decision: Why not FirVariableAssignmentChecker?

`FirAssignExpressionAltererExtension` transforms assignments during resolution (before checkers run). By the time `FirVariableAssignmentChecker` would fire, the assignments have already been transformed into function calls. Validation is therefore done via `FirCallChecker` on the mapper call.

### Design Decision: Why not FirDeclarationGenerationExtension?

`FirDeclarationGenerationExtension.getCallableNamesForClass()` operates on the class declaration, not per-instantiation. When generating members for `Mapper<TO, FROM>`, `TO` is an unsubstituted type parameter -- we cannot enumerate the target type's constructor params.

By making `TO` the lambda receiver instead, property resolution happens naturally through Kotlin's type system.

## Testing

All existing integration tests updated to new syntax. Tests using `mapper()` with no lambda are unchanged. The `IntegrationTest` infrastructure stays the same -- only Kotlin source strings in test cases change.

## Files Changed

- `compiler-runtime/src/main/kotlin/community/flock/kmapper/Runtime.kt` -- Remove Mapper class, change mapper signature
- New: `compiler-plugin/.../fir/KMapperAssignAlterer.kt` -- Assignment alteration
- `compiler-plugin/.../fir/KMapperFirMappingChecker.kt` -- Update to validate __mapField/ignore calls
- `compiler-plugin/.../KMapperFirExtensionRegistrar.kt` -- Register new FIR extension
- `compiler-plugin/.../ir/KMapperIrBuildMapperVisitor.kt` -- Extract __mapField/ignore calls
- `test-integration/.../KMapperTest.kt` -- Update all test syntax
- `README.md` -- Update examples

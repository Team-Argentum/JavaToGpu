# ASM Contract

JavaToGpu includes a structured ASM frontend for integrations that already own an AST, bytecode generator, or compiler pipeline.

This frontend is not a general JVM decompiler. It expects a canonical GPU-safe bytecode subset emitted intentionally by tooling you control.

## Recommended Architecture

Use this path:

```text
your AST or IR -> canonical supported ASM -> JavaToGpu ASM frontend -> IR -> OpenCL
```

Do not rely on this path:

```text
arbitrary JVM bytecode -> automatic recovery -> GPU kernel
```

## Expected Bytecode Shape

- Static methods only.
- Predictable control-flow graphs.
- Explicit locals for important intermediate values.
- Stable stack shapes at merge points.
- `INVOKESTATIC` calls to supported helper owners.
- Whitelisted construction only for supported wrappers and value types.
- Math and backend operations normalized to `GPU.*` or known helper methods.

## Unsupported Bytecode Shapes

- `invokevirtual`, `invokeinterface`, and `invokedynamic` as general dispatch mechanisms.
- Exception tables and exception-driven control flow.
- Monitor enter/exit and Java synchronization semantics.
- General object allocation and object graph access.
- Recursion and unbounded dynamic call graphs.
- Bytecode patterns that cannot be proven GPU-safe before lowering.

## Read-Only ASM Diagnostics

Before attempting real lowering, integrations can run the ASM frontend in a read-only reporting mode through `GpuProgramCompiler.reportStructuredAsm(...)`.

```java
GpuProgramCompiler compiler = GpuProgramCompiler.createDefault();
AsmFrontendFailureReport report = compiler.reportStructuredAsm(List.of(kernelMethod, helperMethod));

if (!report.successful()) {
    System.err.println(report.summaryLine());
    report.failures().forEach(failure -> System.err.println(failure.summary()));
}
```

When an integration already has a full ASM `ClassNode`, use the class-level preflight instead of manually wrapping every method:

```java
AsmFrontendFailureReport report = compiler.reportStructuredAsmClass(classNode);
```

For file or byte-array integrations, use the matching bytecode loading helpers:

```java
AsmFrontendFailureReport fromBytes = compiler.reportStructuredAsmClass(classBytes);
AsmFrontendFailureReport fromFile = compiler.reportStructuredAsmClassFile(classFilePath);
AsmFrontendFailureReport fromDirectory = compiler.reportStructuredAsmClassDirectory(classesDirectory);
AsmFrontendFailureReport fromJar = compiler.reportStructuredAsmJar(jarPath);
AsmFrontendFailureReport fromArtifact = compiler.reportStructuredAsmArtifact(pathToClassDirectoryClassFileOrJar);
```

Directory preflight walks `.class` files recursively in stable path order and aggregates failures into the same report shape. This is useful for CI checks over compiled output directories before attempting any real lowering.

Jar preflight reads `.class` entries in stable entry-name order and aggregates failures into the same report shape. This is useful for checking already packaged artifacts before publishing or loading them into a runtime integration.

Artifact preflight auto-detects directories, `.class` files, and `.jar` files, then delegates to the matching preflight mode. This is the recommended CI entry point when build tooling receives a generic compiled artifact path.

The same report exposes a readiness classifier for broader ASM ingestion work:

```java
AsmFrontendReadinessReport readiness = compiler
        .reportStructuredAsmArtifactReadiness(pathToClassDirectoryClassFileOrJar);

System.err.println(readiness.summaryLine());
```

Readiness verdicts are intentionally conservative:

- `supported` means the current GPU-safe ASM subset accepted the artifact.
- `rewriteRequired` means the artifact contains bytecode that can usually be normalized by an upstream bytecode generator, such as `ARRAYLENGTH`, object allocation, virtual dispatch, exceptions, synchronization, field access, or unsupported owners.
- `rejected` means at least one failure likely needs manual redesign or stronger type/semantic support before the compiler should try to transform it.

The normal `.properties` report now includes nested `asmReport.readiness.*` fields, including `verdict`, `rewriteRequiredFailureCount`, `rejectedFailureCount`, and grouped `actionCount.*` counters for CI dashboards.

Readiness also exports migration buckets so build tooling can group remediation work without parsing diagnostic text:

- `arrayMetadata` - pass lengths, dimensions, and bounds explicitly instead of reading JVM array metadata.
- `hostMemoryModel` - move runtime allocation and ownership to host setup.
- `explicitFailureModel` - replace Java exceptions with explicit status outputs or deliberate GPU trap/unreachable calls.
- `synchronizationModel` - remove JVM monitor semantics before GPU lowering.
- `staticDispatchModel` - rewrite dynamic or unsupported calls into whitelisted static GPU helpers.
- `fieldStateModel` - flatten field state into explicit parameters, structs, or locals.
- `objectModel` - replace heap objects, casts, and type checks with GPU-safe value shapes.
- `typeSignatureModel` - redesign unsupported descriptors into GPU-safe primitive, vector, pointer, image, or struct types.

CI fields include `asmReport.readiness.firstMigrationBucket`, `asmReport.readiness.migrationBucketCounts`, `asmReport.readiness.migrationBucket.<bucket>`, and `asmReport.readiness.migrationGuidance`.

For broader parser work, use the bytecode shape inventory to count risky JVM constructs even before deciding whether they map to a concrete frontend failure:

```java
AsmBytecodeShapeInventoryReport inventory = compiler
        .inventoryStructuredAsmArtifact(pathToClassDirectoryClassFileOrJar);

System.err.println(inventory.summaryLine());
```

Inventory fields use the `asmShapeInventory.*` prefix when written through `writeStructuredAsmArtifactInventory(...)`. They include `riskyShapeCount`, `kindCounts`, `kind.<shape>`, `firstRiskyShape`, and per-observation details. This is intended for planning the improved arbitrary-ASM parser by showing real bytecode shape profiles from `.class` directories or jars without changing accepted lowering behavior.

For CI and migration planning, use the combined artifact snapshot when you want one read-only report containing preflight failures, readiness, and bytecode shape inventory:

```java
AsmFrontendArtifactReport artifactReport = compiler
        .reportStructuredAsmArtifactSnapshot(pathToClassDirectoryClassFileOrJar);

System.err.println(artifactReport.summaryLine());
```

Snapshot fields use the `asmArtifactReport.*` prefix when written through `writeStructuredAsmArtifactSnapshot(...)`. They include top-level `successful`, `summary`, `failureCount`, `riskyShapeCount`, nested `readiness.*`, nested `failureReport.*`, and nested `shapeInventory.*` fields. This keeps validator decisions and parser-planning inventory separate while giving build tooling a single artifact to archive.

For CI jobs that need a machine-readable file, write the report as `.properties`:

```java
AsmFrontendFailureReport report = compiler.writeStructuredAsmArtifactReport(
        pathToClassDirectoryClassFileOrJar,
        Path.of("build/reports/javatogpu-asm-preflight.properties")
);
```

The file uses the same stable `asmReport.*` fields returned by `report.artifactFields("asmReport")`.

Shape inventory can be written separately:

```java
compiler.writeStructuredAsmArtifactInventory(
        pathToClassDirectoryClassFileOrJar,
        Path.of("build/reports/javatogpu-asm-shape-inventory.properties")
);
```

Or write the combined snapshot in one file:

```java
compiler.writeStructuredAsmArtifactSnapshot(
        pathToClassDirectoryClassFileOrJar,
        Path.of("build/reports/javatogpu-asm-artifact-snapshot.properties")
);
```

To make a build fail after writing the same artifact, use the fail-on-unsupported helper:

```java
compiler.writeAndRequireStructuredAsmArtifactReport(
        pathToClassDirectoryClassFileOrJar,
        Path.of("build/reports/javatogpu-asm-preflight.properties")
);
```

It throws `AsmFrontendException` when the report contains unsupported bytecode, while preserving the first failure metadata for diagnostics.

### Gradle / CI Preflight Example

For a build pipeline, keep the preflight code in a tiny Java entry point and call it from Gradle after `classes` or `jar`.

```java
// src/test/java/buildsupport/AsmPreflightMain.java
package buildsupport;

import net.sixik.ga_utils.javatogpu.frontend.GpuProgramCompiler;

import java.nio.file.Path;

public final class AsmPreflightMain {
    private AsmPreflightMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: AsmPreflightMain <classes-or-jar> <report.properties>");
        }

        GpuProgramCompiler.createDefault().writeAndRequireStructuredAsmArtifactReport(
                Path.of(args[0]),
                Path.of(args[1])
        );
    }
}
```

```groovy
tasks.register('javatogpuAsmPreflight', JavaExec) {
    dependsOn classes
    classpath = sourceSets.test.runtimeClasspath
    mainClass = 'buildsupport.AsmPreflightMain'
    args layout.buildDirectory.dir('classes/java/main').get().asFile.absolutePath,
            layout.buildDirectory.file('reports/javatogpu-asm-preflight.properties').get().asFile.absolutePath
}

check.dependsOn tasks.named('javatogpuAsmPreflight')
```

For packaged artifacts, switch the first argument to the jar output path and make the task depend on `jar` instead of `classes`.

The report does not expand the supported ASM subset and does not mutate or lower the method. It uses the same GPU-safe type and owner rules as the real validator, then returns stable metadata for unsupported patterns such as:

- `arrayLength` for `ARRAYLENGTH` bytecode.
- `arrayAllocation` for runtime array allocation shapes.
- `exceptionControlFlow` for `ATHROW` and exception-table control flow.
- `monitorSynchronization` for monitor/synchronized bytecode.
- `methodInvocation` for unsupported call kinds or owners.
- `methodDescriptor` for unsupported helper argument/return types.
- `fieldAccess` for unsupported field owners or descriptors.
- `objectType` for unsupported casts, type checks, or constructors.

Each failure exposes `summary()` plus `.properties`-friendly fields through `artifactFields(...)`, including `family`, `methodKey`, `instructionIndex`, `lineNumber`, `opcode`, and `detail`.

## Best Practices

- Keep the operand stack shallow.
- Prefer simple locals over clever stack manipulation.
- Normalize loops before emission.
- Keep helper signatures explicit and stable.
- Emit predictable casts and primitive operations.
- Run `reportStructuredAsm(...)`, `reportStructuredAsmClass(...)`, or `reportStructuredAsmArtifact(...)` in build tooling before calling `compileStructuredAsm(...)` when ingesting externally generated bytecode.
- Treat validation errors as frontend contract feedback, not runtime surprises.

## Future Direction

A broader arbitrary-ASM ingestion frontier is planned, but it is not part of the current alpha contract. The current frontend is deliberately conservative so compiler integrations can generate predictable GPU-safe input.

## Related Documents

- [Language Contract](Language-Contract.md)
- [Known Limitations](Known-Limitations.md)
- [API Overview](API-Overview.md)

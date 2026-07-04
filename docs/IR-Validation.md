# IR Validation

The `javatogpu-ir-validation` artifact is an optional strict-build module for compiler development, CI, and safety-focused builds.

It plugs into JavaToGpu through Java `ServiceLoader` and runs extra lowered-IR checks before OpenCL emission. The main `javatogpu` artifact stays lightweight; users opt in by adding the validation artifact to the annotation-processor path.

The artifact is intentionally inert unless the compiler option `javatogpu.irValidation` is enabled, so projects can keep it on a CI annotation-processor path without changing normal local builds.

## Add The Module

Use the same version as the main JavaToGpu artifact:

```groovy
dependencies {
    implementation 'io.github.deussixik:javatogpu:0.1.0-alpha.1'
    annotationProcessor 'io.github.deussixik:javatogpu:0.1.0-alpha.1'

    // Optional: stricter lowered-IR validation and read-only optimizer planning checks.
    annotationProcessor 'io.github.deussixik:javatogpu-ir-validation:0.1.0-alpha.1'
}

tasks.withType(JavaCompile).configureEach {
    options.compilerArgs += '-Ajavatogpu.irValidation=diagnostic'
    options.compilerArgs += '-Ajavatogpu.irValidationDiagnostics=summary'
    options.compilerArgs += '-Ajavatogpu.irValidationReport=reports/javatogpu-ir-validation.properties'
}
```

Use `diagnostic` first when adopting the module. It runs the unified validation pipeline without failing builds for optimizer diagnostics.

## What It Checks

The strict validator currently checks the lowered IR for issues that should be caught before backend emission:

- Unknown variable references and duplicate local declarations.
- Invalid assignment targets.
- Invalid `break`, loop-break, and `continue` placement.
- Helper-call reachability, dependency metadata, and void-helper result metadata.
- Intrinsic backend/template/result metadata and placeholder consistency.
- Struct-initializer metadata, return-shape consistency, and blank type/operator metadata.
- Non-positive literal private-array sizes.
- Pure expression statements that have no observable side effect.

## Unified Pipeline

The module registers a `GpuIrValidationProvider` through Java `ServiceLoader`. When `javatogpu.irValidation` is enabled, the compiler invokes a unified read-only pipeline for every lowered helper and entry-point method.

That pipeline combines safety validation, CSE planning preview, and auto-vectorization preview. It is intentionally read-only: it does not rewrite IR or change generated OpenCL. The goal is to harden optimizer analysis before real transformations are enabled.

Current planning stages include:

- Pure-expression classification.
- Stable expression fingerprints.
- Conservative canonical matching for safe commutative operators.
- Scanner options for diagnostic vs optimizer-focused candidate reports.
- Candidate classification into local reuse, helper reuse, intrinsic reuse, and unsafe shapes.
- Straight-line scope filtering to avoid branch/loop/switch boundaries.
- Mutation proximity checks for variables and arrays between occurrences.
- Read-only rewrite-plan reports with skipped-candidate reasons.

## Planning Modes

Compiler builds can select one of these annotation-processor option values:

```text
off
diagnostic
strictSafety
strictOptimizer
```

`off` is the default and skips optional validation providers.

`diagnostic` runs safety validation plus read-only CSE and auto-vectorization previews. It is the safest first opt-in mode for public alpha users and reports a compact javac `NOTE` summary for each validated lowered method.

`strictSafety` fails builds when lowered-IR safety validation fails.

`strictOptimizer` fails builds when safety validation fails or optimizer diagnostics report skipped CSE candidates, auto-vectorization warnings, or rejected vectorization shapes.

Example strict CI configuration:

```groovy
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs += '-Ajavatogpu.irValidation=strictSafety'
}
```

Optimizer diagnostics can include reasons such as:

- `NOT_LOCAL_REUSE`
- `CONTROL_FLOW_BOUNDARY`
- `MUTATED_BETWEEN_OCCURRENCES`
- `UNSUPPORTED_LANE_COUNT`
- `SIDE_EFFECTING_VALUE`

Use `strictOptimizer` mainly for compiler development and internal hardening. It is expected to be conservative while optimizer analysis is still maturing.

## Diagnostic Output

`javatogpu.irValidationDiagnostics` controls javac `NOTE` output in `diagnostic` mode:

```text
quiet
summary
detailed
```

`summary` is the default and emits compact aggregate counters. `quiet` suppresses diagnostic notes while still running the read-only pipeline. `detailed` emits nested optimizer context and is intended for CI/debug runs where verbose javac output is acceptable.

## Report Artifact

`javatogpu.irValidationReport` writes a machine-readable `.properties` artifact under the generated-source output directory. The value must be a relative resource path, for example:

```text
reports/javatogpu-ir-validation.properties
```

The report currently uses format `javatogpu.ir.validation.v1` and records one entry per validated lowered method, including provider name, method name, entry-point flag, safety status, CSE counters, auto-vectorization counters, read-only rewrite-plan operation counters such as `autoVectorizationRewritePlanOperations`, preview-level rewrite gates such as `autoVectorizationCanApplyRewrite` and `autoVectorizationHasPolicyBlockedRewrite`, read-only rewrite-policy fields such as `autoVectorizationRewritePolicyCanRewrite`, `autoVectorizationRewritePolicyPlannedOperations`, `autoVectorizationRewritePolicyBlockingGuards`, and `autoVectorizationRewritePolicyFirstBlockingGuardFamily`, rewrite dry-run fields such as `autoVectorizationRewriteDryRunReadiness`, `autoVectorizationRewriteDryRunSuccessful`, `autoVectorizationRewriteDryRunDiagnostics`, `autoVectorizationRewriteDryRunCandidates`, `autoVectorizationRewriteDryRunOperations`, and `autoVectorizationRewriteDryRunFirstDiagnostic`, resolved rewrite fields such as `autoVectorizationResolvedRewriteInsertions`, `autoVectorizationResolvedRewriteReplacements`, `autoVectorizationResolvedRewriteOperations`, `autoVectorizationResolvedRewriteFirstInsertion`, and `autoVectorizationResolvedRewriteFirstReplacement`, readiness state as `autoVectorizationRewriteReadiness`, rewrite-blocked counters such as `autoVectorizationRewriteBlockedCandidates`, rewrite-plan guard-family counters such as `autoVectorizationRewritePlanGuardFamily.neighborTargetWrite`, `autoVectorizationRewritePlanGuardFamily.backendVectorWidth`, `autoVectorizationRewritePlanGuardFamily.backendDoubleVector`, or `autoVectorizationRewritePlanGuardFamily.memoryAddressSpace`, the first blocking auto-vectorization diagnostic as `autoVectorizationFirstBlockingDiagnostic`, its grouping key as `autoVectorizationFirstBlockingDiagnosticFamily`, vector-shape counters such as `autoVectorizationVectorType.int4`, warning-family counters such as `autoVectorizationWarningFamily.crossLaneRead`, rejection-reason counters such as `autoVectorizationRejectionReason.UNSUPPORTED_LANE_COUNT`, and aggregate optimizer diagnostic counts. This is intended for CI trend tracking and build artifacts; javac diagnostics remain controlled separately by `javatogpu.irValidationDiagnostics`.

## When To Enable It

Recommended uses:

- CI builds for the compiler itself.
- Projects that want stricter diagnostics while adopting JavaToGpu.
- Development of new frontends, lowering rules, intrinsics, or optimizer passes.
- Debugging generated IR before investigating backend-specific OpenCL behavior.

For simple application experiments, the main `javatogpu` artifact is enough. Add `javatogpu-ir-validation` when you prefer earlier and more explicit compiler diagnostics.

## Limitations

The validation module is not a proof of full optimization safety yet.

Current CSE and auto-vectorization planning is intentionally conservative and mostly read-only. It does not perform production common-subexpression elimination, dominance analysis, alias analysis, helper-body equivalence, vectorization, or backend-specific cost modeling yet. Backend-sensitive vector shapes such as `x3` lanes, `double*` vector rewrites, and non-global/read-only memory address spaces are reported as guarded rewrite candidates until ABI/backend, device capability, or memory-space rewrite policy support is proven safe. Internally, rewrite guards now carry typed family metadata while preserving stable string summaries and `.properties` artifact keys, typed insertion/replacement operation previews are available for future applicators, and the read-only rewrite policy exposes `canRewrite` / blocking-guard decisions without mutating IR. `GpuIrAutoVectorizationRewriteApplicator.apply(...)` is currently a no-op safety gate that refuses guarded, warned, or rejected previews before any rewrite attempt, then dry-run validates matching typed operations against the target method and returns unchanged IR. A read-only `GpuIrAutoVectorizationRewriteOperationResolver` also resolves accepted typed operations to concrete loop statement indexes, loop body statement counts, and loop body assignment counts. Separately, the explicit opt-in `rewritePrototype(...)` path can rewrite the narrow fixed-width lane-copy, unary lane, simple lane-wise binary, and lane/literal binary cases into a Java vector alias temporary plus scalar lane writes, returning a `GpuIrAutoVectorizationPrototypeRewriteReport` with typed `GpuIrAutoVectorizationPrototypeAppliedRewrite` metadata for tests and future integration experiments. Applied rewrite metadata now includes the prototype expression family (`LANE_COPY` / `UNARY_LANE_OP` / `BINARY_LANE_OP` / `LANE_LITERAL_BINARY_OP`), stable artifact values such as `laneCopy`, `unaryLaneOp`, `binaryLaneOp`, and `laneLiteralBinaryOp`, plus the concrete unary or binary operator where applicable. The prototype report also exposes family counters through `appliedRewriteCount(...)`, `appliedRewriteCountsByKind()`, `appliedRewriteCountsByArtifactValue()`, and `appliedRewriteFamilyCountersSummary()` so integration experiments can track which rewrite shapes were actually applied. Explicit prototype runners can export `.properties`-friendly fields with `artifactFields(...)`, including stable keys such as `autoVectorizationPrototypeRewriteAppliedRewrites`, `autoVectorizationPrototypeRewriteAppliedRewriteFamily.unaryLaneOp`, and `autoVectorizationPrototypeRewriteFirstAppliedRewriteExpressionKind`. A separate `GpuIrAutoVectorizationPrototypeRuntimeEquivalenceReport` can wrap explicit test/integration equivalence results with fields such as `autoVectorizationPrototypeRuntimeEquivalenceSuccessful`, `autoVectorizationPrototypeRuntimeEquivalenceInputCases`, `autoVectorizationPrototypeRuntimeEquivalenceComparedOutputNames`, and `autoVectorizationPrototypeRuntimeEquivalenceAppliedRewriteFamilies`. `GpuIrAutoVectorizationPrototypeArtifactReport` combines rewrite and runtime-equivalence evidence into one export map with keys such as `autoVectorizationPrototypeArtifactSuccessful`, `autoVectorizationPrototypeArtifactRewrite.AppliedRewrites`, and `autoVectorizationPrototypeArtifactRuntimeEquivalence.Successful`. `GpuIrAutoVectorizationPrototypeArtifactRunner` is the current opt-in helper for tests/integration experiments: callers pass explicit integer-array input cases and compared output names, and it returns the combined artifact after running the prototype rewrite plus lightweight pre/post IR equivalence. Equivalence mismatches and narrow-runner execution failures are reported as failed artifact diagnostics such as `case ... output ... differs` or `case ... execution failed`, rather than being used as normal compiler/validation failures. These helpers do not wire the mutating prototype path into normal validation.

Those checks are being built incrementally so future optimizer transforms can be enabled with testable safety boundaries.

## Read Next

- [Getting Started](Getting-Started.md)
- [Validation and Operations](Validation-and-Operations.md)
- [Diagnostics Reference](Diagnostics-Reference.md)
- [Known Limitations](Known-Limitations.md)

# IR Validation

The `javatogpu-ir-validation` artifact is an optional strict-build module for compiler development, CI, and safety-focused builds.

It plugs into JavaToGpu through Java `ServiceLoader` and runs extra lowered-IR checks before OpenCL emission. The main `javatogpu` artifact stays lightweight; users opt in by adding the validation artifact to the annotation-processor path.

## Add The Module

Use the same version as the main JavaToGpu artifact:

```groovy
dependencies {
    implementation 'io.github.deussixik:javatogpu:0.1.0-alpha.1'
    annotationProcessor 'io.github.deussixik:javatogpu:0.1.0-alpha.1'

    // Optional: stricter lowered-IR validation and read-only optimizer planning checks.
    annotationProcessor 'io.github.deussixik:javatogpu-ir-validation:0.1.0-alpha.1'
}
```

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

## CSE Planning Pass

The module also registers a no-op common-subexpression planning pass.

That pass is intentionally read-only. It builds a CSE planning report during strict validation builds, but it does not rewrite IR or change generated OpenCL. The goal is to harden optimizer analysis before real transformations are enabled.

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

The registered pass defaults to diagnostic mode:

```text
DIAGNOSTIC_ONLY
```

In that mode it computes the planning report but never fails a build because a candidate was skipped. This is the safe default for public alpha users.

The module also has an opt-in hardening mode for tests and future compiler work:

```text
STRICT_FAIL_ON_SKIPPED_CANDIDATES
```

That mode can fail when the planner sees skipped candidates, with reasons such as:

- `NOT_LOCAL_REUSE`
- `CONTROL_FLOW_BOUNDARY`
- `MUTATED_BETWEEN_OCCURRENCES`

Strict planning mode is not enabled by the default `ServiceLoader` registration. Use it directly in tests or internal compiler experiments when you want to validate optimizer assumptions aggressively.

## When To Enable It

Recommended uses:

- CI builds for the compiler itself.
- Projects that want stricter diagnostics while adopting JavaToGpu.
- Development of new frontends, lowering rules, intrinsics, or optimizer passes.
- Debugging generated IR before investigating backend-specific OpenCL behavior.

For simple application experiments, the main `javatogpu` artifact is enough. Add `javatogpu-ir-validation` when you prefer earlier and more explicit compiler diagnostics.

## Limitations

The validation module is not a proof of full optimization safety yet.

Current CSE planning is intentionally conservative and read-only. It does not perform actual common-subexpression elimination, dominance analysis, alias analysis, helper-body equivalence, vectorization, or backend-specific cost modeling yet.

Those checks are being built incrementally so future optimizer transforms can be enabled with testable safety boundaries.

## Read Next

- [Getting Started](Getting-Started.md)
- [Validation and Operations](Validation-and-Operations.md)
- [Diagnostics Reference](Diagnostics-Reference.md)
- [Known Limitations](Known-Limitations.md)

# IR Validation Example

This example shows how to use the optional `javatogpu-ir-validation` module while developing real `@GPU` methods in `examples-app`.

The important source file is:

```text
examples-app/src/main/java/net/sixik/ga_utils/examples/IrAnalysisExamples.java
```

It contains two real kernels that still compile normally:

```text
irPassesCleanly(...)
irPassesButNeedsOptimizerEvidence(...)
```

The first one is intentionally simple. The second one repeats an intrinsic expression so the validator can show optimizer-readiness diagnostics without rewriting IR or changing generated OpenCL.

## Run The Example

From the repository root:

```powershell
.\gradlew.bat `
  "-Pjavatogpu.enableIrValidationExamples=true" `
  "-Pjavatogpu.irValidationMode=diagnostic" `
  "-Pjavatogpu.irValidationDiagnostics=summary" `
  :examples-app:compileJava
```

What this does:

- Adds `:ir-validation` to the annotation processor path for `examples-app`.
- Enables `-Ajavatogpu.irValidation=diagnostic`.
- Writes a machine-readable report without failing the build on optimizer-readiness notes.
- Keeps generated OpenCL and runtime behavior unchanged.

## Report Location

The default report path for this example is:

```text
examples-app/build/generated/sources/annotationProcessor/java/main/reports/examples-app-ir-validation.properties
```

Open that file and start with:

```properties
format=javatogpu.ir.validation.v1
entry.count=...
entry.0.methodName=...
entry.0.severity=...
entry.0.ruleId=...
entry.0.sourceAnchor=...
```

For this example, look for entries whose `methodName` is:

```text
irPassesCleanly
irPassesButNeedsOptimizerEvidence
```

Useful fields for the optimizer-readiness view:

```properties
entry.N.safety=ok
entry.N.optimizerBlockerSource=...
entry.N.optimizerBlockerFamily=...
entry.N.optimizerBlockerRemainingWork=...
entry.N.optimizerProductionReadinessFirstBlockingStage=...
entry.N.optimizerProductionReadinessVerdict=...
entry.N.optimizerProductionReadinessProductionMutationEnabled=false
```

`optimizerProductionReadinessProductionMutationEnabled=false` is expected. IR validation is read-only; it reports evidence and blockers, but it does not apply rewrites.

## Check The Report Against The Example Tables

`IrAnalysisExamples` also exposes small text tables that explain what the report should mean:

```java
IrAnalysisExamples.renderOptimizerBlockerTable();
IrAnalysisExamples.renderProductionReadinessStageTable();
```

The test below verifies that those tables stay aligned with the generated report when IR validation is enabled:

```powershell
.\gradlew.bat `
  "-Pjavatogpu.enableIrValidationExamples=true" `
  "-Pjavatogpu.irValidationMode=diagnostic" `
  "-Pjavatogpu.irValidationDiagnostics=summary" `
  :examples-app:test `
  --tests "net.sixik.ga_utils.examples.IrAnalysisExamplesReportSyncTest"
```

If the report is not generated, this test is skipped. With `-Pjavatogpu.enableIrValidationExamples=true`, it asserts that the example table rows match the actual IR validation artifact.

## Try Strict Safety

After the diagnostic report is stable, try strict safety mode:

```powershell
.\gradlew.bat `
  "-Pjavatogpu.enableIrValidationExamples=true" `
  "-Pjavatogpu.irValidationMode=strictSafety" `
  "-Pjavatogpu.irValidationDiagnostics=summary" `
  :examples-app:compileJava
```

Use `strictSafety` when safety findings should fail the build. Do not start with `strictOptimizer` for normal app development; it is intentionally conservative and is better suited for compiler hardening or optimizer-readiness branches.

## What This Example Proves

- `javatogpu-ir-validation` is optional.
- Enabling it does not mutate IR.
- Diagnostics are tied to real `@GPU` methods in `examples-app`.
- The report gives a concrete bridge from source method to safety/optimizer-readiness status.
- Future optimizer modules should use a separate proposal/approval path instead of extending IR validation into write behavior.

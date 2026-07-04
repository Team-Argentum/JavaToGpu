# Validation And Operations

This page describes the validation flow for the current alpha.

## Current Operational Position

The active production-confidence path is NVIDIA OpenCL because that is the currently available real hardware stack.

Current local evidence proves the available NVIDIA path. Intel and AMD remain future cross-vendor promotion gates.

## Main Validation Buckets

- `:processor:benchmarkTest`
- `:processor:integrationOpenClSmokeTest`
- `:processor:openClLongRunningStabilityTest`
- `:processor:atomicsCompileTest`
- `:processor:compileOnlyTest`
- `:processor:imageOpenClTest`
- `:processor:localMemoryTest`
- `:processor:performanceStressTest`
- `:processor:runtimeOpenClTest`
- `:processor:structAbiTest`
- `:processor:openClVendorValidation`
- `:processor:openClWorkloadValidationTest`
- `:processor:openClValidationReport`

## Strict IR Validation

For compiler-development or CI builds, add the optional `javatogpu-ir-validation` artifact to the annotation-processor path and enable `-Ajavatogpu.irValidation=diagnostic`, `strictSafety`, or `strictOptimizer`. It runs additional lowered-IR validation before OpenCL emission and includes read-only CSE / auto-vectorization planning reports without rewriting IR. `-Ajavatogpu.irValidationDiagnostics=quiet|summary|detailed` controls javac note verbosity in diagnostic mode. `-Ajavatogpu.irValidationReport=reports/javatogpu-ir-validation.properties` writes a machine-readable CI artifact with safety/CSE/vectorization counters plus auto-vectorization vector-shape, rewrite-readiness, rewrite-plan, rewrite-blocked, rewrite-guard, warning-family, rejection-reason, first-blocking-diagnostic, and first-blocking-family fields. Strict modes fail the build on the configured validation boundary with detailed optimizer context.

See [IR Validation](IR-Validation.md) for setup, checks, planning modes, and current limitations.

## Recommended Full Routine

Run this before publishing release notes or claiming a fresh local validation point:

```powershell
.\gradlew.bat :processor:openClOperationalRoutine --rerun-tasks --console=plain
```

## Report Artifacts

The report bundle is written to:

```text
processor/build/reports/opencl/
```

Important files:

- `validation-report.md` - runtime/device snapshot and feature report.
- `validation-history.md` - rolling local validation history.
- `validation-history.properties` - machine-readable validation history.
- `bucket-status.properties` - per-bucket pass/fail/skip status.
- `workload-summary.properties` - serious workload equivalence summary.
- `long-running-summary.properties` - warm-session stability summary.

## What A Green Routine Proves

- The current compiler/runtime test buckets completed on the selected machine.
- OpenCL runtime selection and device capability reporting worked.
- Serious workload CPU-vs-GPU equivalence checks passed.
- Long-running warm-session reuse completed without detected ABI/resource regressions.
- Benchmark and stress buckets produced fresh evidence.

## What It Does Not Prove Yet

- Universal correctness across every OpenCL implementation.
- Intel or AMD behavior unless those devices were actually used.
- Stable public API compatibility before beta.
- Support for arbitrary Java bytecode or arbitrary Java application acceleration.

## Vendor Quirks

Record reproduced vendor-specific deviations in [Device Quirks](Device-Quirks.md). Keep quirks evidence-based and linked to report artifacts where possible.

## Runner Contract

For future self-hosted machines, see [OpenCL Runner Contract](OpenCL-Runner-Contract.md).

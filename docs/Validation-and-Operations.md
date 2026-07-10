# Validation And Operations

This page explains how to validate JavaToGpu locally, how to capture useful evidence, and what the current alpha validation status means.

## What To Run First

For normal development, run the processor tests:

```powershell
.\gradlew.bat :processor:test --console=plain
```

For real OpenCL validation on a GPU machine, run the operational routine:

```powershell
.\gradlew.bat :processor:openClOperationalRoutine --rerun-tasks --console=plain
```

The OpenCL reports are written under:

```text
processor/build/reports/opencl/
```

Start with `validation-report.md`. Keep the `.properties` files when debugging CI or comparing runs.

## Current Alpha Position

The current validated OpenCL baseline covers NVIDIA and AMD hardware.

This means:

- NVIDIA RTX 3060 and RTX 5070 validation lanes are green.
- AMD RX 7800 XT validation is green.
- Intel should still be validated on real hardware before making broad cross-vendor claims.
- A green local validation run proves the tested commit, machine, driver, and backend, not universal OpenCL behavior.

## Main Validation Buckets

The operational routine combines focused buckets for compile, runtime, ABI, images, local memory, stress, workloads, and report generation.

Important buckets include:

- `:processor:benchmarkTest`
- `:processor:integrationOpenClSmokeTest`
- `:processor:openClLongRunningStabilityTest`
- `:processor:compileOnlyTest`
- `:processor:imageOpenClTest`
- `:processor:localMemoryTest`
- `:processor:runtimeOpenClTest`
- `:processor:structAbiTest`
- `:processor:openClVendorValidation`
- `:processor:openClWorkloadValidationTest`
- `:processor:openClBackendSourcePromotionCandidateGate`
- `:processor:writeOpenClBackendSourcePromotionManifestTemplate`
- `:processor:validateOpenClBackendSourcePromotionManifest`
- `:processor:openClValidationReport`

You usually do not need to run buckets one by one unless you are narrowing down a failure.

## Reports To Keep

When a validation run fails, keep these artifacts if they exist:

```text
processor/build/reports/opencl/validation-report.md
processor/build/reports/opencl/validation-history.md
processor/build/reports/opencl/bucket-status.properties
processor/build/reports/opencl/workload-summary.properties
processor/build/reports/opencl/long-running-summary.properties
processor/build/reports/opencl/backend-source-promotion-gate.properties
processor/build/reports/opencl/backend-source-promotion-workload-gate.properties
processor/build/reports/opencl/production-source-switching-validation.properties
processor/build/reports/opencl/backend-source-promotion-candidate-gate.properties
processor/build/reports/opencl/backend-source-promotion-manifest-template.properties
processor/build/reports/opencl/backend-source-promotion-manifest-validation.properties
processor/build/test-results/
```

These files are more useful than a screenshot because they preserve bucket status, device details, and machine-readable failure state.

The candidate gate combines the real-workload gate with controlled source-switching acceptance for the same kernel resources and device identity. `review-ready` means the candidate evidence is complete; default production source switching and production mutation remain disabled.

Manual `workflow_dispatch` runs expose `production_promotion_manifest_mode=skip|template|validate`. Use `template` to archive a pending device-specific manifest bound to that run's `github.sha`. After approving and committing the manifest, use `validate` with `production_promotion_manifest_file`, the original SHA in `production_promotion_candidate_git_sha`, and a single matching `validation_lane`. Candidate SHA-256 and identity bindings prevent reuse for another GPU, driver, candidate artifact, or source state.

## Optional IR Validation

For stricter compiler diagnostics or CI evidence, add `javatogpu-ir-validation` and start with diagnostic mode:

```groovy
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs += '-Ajavatogpu.irValidation=diagnostic'
    options.compilerArgs += '-Ajavatogpu.irValidationDiagnostics=summary'
    options.compilerArgs += '-Ajavatogpu.irValidationReport=reports/javatogpu-ir-validation.properties'
}
```

Use `strictSafety` when CI should fail on safety diagnostics. Use `strictOptimizer` only for compiler hardening or optimizer-readiness experiments.

See [IR Validation](IR-Validation.md) for details.

## Vendor Validation

When adding or checking a self-hosted GPU runner, run the full operational routine on that machine and archive the OpenCL report directory.

Record:

- Vendor and device name.
- Driver/runtime version.
- Command used.
- Whether all buckets passed.
- Any confirmed device-specific failures.

If a failure reproduces only on one vendor stack, document it in [Device Quirks](Device-Quirks.md). Current green lanes include NVIDIA RTX 3060, NVIDIA RTX 5070, and AMD RX 7800 XT.

## What Green Validation Means

A green operational routine means the current repo, selected backend, driver, and hardware passed the current alpha evidence suite.

It does not prove:

- Every OpenCL implementation behaves the same.
- CUDA, Vulkan, or Metal support.
- Arbitrary Java bytecode support.
- Stable beta/production API compatibility.

## Practical Release Checklist

Before publishing or announcing a new alpha build:

1. Run `:processor:test`.
2. Run `:processor:openClOperationalRoutine --rerun-tasks` on at least one GPU machine.
3. Inspect `validation-report.md`.
4. Keep report artifacts for the release notes or CI logs.
5. Update [Device Quirks](Device-Quirks.md) if a vendor-specific issue is confirmed.

## Read Next

- [Troubleshooting](Troubleshooting.md)
- [IR Validation](IR-Validation.md)
- [Device Quirks](Device-Quirks.md)
- [OpenCL Runner Contract](OpenCL-Runner-Contract.md)

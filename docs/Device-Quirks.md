# Device Quirks

This page tracks known vendor-specific or driver-specific OpenCL behavior that affects JavaToGpu.

Record only issues that were reproduced on a real device stack or confirmed by repeated validation failures.

## Entry Template

- Vendor
- Device
- Driver / Runtime
- Platform
- Affected validation bucket
- Symptom
- Minimal reproducer
- Workaround
- Status: `open`, `mitigated`, or `driver-fixed`
- Matching validation artifact

## Current Status

No confirmed vendor-specific quirks are recorded in the public tracker yet.

Current production-confidence evidence is NVIDIA-only because Intel and AMD OpenCL hardware are not
available in the local validation setup yet. Intel and AMD remain cross-vendor promotion gates, not
current blockers for repo-local NVIDIA operational validation.

## Known Clean Baseline

- Vendor: `NVIDIA`
- Device: `NVIDIA GeForce RTX 5070`
- Driver / Runtime: `595.97`
- Platform: `OpenCL 3.0 CUDA 13.2.73`
- Validated command: `:processor:openClOperationalRoutine --rerun-tasks --console=plain`
- Validated buckets: `benchmarkTest`, `integrationOpenClSmokeTest`, `openClLongRunningStabilityTest`, `atomicsCompileTest`, `compileOnlyTest`, `imageOpenClTest`, `localMemoryTest`, `performanceStressTest`, `runtimeOpenClTest`, `structAbiTest`, `openClVendorValidation`, `openClWorkloadValidationTest`, `openClValidationReport`
- Latest result: all buckets passed at `2026-07-06T08:26:18Z`
- Workload coverage: Perlin, packed/blob, packed numeric, synthetic 3D packed-grid, and image workloads passed
- Long-running stability: 150 iterations, 600 invocations, 4 compiles, 596 compile-cache hits, 1 session
- Result: no vendor-specific issue observed across the repeated NVIDIA operational evidence runs
- Validation artifact: `processor/build/reports/opencl/validation-report.md`

## Pending Vendor Coverage

### Intel OpenCL

- Status: `pending-hardware`
- Reason: no Intel OpenCL card or stable runner is currently available
- Required before cross-vendor promotion: repeat the full operational routine, record bucket status, and add any confirmed quirks here

### AMD OpenCL

- Status: `pending-hardware`
- Reason: no AMD OpenCL card or stable runner is currently available
- Required before cross-vendor promotion: repeat the full operational routine, record bucket status, and add any confirmed quirks here

## Recording Rules

- Do not add generic "works on my machine" notes.
- Keep one issue as one clear entry.
- If a driver update fixes the issue, keep the history and mark it `driver-fixed`.

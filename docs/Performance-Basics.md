# Performance Basics

Use this page to decide whether a kernel is worth moving to JavaToGpu and how to read early performance results.

The short version: GPU execution usually pays off when you run the same simple parallel operation over enough data, especially when the OpenCL session and kernel cache are already warm. Tiny one-off calls can be slower than CPU code because startup, compilation, launch, and memory transfer have fixed costs.

## What Costs Time

| Cost | What It Means | How To Reduce It |
| --- | --- | --- |
| Cold startup | OpenCL platform/device discovery, context setup, and first compile. | Use `JavaToGpu.useOpenClSharedCache()` for repeated calls. |
| Kernel compile | OpenCL compiler turns generated source into a device program. | Reuse the shared cache and avoid changing compile options per call. |
| Runtime wrapper overhead | Full generated calls may re-enter descriptor selection, diagnostics, source-selection gates, and artifact decisions. | Use `JavaToGpu.prepare(...)` once, then call `GpuPreparedLauncher.invoke(...)` inside hot loops. |
| Launch overhead | Submitting a kernel has a fixed host-side cost. | Batch work into fewer, larger launches. |
| Marshalling | Java arrays/structs/vectors/images must be packed, uploaded, and read back. | Keep data layouts simple and avoid unnecessary readback. |
| Driver variance | Different vendors and driver versions optimize differently. | Validate on the target hardware and check `Device-Quirks.md`. |

## When GPU Execution Is A Good Fit

- Large arrays with one independent operation per element.
- Repeated calls to the same kernel in one process.
- Arithmetic-heavy kernels where transfer cost is small compared with compute.
- Struct/vector/image workflows that match JavaToGpu's supported data model.
- Workloads where approximate GPU math behavior is acceptable and explicitly tested.

## When CPU May Be Better

- Very small arrays or single-value calls.
- One-off calls that pay cold startup and compile cost once.
- Kernels that mostly move memory without much computation.
- Heavy branching, object-heavy Java code, exceptions, recursion, or virtual dispatch.
- Workloads that require unsupported data shapes or strict cross-device bit-identical output.

## Practical Runtime Pattern

For repeated calls, keep the OpenCL runtime and compile cache warm:

```java
try (GpuScope ignored = JavaToGpu.useOpenClSharedCache()) {
    MyKernel.step(input, output);
    MyKernel.step(input, output);
} finally {
    JavaToGpu.shutdownOpenClSharedCache();
}
```

For tight loops, prepare once and invoke the prepared handle:

```java
try (GpuScope ignored = JavaToGpu.useOpenClSharedCache()) {
    GpuPreparedLauncher launcher = JavaToGpu.prepare(MyKernel.class, "step", input, output);

    for (int i = 0; i < iterations; i++) {
        launcher.invoke(input, output);
    }
} finally {
    JavaToGpu.shutdownOpenClSharedCache();
}
```

Use `JavaToGpu.useOpenCl()` when you need a short one-off scope. Prefer the shared cache for application loops, services, demos, and performance checks. Prefer `GpuPreparedLauncher` when the same kernel is called repeatedly from a hot path. The lower-level `GpuRuntime` API remains available for advanced runtime configuration.

## How To Check Performance Locally

Start with correctness, then performance.

1. Run tiny inputs and compare with a CPU reference.
2. Run the curated OpenCL walkthrough:

```powershell
.\gradlew.bat :examples-app:runOpenClPracticalReleaseExample --console=plain
```

3. On a GPU validation machine, run the operational routine:

```powershell
.\gradlew.bat :processor:openClOperationalRoutine --rerun-tasks --console=plain
```

4. Read `processor/build/reports/opencl/validation-report.md` first.

## Rules Of Thumb

- Do not benchmark the first cold call as the steady-state result.
- Prefer warm-cache measurements for application-like workloads.
- For hot loops, compare normal generated calls against a prepared launcher before blaming OpenCL or the driver.
- Keep output buffers explicit so readback cost is visible.
- Test representative data sizes, not only the smallest example.
- Treat vendor/device results as facts for that machine, not universal claims.

## Read Next

- [User Quickstart](User-Quickstart.md) for the shortest runnable path.
- [Runtime Guide](Runtime-Guide.md) for runtime scopes, launch sizes, and artifacts.
- [Method Tests](Method-Tests.md) for fixture-based correctness checks.
- [Device Quirks](Device-Quirks.md) for known vendor/runtime notes.

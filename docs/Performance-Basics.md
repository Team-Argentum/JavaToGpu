# Performance Basics

Use this page to decide whether a kernel is worth moving to JavaToGpu and how to read early performance results.

The short version: GPU execution usually pays off when you run the same simple parallel operation over enough data, especially when the OpenCL session and kernel cache are already warm. Tiny one-off calls can be slower than CPU code because startup, compilation, launch, and memory transfer have fixed costs.

## What Costs Time

| Cost | What It Means | How To Reduce It |
| --- | --- | --- |
| Cold startup | OpenCL platform/device discovery, context setup, and first compile. | Use `JavaToGpu.useOpenClSharedCache()` for repeated calls. |
| Kernel compile | OpenCL compiler turns generated source into a device program. | Reuse the shared cache and avoid changing compile options per call. |
| Runtime wrapper overhead | Full generated calls may re-enter descriptor selection, diagnostics, source-selection gates, and artifact decisions. | Use `JavaToGpu.prepare(...)` once, then call `GpuPreparedLauncher.invoke(...)` inside hot loops. Reuse the same Java array objects when possible. |
| Launch overhead | Submitting a kernel has a fixed host-side cost. | Batch work into fewer, larger launches. |
| Marshalling | Java arrays/structs/vectors/images must be packed, uploaded, and read back. | Keep data layouts simple, avoid unnecessary readback, and freeze static `READ_ONLY` payload buffers with `withStaticArgumentNames(...)`. |
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

For the lowest current OpenCL overhead, keep buffer identity stable: allocate `input` and `output` once, change their
contents, and call the prepared launcher with those same array objects. Creating new arrays with the same length is
still supported, but it forces a safe binding rebuild before the kernel launch.
Reusing the same dynamic buffer objects keeps native device-buffer allocation stable. Upload and readback still run when
the descriptor requires them; use static payload arguments only for buffers that are genuinely immutable.

For output and scratch buffers, avoid unnecessary copies with explicit prepared-launcher transfer hints:

```java
GpuPreparedLauncher launcher = JavaToGpu.prepare(MyKernel.class, "step", input, output, scratch)
        .withoutHostUploadArgumentNames("output", "scratch")
        .withoutHostReadbackArgumentNames("scratch");
```

This keeps output readback enabled, but skips uploading old output contents before the kernel. Scratch becomes
device-only temporary storage for that prepared launcher.

When warm performance is still slower than expected, inspect the last prepared invoke instead of guessing:

Import `net.sixik.ga_utils.javatogpu.api.observability.GpuPreparedInvocationTimings` for the receipt type.

```java
launcher.invoke(input, output, scratch);

GpuPreparedInvocationTimings timings = launcher.lastInvocationTimings();
System.out.println(timings.artifactFields("jtg.hot"));
```

The fields split the host-side cost into buffer allocate/reuse, dynamic upload, skipped upload, argument binding,
kernel submit/wait/finish, and readback. If upload/readback dominates, focus on transfer policy or data layout. If
allocate is non-zero after warm-up, check whether Java array identity or shape is changing between calls.

For kernels with many constant payload buffers, prepare once with the full argument list and then derive a static-payload
launcher:

```java
GpuPreparedLauncher launcher = JavaToGpu.prepare(MyKernel.class, "step", coords, opcodes, arg0, value0, output)
        .withStaticArgumentNames("opcodes", "arg0", "value0");

launcher.invoke(coords, output);
```

Only freeze arguments that are truly immutable for the lifetime of the prepared handle. Writable outputs and scratch
buffers must stay dynamic.

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
- In prepared hot loops, reuse buffer objects instead of allocating replacement arrays each iteration.
- For large payload kernels, freeze unchanged `READ_ONLY` buffers so the hot loop only passes dynamic inputs/outputs.
- Keep output buffers explicit so readback cost is visible.
- Test representative data sizes, not only the smallest example.
- Treat vendor/device results as facts for that machine, not universal claims.

## Read Next

- [User Quickstart](User-Quickstart.md) for the shortest runnable path.
- [Runtime Guide](Runtime-Guide.md) for runtime scopes, launch sizes, and artifacts.
- [Method Tests](Method-Tests.md) for fixture-based correctness checks.
- [Device Quirks](Device-Quirks.md) for known vendor/runtime notes.

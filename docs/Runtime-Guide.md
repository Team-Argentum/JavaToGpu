# Runtime Guide

JavaToGpu runtime execution is controlled through `GpuRuntime`.

## Runtime Scopes

### Isolated OpenCL Backend

Use this for simple applications, tests, and one-off calls:

```java
try (GpuRuntimeScope ignored = GpuRuntime.useOpenCl()) {
    DemoKernel.transform(input, output);
}
```

### Shared OpenCL Cache

Use this for hot paths and repeated calls:

```java
try (GpuRuntimeScope ignored = GpuRuntime.useOpenClSharedCache()) {
    DemoKernel.transform(input, output);
    DemoKernel.transform(input, output);
} finally {
    GpuRuntime.shutdownOpenClSharedCache();
}
```

The shared cache keeps the OpenCL session and compiled kernels warm across calls.

## Runtime Selection

### Strict OpenCL

```java
try (GpuRuntimeScope ignored = GpuRuntime.useOpenClSharedCache()) {
    DemoKernel.transform(input, output);
}
```

This fails fast if OpenCL cannot be selected.

### Fallback Policy

```java
GpuRuntimeBackendPolicy policy = GpuRuntimeBackendPolicy.builder()
        .preferOpenClSharedCache()
        .preferFactory(MyCpuFallbackBackend::new)
        .build();

try (GpuRuntimeScope ignored = GpuRuntime.use(policy)) {
    DemoKernel.transform(input, output);
}
```

### Capability Precheck

```java
GpuRuntimeBackendPolicy policy = GpuRuntimeBackendPolicy.builder()
        .requireFeature(GpuBackendTarget.OPENCL, GpuRuntimeFeature.IMAGES)
        .preferOpenClSharedCache()
        .build();

GpuRuntimeSelectionResult result = GpuRuntime.trySelect(policy);
if (!result.matched()) {
    System.out.println("GPU path skipped: " + result.failureSummary());
    return;
}

try (GpuRuntimeScope ignored = result.install()) {
    DemoKernel.transform(input, output);
}
```

## Explicit Launch Sizes

Generated direct calls are convenient, but lower-level workloads sometimes need explicit launch sizes.

One-dimensional launch:

```java
GpuRuntime.invoke(
        GpuExecutionConfig.oneDimensional(itemCount),
        descriptor,
        input,
        output
);
```

Two-dimensional launch:

```java
GpuRuntime.invoke(
        GpuExecutionConfig.twoDimensional(width, height),
        descriptor,
        input,
        output
);
```

Three-dimensional launch:

```java
GpuRuntime.invoke(
        GpuExecutionConfig.threeDimensional(width, height, depth),
        descriptor,
        input,
        output
);
```

Explicit local sizes are also supported by the matching config factory overloads. After OpenCL compiles the selected kernel, JavaToGpu validates the total explicit local work-group size against that kernel's `CL_KERNEL_WORK_GROUP_SIZE` limit. For multidimensional launches, the validated size is the product of the local dimensions. An oversized explicit group fails before enqueue with `GpuRuntimeCapabilityException`. If no local size is specified, JavaToGpu leaves work-group selection to the OpenCL driver.

## Generated Launcher Helpers

For packed/blob workloads where logical item count does not match raw buffer length:

```java
GpuGeneratedLauncherInvoker.invokeWithGlobalWorkSize(
        OwnerClass.class,
        "kernel",
        itemCount,
        blob,
        view,
        output
);
```

For explicit multidimensional configs, use generated launcher config entry points or `GpuGeneratedLauncherInvoker.invokeWithConfig(...)` where applicable.

## Runtime Compile Options

Compile options are optional and keep the normal kernel arguments unchanged. Use them when you need backend-specific build flags or want to select a future runtime optimization profile explicitly.

```java
GpuRuntimeCompileOptions compileOptions = new GpuRuntimeCompileOptions(
        GpuBackendTarget.OPENCL,
        List.of("-cl-fast-relaxed-math"),
        "diagnostic"
);

GpuRuntime.invokeWithCompileOptions(
        GpuExecutionConfig.oneDimensional(itemCount),
        compileOptions,
        descriptor,
        input,
        output
);
```

Generated launchers expose the same path:

```java
OwnerClass_kernel_GpuLauncher.invokeWithConfigAndCompileOptions(
        GpuExecutionConfig.oneDimensional(itemCount),
        compileOptions,
        input,
        output
);
```

Reflection launcher helpers also support compile options for dynamically loaded/generated kernels:

```java
GpuGeneratedLauncherInvoker.invokeWithConfigAndCompileOptions(
        OwnerClass.class,
        "kernel",
        GpuExecutionConfig.oneDimensional(itemCount),
        compileOptions,
        input,
        output
);
```

The default optimization profile is `off`. Keep production code on `off` unless you are collecting diagnostics or testing an opt-in optimizer path.

OpenCL compile options are validated before the runtime touches the device. Supported options include common OpenCL build flags such as `-cl-fast-relaxed-math`, `-cl-mad-enable`, `-cl-opt-disable`, `-cl-std=...`, `-DNAME=VALUE`, and `-Ipath`. Backend-mismatched options fail early with a clear Java exception instead of being ignored by the runtime.

### Startup Device Self-Tests

OpenCL performs a bounded compile, enqueue, and readback correctness smoke before selecting among multiple discovered devices. Passed evidence contributes to deterministic ranking; failed correctness evidence rejects the device before JavaToGpu creates the production OpenCL context.

The default mode is `AUTO`: self-tests run when multiple device candidates are present and are skipped for a single candidate. Override this per invocation when startup latency or strict deployment validation matters:

```java
GpuRuntimeCompileOptions options = GpuRuntimeCompileOptions
        .defaults(GpuBackendTarget.OPENCL)
        .withDeviceSelfTestMode(GpuRuntimeDeviceSelfTestMode.REQUIRED);
```

- `AUTO` - run self-tests for multi-device discovery and reuse cached evidence.
- `DISABLED` - do not run or apply self-test evidence.
- `REQUIRED` - require passed evidence for every selectable candidate, including single-device systems.

Evidence is cached for the current process. The identity includes backend, device id/label/vendor, device class, unified-memory topology, driver version, runtime API version, self-test runner id/version, and JavaToGpu compiler identity. Changing any of those values invalidates the old entry.

After correctness passes, OpenCL performs one warm-up plus five measured compute and host/device round-trip samples. JavaToGpu discards the minimum and maximum sample and uses the median of the remaining three. The runtime selects a bounded workload profile from the detected device class:

| Device class | Profile | Compute workload | One-way transfer | Noise limit | Compute / transfer score caps |
| --- | --- | ---: | ---: | ---: | ---: |
| dGPU | `dgpu-balanced-v1` | 262,144 items x 128 iterations | 4 MiB | 300 permille | 3,000,000 / 1,000,000 |
| iGPU | `igpu-unified-v1` or `igpu-conservative-v1` | 131,072 items x 64 iterations | 2 MiB | 400 permille | 2,000,000 / 250,000 |
| CPU OpenCL | `cpu-opencl-conservative-v1` | 32,768 items x 32 iterations | 1 MiB | 500 permille | 500,000 / 100,000 |

An iGPU with unified memory uses the `unified-memory-round-trip` transfer model. Dedicated, integrated, unified, and host-memory rates are compared only with results from the same transfer model; compute rates are compared only inside the same workload profile. This prevents a small iGPU workload or shared-memory transfer path from being ranked directly against a dGPU-sized workload.

Compute and transfer stability are evaluated independently. Stable compute evidence may still affect ranking when transfer timing is noisy, and the reverse is also true. A noisy metric remains visible in selection artifacts but receives zero score. These measurements are a short startup ranking smoke, not a general GPU benchmark; real Intel/AMD iGPU validation, register-pressure stress, long-running stability, and persistent-cache policies remain roadmap work.

### Register-Pressure Analysis

The default runtime IR pipeline performs an advisory register-pressure analysis before transformation passes. It reads typed `IrGpu` method bodies and estimates the peak number of simultaneously required value slots from:

- kernel and helper parameters;
- local scalar and vector declarations;
- private arrays;
- nested expression evaluation pressure;
- branches, loops, and switch bodies.

The current `register-pressure-interprocedural:v4` model performs backward last-use analysis. Sequential locals are released after their final reference, unused parameters do not contribute to peak liveness, branch paths are merged at control-flow joins, and loops are iterated to a fixed point so loop-carried values remain live across the back-edge. Structured `break` and `continue` targets are modeled separately.

Typed `IrGpu` references currently store source names, so the analyzer first assigns a stable lexical identity to each parameter, local declaration, and private array. Nested declarations with the same source name remain separate live values. Unresolved references receive isolated conservative identities and add an advisory diagnostic instead of being merged with an unrelated declaration.

Helper calls are analyzed after all method-local estimates are available. A non-inline helper uses a separate frame, so caller and callee pressure are compared rather than blindly added. A helper marked inline may add its non-reusable frame pressure to values live at the call site. Source and emitted helper names are both resolved. Recursive call cycles are reported but are not expanded indefinitely, and unresolved helpers remain advisory edges.

The result uses `LOW`, `MODERATE`, `HIGH`, `CRITICAL`, or `UNAVAILABLE`. Device-class and vendor profiles provide conservative budgets, for example 64 value slots for the initial NVIDIA/AMD dGPU profile and 24 for the initial iGPU profile. These values are planning heuristics, not the physical register count reported by the GPU compiler. Driver compilers may allocate, merge, spill, or eliminate values differently.

High and critical estimates add optimizer diagnostics with the hottest method, estimated value-register count, advisory budget, and suggested reductions such as fewer simultaneously live temporaries, smaller private arrays, narrower vectors, or less unrolling. The analysis never changes the selected IR and never counts as accepted optimizer proof.

Artifact dumps store the complete result in `runtime-ir-analysis.properties`. Per-method fields include total parameter/local/private-array storage, `peakLiveRegisters`, expression-temporary peak, scoped-variable count, shadowed-variable count, unresolved-reference count, budget, utilization, level, and typed-node counts. Per-call fields include resolution state, inline/recursive flags, caller-live values, argument/result pressure, callee pressure, additional frame pressure, and combined estimate.

After a successful OpenCL program build, the runtime queries `CL_PROGRAM_BUILD_LOG` through the native program and device handles and stores any returned diagnostics in the compile snapshot. JavaToGpu then writes `backend-compiler-feedback.properties`. The built-in parser recognizes common NVIDIA/ptxas register and spill lines, AMD VGPR/SGPR/scratch fields, Intel/general register and spill fields, stack-frame bytes, local/shared-memory bytes, and occupancy percentages. SGPR and VGPR values remain separate; `effectiveRegisterCount` prefers a general register count, then VGPR, then SGPR, and never adds distinct register files together.

If both the heuristic estimate and a compiler register count exist, `runtime-ir-analysis.properties` adds the compiler provider, parsed metrics, compiler count, heuristic count, delta, and comparison status. This is calibration evidence only: it does not rewrite IR, change device selection, or enable production optimization. OpenCL permits an empty successful build log, and many drivers do not expose resource counts by default; a missing or unrecognized log leaves the heuristic analysis unchanged and marks compiler feedback unavailable. Failed build diagnostics remain available through `GpuRuntimeKernelCompilationException`.

The real-device `openClWorkloadValidationTest` also enables an isolated diagnostic rebuild after the production program has compiled. This second build cannot replace or invalidate the production program. NVIDIA devices use `-cl-nv-verbose` by default. AMD and other vendors remain fail-safe until a reviewed diagnostic option is supplied through `JTG_OPENCL_DIAGNOSTIC_COMPILE_ARGS` or the `javatogpu.opencl.compilerDiagnosticArgs` system property.

`backend-compiler-feedback.properties` exposes `diagnosticCompilation.present`, `status`, `source`, `options`, and `diagnostic`. Expected states are `recorded`, `completed-empty`, `failed`, and `skipped-no-options`. The last three states are evidence about diagnostic availability, not kernel execution failures.

JavaToGpu also queries standard kernel resource information after every successful OpenCL kernel creation. This path does not depend on vendor build-log behavior and records maximum work-group size, preferred work-group multiple, compiler-reported local memory, and compiler-reported private memory. Drivers may still report zero or reject individual queries; each metric remains independent. When the kernel maximum is available, it is enforced for explicit local launch sizes. Preferred multiples and memory values remain advisory.

The standard values appear in `backend-compiler-feedback.properties` as `selected.localMemoryBytes` plus raw fields `privateMemoryBytes`, `maxWorkGroupSize`, and `preferredWorkGroupSizeMultiple`. They provide useful spill/private-memory and launch-shape evidence but are not a replacement for exact SGPR/VGPR or NVIDIA register counts.

When runtime artifact dumping is enabled, each kernel directory also receives `runtime-launch-advisory.properties` for the latest accepted launch. Its status is `aligned`, `non-preferred-multiple`, `driver-selected`, or `unavailable`. The artifact records the requested local shape and total size, kernel maximum, preferred multiple, whether a comparison was performed, and whether it matched. `blocking=false` is invariant: a non-preferred multiple remains a performance diagnostic and never rejects the launch. A launch rejected by the hard kernel maximum clears any older advisory so stale accepted-launch evidence is not retained.

`openClValidationReport` aggregates only the kernels listed by the real-workload promotion gate into the `Kernel Launch Advisories` section. It reports status counts and a per-kernel table, and writes the same compact fragment to `runtime-launch-advisory-summary.md` for CI step summaries.

Validation history stores the aggregate counts plus a versioned encoded per-kernel snapshot in `kernelLaunchAdvisoryStatus`. The snapshot retains resource, status, local shape/size, kernel maximum, preferred multiple, match result, and blocking state. The Markdown history table still displays only the compact aggregate summary.

When a compatible previous entry exists for the same backend, device, vendor, and lane, the reporter adds `Kernel Launch Advisory Drift`. It compares counts and matches per-kernel snapshots by resource even across driver versions. Individual status degradation, blocking activation, preferred-match loss, kernel-maximum reduction, or a missing resource is a regression even when aggregate counts are unchanged. Preferred-multiple and launch-shape changes are reported as non-blocking changes. Baselines created before snapshot support continue through aggregate fallback. The CI Markdown includes the previous driver, signed deltas, and a per-kernel change table when snapshots are available.

The reporter also writes `runtime-launch-advisory-drift.properties`. `validateOpenClKernelLaunchAdvisoryDrift` accepts `stable`, `changed`, `improved`, `no-baseline`, and `unavailable`, but fails on `regressed`, a missing artifact, or an unsupported status. The vendor workflow captures artifacts and then fails the lane when this validator fails.

GitHub Actions restores an immutable per-lane/per-branch validation-history cache before the workload runs. The reporter seeds the current history from that baseline but always computes drift from the separate baseline file, so repeated report generation inside one workflow cannot hide a cross-run regression. After a successful validation and drift gate, the updated history is staged and saved under a unique run key for the next run; regressed or failed runs do not replace the previous baseline.

Manual `workflow_dispatch` runs can enable `launch_advisory_negative_fixture`. This mode requires a baseline containing per-kernel snapshots, increases one kernel maximum only in the restored baseline, and expects the normal drift validator to reject the resulting current-vs-baseline reduction. History staging and cache save are disabled unconditionally in this mode. The workflow succeeds only when fixture preparation succeeds, the drift step fails, and both cache steps remain skipped.

## Runtime Failures And Fallbacks

All structured runtime failures extend `GpuRuntimeException`. Use the base type when every GPU failure should take the same fallback path:

```java
try (GpuRuntimeScope ignored = GpuRuntime.useOpenClSharedCache()) {
    DemoKernel.transform(input, output);
} catch (GpuRuntimeException exception) {
    System.err.println(exception.diagnosticText());
    CpuFallback.transform(input, output);
}
```

Use a specific subtype when recovery differs by phase:

```java
try (GpuRuntimeScope ignored = GpuRuntime.useOpenClSharedCache()) {
    DemoKernel.transform(input, output);
} catch (GpuRuntimeDeviceSelectionException | GpuRuntimeMethodVariantSelectionException exception) {
    CpuFallback.transform(input, output);
} catch (GpuRuntimeKernelCompilationException exception) {
    reportBrokenKernel(exception.context(), exception.getCause());
    throw exception;
}
```

The public hierarchy includes:

- `GpuRuntimeDeviceSelectionException` - no compatible device or active-session mismatch.
- `GpuRuntimeMethodVariantSelectionException` - no compatible fallback method implementation.
- `GpuRuntimeBackendUnavailableException` - native backend/session initialization failed.
- `GpuRuntimeCompileOptionsException` - backend compile arguments are invalid.
- `GpuRuntimeInvocationException` - Java arguments or execution configuration do not match the kernel ABI.
- `GpuRuntimeCapabilityException` - the selected device lacks a required capability or resource budget.
- `GpuRuntimeKernelCompilationException` - driver/backend kernel build failed.
- `GpuRuntimeKernelExecutionException` - binding, enqueue, synchronization, or readback failed.

Every exception provides:

- `code()` - stable identifier such as `JTG-RUNTIME-COMPILE-001`.
- `phase()` - a `GpuRuntimeFailurePhase` value.
- `summary()` - concise failure description.
- `context()` - kernel, resource, backend, device, compile args, optimization profile, GPU method location, and original Java call site.
- `diagnosticText()` - pre-rendered Rust-like diagnostic suitable for logs.
- `getCause()` - the original backend/driver failure when one exists.

The annotation processor records calls to local and dependency-provided `@GPU` methods in `META-INF/javatogpu/call-sites/<binary-class-name>.properties`. At runtime, JavaToGpu matches the active caller stack frame against this index and prefers the exact original Java invocation expression in `diagnosticText()`. The `IrGpu` method source location remains available separately in `context().sourceLocation()`, while `context().callSite()` exposes the caller class/method, file, range, expression, target method, and whether the anchor came from the compiler index or stack-trace fallback.

Method-body rewriting preserves the caller's original exception table. A rewritten GPU call remains inside the same user-authored `try/catch` region, so `catch (GpuRuntimeException exception)` can reliably invoke a CPU, device, method-variant, or backend fallback.

### IrGpu Source Review Lane

`IrGpu` is the backend-neutral artifact that JavaToGpu is moving toward as the runtime source of truth. The normal runtime path still compiles the generated descriptor OpenCL source by default, even when an `IrGpu` artifact is packaged beside it.

Use the explicit review preset when you want to smoke-test reconstructed `IrGpu -> OpenCL` source without enabling production source switching:

```java
GpuRuntimeCompileOptions compileOptions =
        GpuRuntimeCompileOptions.openClIrGpuSourceReview(List.of());

GpuRuntime.invokeWithCompileOptions(
        GpuExecutionConfig.oneDimensional(itemCount),
        compileOptions,
        descriptor,
        input,
        output
);
```

The preset enables the reconstructed-source review mode. Production runs keep using the normal generated OpenCL source unless you explicitly opt into future source-switching features.

## ABI Debug

Enable ABI diagnostics with:

```java
System.setProperty("javatogpu.opencl.debugAbi", "true");
```

Use ABI debug when diagnosing struct layout, vector array, image, or readback issues.

## Related Documents

- [Validation and Operations](Validation-and-Operations.md)
- [OpenCL Data Model](OpenCL-Data-Model.md)
- [Troubleshooting](Troubleshooting.md)

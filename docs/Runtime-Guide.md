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

Explicit local sizes are also supported by the matching config factory overloads.

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

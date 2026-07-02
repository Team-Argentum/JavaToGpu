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

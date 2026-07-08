# Cookbook

Small copyable patterns for common JavaToGpu tasks.

## Run A Kernel With Shared OpenCL Cache

Use this for repeated calls:

```java
try (GpuRuntimeScope ignored = GpuRuntime.useOpenClSharedCache()) {
    DemoKernel.transform(input, output);
    DemoKernel.transform(input, output);
} finally {
    GpuRuntime.shutdownOpenClSharedCache();
}
```

## Run With An Explicit Launch Size

Use this when buffer length is not the logical work size:

```java
GpuRuntime.invoke(
        GpuExecutionConfig.oneDimensional(itemCount),
        descriptor,
        input,
        output
);
```

## Smoke-Test Reconstructed IrGpu Source

This is a review/smoke path. It does not enable production source switching.

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

## Inline Helper

Use `@CCode(inline = true)` for tiny helper functions:

```java
@CCode(inline = true)
static float lerp(float a, float b, float t) {
    return a + (b - a) * t;
}
```

## Mutable Helper Pointer

Use pointer wrappers for helper mutation patterns:

```java
@CCode
static void setValue(FloatPtr ptr) {
    ptr.value = 42.0f;
}
```

## Packed Blob Read

Use address-space pointer views when you need packed binary layouts:

```java
GlobalBytePtr root = GPU.global(blob);
int value = root.add(view.offset + id * 4).asIntPtr().value;
```

## Struct Value

Use `@GPUStruct` for simple ABI-marshalled values:

```java
@GPUStruct
static final class Point {
    public float x;
    public float y;
}
```

## Runtime Fallback Flow

Use `trySelect(...)` when CPU fallback or graceful skipping matters:

```java
GpuRuntimeSelectionResult result = GpuRuntime.trySelect(policy);
if (result.matched()) {
    try (GpuRuntimeScope ignored = result.install()) {
        DemoKernel.transform(input, output);
    }
} else {
    cpuFallback(input, output);
}
```

## Image Workflow

Create OpenCL image resources through the runtime backend and pass wrappers to the generated kernel:

```java
try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend();
     Image2DReadOnly input = backend.createReadOnlyRgbaIntImage(2, 1, pixels);
     Image2DWriteOnly output = backend.createWriteOnlyRgbaFloatImage(2, 1);
     Sampler sampler = backend.createNearestClampToEdgeSampler()) {
    ImageKernel.run(input, output, sampler, sums);
}
```

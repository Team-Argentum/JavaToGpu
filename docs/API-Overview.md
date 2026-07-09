# API Overview

This page gives a practical map of the JavaToGpu API for normal application code.

## Packages You Will Use

- `net.sixik.ga_utils.javatogpu.api` - kernel-facing helpers such as `GPU`, vectors, pointer views, image wrappers, samplers, and unsigned aliases.
- `net.sixik.ga_utils.javatogpu.api.annotations` - annotations for marking kernels, parameters, structs, helpers, intrinsics, attributes, and qualifiers.
- `net.sixik.ga_utils.javatogpu.runtime` - runtime scopes, backend selection, launch configs, descriptors, compile options, and invocation helpers.

## Kernel Annotations

Use these in source code that should compile to GPU code.

- `@GPU` marks a static Java method as a GPU kernel entry point.
- `@GPUGlobal`, `@GPUConstant`, and `@GPULocal` choose the OpenCL address space for array or pointer-like parameters.
- `@GPUWorkGroupSize` declares a portable required work-group size for the kernel.
- `@GPUOptimize` records method-level optimizer policy such as `fastMath`; the default remains strict.
- `@GPUStruct` marks a Java class as a value type that can be marshalled to OpenCL struct layout.
- `@CCode` marks a reusable helper method that should be emitted as GPU helper code.
- `@CCodeLibrary` groups reusable helper methods.
- `@GPUIntrinsic` maps a Java method to a backend intrinsic instead of a normal helper call.
- `@GPUAttribute` is the backend-aware raw escape hatch for metadata JavaToGpu does not model portably yet.
- `@OpenCLAttributes` and `@OpenCLQualifiers` remain OpenCL-only compatibility annotations for existing code.

Prefer portable annotations first. Use raw attributes only for backend-specific code that cannot be expressed through the normal API.

```java
@GPU
@GPUWorkGroupSize(x = 8, y = 8, z = 1)
@GPUOptimize(fastMath = false)
static void kernel(@GPUGlobal float[] output) {
    output[GPU.get_global_id(0)] = 1.0f;
}
```

If you need a backend-specific hint that JavaToGpu does not expose yet, use `@GPUAttribute` and declare the target explicitly:

```java
@GPUAttribute(backend = GpuBackendTarget.OPENCL, value = "vec_type_hint(float4)")
@GPU
static void openClOnlyKernel(@GPUGlobal float[] output) {
    output[GPU.get_global_id(0)] = 1.0f;
}
```

## `GPU.*` Builtins

Use `GPU.*` inside kernels for operations the compiler knows how to lower.

`GPU.*` is intentionally OpenCL-style. Even when CUDA, Vulkan/SPIR-V, or Metal lowerers are added, user code should keep the same `GPU` facade instead of switching to backend-specific Java dialects.

Common groups:

- Work-item indexing: `get_global_id`, `get_local_id`, `get_group_id`, `get_global_size`, `get_local_size`.
- Math: `sin`, `cos`, `tan`, `sqrt`, `pow`, `exp`, `log`, `clamp`, `mix`, `smoothstep`, and related helpers.
- Synchronization: `barrier` and memory fence constants.
- Images and samplers: image reads, writes, and metadata queries.
- Pointer/view bridging: `GPU.global(...)`, `GPU.constant(...)`, `GPU.local(...)`.

Prefer `GPU.*` over ordinary Java library calls inside kernels.

## Data Types

### Scalars

Supported scalar shapes include the common Java primitives used by the current subset: `byte`, `short`, `int`, `long`, `float`, `double`, and `char`.

Unsigned aliases include `UByte`, `UShort`, `UInt`, and `ULong`.

### Vectors

OpenCL-style vector wrapper families include:

- `Float2`, `Float3`, `Float4`
- `Int2`, `Int3`, `Int4`
- `UInt2`, `UInt3`, `UInt4`
- `Double2`, `Double3`, `Double4`

Vectors can be locals, helper parameters/returns, kernel parameters, and buffer element types where supported.

### Structs

Use `@GPUStruct` for small value objects with GPU-compatible fields.

Currently supported field categories:

- primitive scalar fields
- vector fields
- nested `@GPUStruct` fields

Arrays inside struct fields are not supported in the current alpha.

### Pointer Views

Pointer wrappers and address-space views are useful for low-level helpers and packed/blob data.

Examples:

- helper pointer wrappers: `FloatPtr`, `IntPtr`, `DoublePtr`
- address-space views: `GlobalBytePtr`, `GlobalIntPtr`, `GlobalFloatPtr`, `ConstantBytePtr`, `LocalFloatPtr`

Use these only when simple typed arrays are not enough.

### Images And Samplers

Image wrappers model OpenCL image parameters. Typical shapes include read-only images, write-only images, and `Sampler` arguments.

Use image APIs when you need OpenCL image memory, filtering, channel metadata, or pixel read/write operations.

## Runtime API

The usual runtime entry point is `GpuRuntime`.

Common calls:

- `GpuRuntime.useOpenCl()` for a simple scoped OpenCL runtime.
- `GpuRuntime.useOpenClSharedCache()` for repeated calls with a warm session and compile cache.
- `GpuRuntime.use(policy)` for custom fallback policies.
- `GpuRuntime.trySelect(policy)` for prechecking whether a GPU path is available.
- `GpuRuntime.invoke(...)` for descriptor-based direct invocation.
- `GpuExecutionConfig.oneDimensional(...)`, `twoDimensional(...)`, and `threeDimensional(...)` for explicit launch sizes.
- Generated launcher methods for normal `@GPU` calls.
- `GpuRuntimeCompileOptions.openClIrGpuSourceReview(...)` for opt-in reconstructed-`IrGpu` source smoke/review runs without changing the production default source path.

## Programmatic Compiler API

Use `GpuProgramCompiler` when another compiler or tool wants to call JavaToGpu directly.

Main entry points:

- `GpuProgramCompiler.createDefault()`
- `compileSource(...)`
- `compileStructuredAsm(...)`

Use normal Java source for application kernels. Use the structured ASM path only when you are building tooling that intentionally emits supported bytecode.

## Recommended Reading

- [Getting Started](Getting-Started.md)
- [Cookbook](Cookbook.md)
- [Runtime Guide](Runtime-Guide.md)
- [OpenCL Data Model](OpenCL-Data-Model.md)
- [Known Limitations](Known-Limitations.md)

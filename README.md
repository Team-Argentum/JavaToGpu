# JavaToGpu

JavaToGpu is a source-first Java-to-OpenCL pipeline with an additional structured ASM frontend for advanced integrations.

You write a restricted Java method, mark it with `@GPU`, and JavaToGpu validates it, lowers it into IR, emits OpenCL C, generates a launcher, and rewrites direct calls to execute through the runtime backend.

## Documentation

- Supported subset: [docs/supported-subset-contract.md](docs/supported-subset-contract.md)
- Runtime configuration: [docs/runtime-configuration.md](docs/runtime-configuration.md)
- Device requirements: [docs/device-requirements.md](docs/device-requirements.md)
- OpenCL validation matrix: [docs/opencl-validation-matrix.md](docs/opencl-validation-matrix.md)
- Fallback mode: [docs/fallback-mode.md](docs/fallback-mode.md)
- Known limitations: [docs/known-limitations.md](docs/known-limitations.md)
- OpenCL build debugging: [docs/debugging-opencl-build-failures.md](docs/debugging-opencl-build-failures.md)
- Diagnostics glossary: [docs/gpu-diagnostics-guide.md](docs/gpu-diagnostics-guide.md)

## What You Get

- Write kernels in Java instead of hand-writing OpenCL C.
- Built-in `GPU.*` intrinsics for indexing, math, atomics, barriers, images, and samplers.
- `@CCode` helpers for reusable GPU-side functions.
- Pointer wrappers like `FloatPtr` and `DoublePtr` for helper mutation patterns.
- Vector wrappers like `Float2`, `Float4`, `Int2`, `Double4`.
- `@GPUStruct` support for user-defined OpenCL structs.
- Kernel launcher generation and runtime dispatch through `GpuRuntime`.
- A public `GpuProgramCompiler` facade for both source and structured ASM inputs.

The public annotation package is `net.sixik.ga_utils.javatogpu.api.annotations`.

## Project Layout

- `processor`
  Compiler frontend, OpenCL emitter, runtime support, launcher generation, and bytecode rewriting.
- `test-app`
  Small sample application wired like a real consumer module.
- `examples-app`
  Showcase module with multiple focused example kernels.

## Current Status

Implemented and working:

- arithmetic, comparisons, logical operators
- casts
- `if / else`
- `for`, `while`, `do-while`
- `switch / case`
- compound assignments
- `++ / --`
- primitive arrays and scalars
- helper methods via `@CCode`
- inline helpers
- native helper bodies via `@CCode(code = "...")`
- pointer helpers
- vector local values, helper params / returns and kernel parameters
- `@GPUStruct`
- struct kernel parameters
- struct array buffers
- vector array buffers
- integer atomics
- local memory helper intrinsics
- image / sampler kernel code generation
- runtime image / sampler marshalling for real OpenCL handles
- `image1d_t`, `image1d_array_t`, `image1d_buffer_t`, `image2d_t`, `image2d_msaa_t`, `image2d_array_t`, `image3d_t` kernel parameters
- samplerless image reads and image metadata intrinsics
- host-side image upload / readback helpers for RGBA float/int/uint and RGBA8
- read/write coverage for float, int and uint image builtins across the supported image object families
- OpenCL attributes via `@OpenCLAttributes`
- OpenCL address spaces: `@GPUGlobal`, `@GPUConstant`, `@GPULocal`
- structured ASM frontend for canonical GPU-friendly bytecode

Still intentionally limited:

- non-`void` `@GPU` entry methods are not supported
- arbitrary Java object allocation is not supported
- arbitrary Java method calls are not supported
- CUDA backend is not implemented yet
- ASM frontend currently expects a strict GPU-friendly JVM subset rather than arbitrary bytecode

## Quick Start

Add the processor both as a dependency and as an annotation processor:

```groovy
dependencies {
    implementation project(':processor')
    annotationProcessor project(':processor')
}
```

To execute direct `@GPU` calls on the GPU at runtime, configure a backend:

```java
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntime;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeScope;

try (GpuRuntimeScope ignored = GpuRuntime.useOpenClSharedCache()) {
    Demo.kernel(input, output);
} finally {
    GpuRuntime.shutdownOpenClSharedCache();
}
```

## Runtime Scopes

Choose the runtime scope based on how often you call GPU kernels:

- `GpuRuntime.useOpenCl()`
  Good default for simple applications, short-lived tools, tests, or cases where you want an isolated backend instance.
- `GpuRuntime.useOpenClSharedCache()`
  Best choice for hot paths and repeated kernel calls. Compiled kernels and the OpenCL session stay warm across backend instances, so repeated launches avoid paying compile/setup cost again.

Simple isolated scope:

```java
try (GpuRuntimeScope ignored = GpuRuntime.useOpenCl()) {
    Demo.kernel(input, output);
}
```

Hot repeated-call scope with explicit shared-cache shutdown:

```java
try (GpuRuntimeScope ignored = GpuRuntime.useOpenClSharedCache()) {
    Demo.kernel(input, output);
    Demo.kernel(input, output);
    Demo.kernel(input, output);
} finally {
    GpuRuntime.shutdownOpenClSharedCache();
}
```

## Runtime Failure Modes

JavaToGpu now supports three practical runtime modes:

- `strict fail`
  Install one concrete backend and let unsupported environments fail immediately with a clear exception.
- `fallback chain`
  Build an ordered backend policy and let runtime pick the first compatible candidate.
- `capability precheck + skip`
  Probe a policy first, inspect the miss reason, and skip GPU execution without exception-driven control flow.

Strict fail:

```java
try (GpuRuntimeScope ignored = GpuRuntime.useOpenClSharedCache()) {
    Demo.kernel(input, output);
} finally {
    GpuRuntime.shutdownOpenClSharedCache();
}
```

Fallback chain:

```java
GpuRuntimeBackendPolicy policy = GpuRuntimeBackendPolicy.builder()
        .minimumApiVersion(GpuBackendTarget.OPENCL, 3, 0)
        .preferOpenClSharedCache()
        .preferFactory(MyCpuFallbackBackend::new)
        .build();

try (GpuRuntimeScope ignored = GpuRuntime.use(policy)) {
    Demo.kernel(input, output);
}
```

Capability precheck + skip:

```java
GpuRuntimeBackendPolicy policy = GpuRuntimeBackendPolicy.builder()
        .requireFeature(GpuBackendTarget.OPENCL, GpuRuntimeFeature.IMAGES)
        .preferOpenClSharedCache()
        .build();

GpuRuntimeSelectionResult result = GpuRuntime.trySelect(policy);
if (!result.matched()) {
    System.out.println("GPU path skipped: " + result.failureSummary());
} else {
    try (GpuRuntimeScope ignored = result.install()) {
        Demo.kernel(input, output);
    }
}
```

## Basic Kernel Example

```java
import net.sixik.ga_utils.javatogpu.api.GPU;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

public final class Demo {

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void saxpy(
            @GPUGlobal float[] input,
            @GPUGlobal float[] output
    ) {
        int id = GPU.get_global_id(0);
        output[id] = GPU.sin(input[id]) + 2.0f;
    }
}
```

Conceptually this becomes something like:

```c
__kernel void jtg_kernel(__global float* input, __global float* output) {
    int id = get_global_id(0);
    output[id] = sin(input[id]) + 2.0f;
}
```

More complete production notes now live in the local docs set listed above, with the subset contract, runtime configuration, fallback guidance, limitations, and OpenCL debugging notes split into dedicated pages.

## Programmatic Frontends

If you want to use JavaToGpu as a backend from another compiler, use `GpuProgramCompiler`.

```java
GpuProgramCompiler compiler = GpuProgramCompiler.createDefault();
String sourceOpencl = compiler.compileSource(methodSource, helperSources);
String asmOpencl = compiler.compileStructuredAsm(kernelAsmMethod, helperAsmMethods, structs);
```

Important note:

- the ASM path is for GPU-friendly bytecode generated on purpose
- it is not meant to decompile arbitrary JVM methods back into kernels
- the recommended architecture is `your AST -> GPU-friendly ASM -> JavaToGpu ASM frontend -> IR -> OpenCL`

## Build

```powershell
./gradlew.bat clean test --console=plain
./gradlew.bat :test-app:run --console=plain
```

## Limitations And Design Notes

JavaToGpu is intentionally not a "run any Java on GPU" system.

It is a restricted Java DSL for GPU-safe code generation.

That means:

- explicit support is better than implicit magic
- unsupported constructs should fail fast at compile time
- correctness and understandable diagnostics matter more than pretending the whole language is available

For the ASM path, the same philosophy applies even more strictly: generate a canonical subset that is easy to validate and lift, instead of trying to support arbitrary JVM patterns.

## Roadmap

High-value next areas:

- richer complex-type marshalling and ABI formalization
- broader OpenCL surface area
- continued reusable-library hardening
- CUDA backend later

## License

See [LICENSE](LICENSE).

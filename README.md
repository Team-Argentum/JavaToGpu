# JavaToGpu

JavaToGpu is a source-first Java-to-OpenCL pipeline with an additional structured ASM frontend for advanced integrations.

You write a restricted Java method, mark it with `@GPU`, and JavaToGpu validates it, lowers it into IR, emits OpenCL C, generates a launcher, and rewrites direct calls to execute through the runtime backend.

## Documentation

- Public user documentation: [github-wiki](https://github.com/DeusSixik/JavaToGpu/wiki)

## What You Get

- Write kernels in Java instead of hand-writing OpenCL C.
- Built-in `GPU.*` intrinsics for indexing, math, atomics, barriers, images, and samplers.
- `@CCode` helpers for reusable GPU-side functions.
- Pointer wrappers like `FloatPtr` and `DoublePtr` for helper mutation patterns.
- Vector wrappers like `Float2`, `Float4`, `Int2`, `Double4`.
- `@GPUStruct` support for user-defined OpenCL structs.
- Kernel launcher generation and runtime dispatch through `GpuRuntime`.
- A public `GpuProgramCompiler` facade for both source and structured ASM inputs.

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
- `image1d_t`, `image1d_array_t`, `image1d_buffer_t`, `image2d_t`, `image2d_array_t`, `image3d_t` kernel parameters
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
import net.sixik.ga_utils.javatogpu.runtime.opencl.OpenClGpuRuntimeBackend;

try (OpenClGpuRuntimeBackend backend = new OpenClGpuRuntimeBackend()) {
    GpuRuntime.setBackend(backend);
    Demo.kernel(input, output);
} finally {
    GpuRuntime.setBackend(GpuRuntime.defaultBackend());
}
```

## Basic Kernel Example

```java
import net.sixik.ga_utils.javatogpu.api.GPU;
import net.sixik.ga_utils.javatogpu.api.anotations.GPUGlobal;

public final class Demo {

    @net.sixik.ga_utils.javatogpu.api.anotations.GPU
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

More complete examples, troubleshooting, ASM notes, and feature pages now live in the public wiki: [github-wiki](https://github.com/DeusSixik/JavaToGpu/wiki).

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

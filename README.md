# JavaToGpu

JavaToGpu is a source-first Java-to-OpenCL pipeline.

You write a restricted Java method, mark it with `@GPU`, and during compilation the project:

1. parses the Java source,
2. validates it against the supported GPU subset,
3. lowers it to an internal IR,
4. emits OpenCL C,
5. generates a launcher,
6. rewrites direct `@GPU` calls to go through the runtime backend.

Right now the main backend is OpenCL. CUDA is planned, but not the current focus.

## What You Get

- Write kernels in Java instead of hand-writing OpenCL C.
- Built-in `GPU.*` intrinsics for indexing, math and barriers.
- `@CCode` helpers for reusable GPU-side functions.
- Pointer wrappers like `FloatPtr` and `DoublePtr` for helper mutation patterns.
- Vector wrappers like `Float2`, `Float4`, `Int2`, `Double4`.
- `@GPUStruct` support for user-defined OpenCL structs.
- Kernel launcher generation and runtime dispatch through `GpuRuntime`.

## Project Layout

- `processor`
  The compiler frontend, emitter, runtime support and bytecode rewriter.
- `test-app`
  A small sample application that uses the processor as both `implementation` and `annotationProcessor`.
- `docs`
  Internal design notes, specs and implementation plans.

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
- OpenCL attributes via `@OpenCLAttributes`
- OpenCL address spaces: `@GPUGlobal`, `@GPUConstant`, `@GPULocal`

Still intentionally limited:

- non-`void` `@GPU` entry methods are not supported
- arbitrary Java object allocation is not supported
- arbitrary Java method calls are not supported
- CUDA backend is not implemented yet

## Quick Start

In a consumer module, add the processor both as a dependency and as an annotation processor.

```groovy
dependencies {
    implementation project(':processor')
    annotationProcessor project(':processor')
}
```

If you want direct `@GPU` method invocation to execute on the GPU at runtime, configure a backend:

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

## `@CCode` Helpers

Use `@CCode` when you want reusable GPU-side helper logic.

```java
import net.sixik.ga_utils.javatogpu.api.anotations.CCode;

public final class GpuUtils {

    @CCode(inline = true)
    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
```

Call it from a kernel:

```java
@net.sixik.ga_utils.javatogpu.api.anotations.GPU
static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
    int id = GPU.get_global_id(0);
    output[id] = GpuUtils.lerp(input[id], input[id] * 2.0f, 0.5f);
}
```

You can also provide raw backend code:

```java
@CCode(code = """
        return (*ptr) * scale;
        """)
public static native float scaled(FloatPtr ptr, float scale);
```

## Pointer Wrappers

Pointer wrappers are useful when a helper needs to mutate a scalar by reference.

```java
import net.sixik.ga_utils.javatogpu.api.FloatPtr;

public final class GpuUtils {

    @CCode
    public static void fill(FloatPtr ptr) {
        ptr.value = 42.0f;
    }
}

@net.sixik.ga_utils.javatogpu.api.anotations.GPU
static void kernel(@GPUGlobal float[] output) {
    FloatPtr ptr = new FloatPtr();
    GpuUtils.fill(ptr);
    output[0] = ptr.value;
}
```

Available pointer wrappers:

- `BytePtr`
- `CharPtr`
- `ShortPtr`
- `IntPtr`
- `LongPtr`
- `FloatPtr`
- `DoublePtr`

## Vector Types

JavaToGpu includes Java-side wrappers for OpenCL vector types.

Available vector types:

- `Float2`, `Float3`, `Float4`
- `Int2`, `Int3`, `Int4`
- `Long2`, `Long3`, `Long4`
- `Double2`, `Double3`, `Double4`

Example:

```java
import net.sixik.ga_utils.javatogpu.api.Float2;

public final class GpuUtils {

    @CCode
    public static Float2 add(Float2 left, Float2 right) {
        return new Float2(left.x + right.x, left.y + right.y);
    }
}

@net.sixik.ga_utils.javatogpu.api.anotations.GPU
static void kernel(@GPUGlobal float[] input, @GPUGlobal float[] output) {
    int id = GPU.get_global_id(0);
    Float2 a = new Float2(input[id], input[id] * 2.0f);
    Float2 b = new Float2(1.0f);
    Float2 c = GpuUtils.add(a, b);
    output[id] = c.x + c.y;
}
```

Notes:

- vector locals are supported
- vector helper params and returns are supported
- vector kernel parameters are supported
- `@GPUGlobal Float2[]` style vector buffers are supported

## `@GPUStruct`

Use `@GPUStruct` for OpenCL struct-like data.

Important rule:

- put scalar fields, vector fields, or other `@GPUStruct` fields inside a struct
- do not use reserved variable names like `struct` in GPU code

Example:

```java
import net.sixik.ga_utils.javatogpu.api.anotations.GPUStruct;

@GPUStruct
public static class Vec2 {
    public double x;
    public double y;

    public Vec2() {
    }

    public Vec2(double x, double y) {
        this.x = x;
        this.y = y;
    }
}

@GPUStruct
public static class Sample {
    public Vec2 point;
    public double bias;
    public int count;

    public Sample() {
    }

    public Sample(Vec2 point, double bias, int count) {
        this.point = point;
        this.bias = bias;
        this.count = count;
    }
}
```

Usage inside a kernel:

```java
@net.sixik.ga_utils.javatogpu.api.anotations.GPU
static void kernel(@GPUGlobal double[] input, @GPUGlobal double[] output) {
    int id = GPU.get_global_id(0);
    Vec2 point = new Vec2(input[id], input[id] * 2.0);
    Sample sample = new Sample(point, 20.5, 10);
    output[id] = sample.point.x + sample.point.y + sample.bias + sample.count;
}
```

Notes:

- nested structs are supported
- struct constants are supported
- struct kernel parameters are supported
- `@GPUGlobal Sample[]` style struct buffers are supported

## OpenCL Address Spaces

Kernel array parameters can be mapped to OpenCL address spaces:

```java
@net.sixik.ga_utils.javatogpu.api.anotations.GPU
static void kernel(
        @GPUGlobal float[] input,
        @GPUConstant float[] lookup,
        @GPULocal float[] scratch,
        @GPUGlobal float[] output
) {
    int id = GPU.get_global_id(0);
    int lid = GPU.get_local_id(0);

    scratch[lid] = input[id] + lookup[lid];
    GPU.barrier(GPU.CLK_LOCAL_MEM_FENCE);
    output[id] = scratch[lid];
}
```

Mappings:

- `@GPUGlobal` -> `__global`
- `@GPUGlobal(constant = true)` -> `__global const`
- `@GPUConstant` -> `__constant`
- `@GPULocal` -> `__local`

## OpenCL Attributes

If you need backend-specific attributes, use `@OpenCLAttributes`.

```java
import net.sixik.ga_utils.javatogpu.api.anotations.OpenCLAttributes;

@OpenCLAttributes({"reqd_work_group_size(16, 1, 1)"})
@net.sixik.ga_utils.javatogpu.api.anotations.GPU
static void kernel(@GPUGlobal float[] output) {
    output[0] = 1.0f;
}
```

## Reusable Libraries

When helpers or intrinsics should be reused across compilations, annotate their owner classes:

- `@CCodeLibrary`
- `@GPUIntrinsicLibrary`

This makes the processor export metadata that another compilation can load from the classpath.

## How Runtime Dispatch Works

At build time, the processor generates:

- OpenCL kernel source
- a Java launcher class with argument descriptors
- bytecode rewrites for direct `@GPU` calls in compiled classes

At runtime:

1. your Java code calls the original `@GPU` method,
2. the rewritten body forwards into the generated launcher,
3. the launcher delegates to `GpuRuntime`,
4. the selected backend compiles, caches and runs the kernel.

## Build And Test

Run the full build:

```powershell
./gradlew.bat clean test --console=plain
```

Run only the sample app:

```powershell
./gradlew.bat :test-app:run --console=plain
```

On Unix-like systems, use `./gradlew` instead of `./gradlew.bat`.

## Troubleshooting

### `OpenCL program build failed`

This means JavaToGpu successfully generated kernel source, but the OpenCL compiler rejected it.

What to check:

- inspect the generated `.cl` resource under `build/generated/sources/annotationProcessor/java/main/javatogpu/...`
- compare the emitted code with the supported patterns shown in `test-app` and `processor/src/test`
- verify that the Java method stays inside the current GPU subset and does not rely on unsupported object semantics

### `Failed to compile @GPU method`

This is a compile-time frontend validation error.

What to do:

- read the full compiler diagnostic first, because the processor is intentionally fail-fast
- if the issue looks layout-related, enable processor ABI notes with `-Ajavatogpu.debugAbi=true`
- check helper calls, struct fields, vector types and address-space annotations against the examples in this README and the tests

With ABI debug enabled, the annotation processor prints extra notes describing how kernel parameters and struct fields are interpreted.

### `GPU execution failed`

This usually means runtime setup or argument marshalling failed after compilation.

What to check:

- make sure your application configures a real backend such as `new OpenClGpuRuntimeBackend()` through `GpuRuntime.setBackend(...)`
- enable runtime ABI diagnostics with `-Djavatogpu.opencl.debugAbi=true`
- if the failure involves `@GPUStruct`, vectors or packed data, compare the Java-side layout with the emitted ABI dump

With runtime ABI debug enabled, the backend prints parameter layout and marshalling details to `stderr` before launch.

### `rewriteGpuMethods` fails after `clean`

The sample app already wires this correctly:

- `rewriteGpuMethods` depends on `:processor:classes`
- `classes` depends on `rewriteGpuMethods`

If you copy the setup into another module, keep that dependency order. See [`test-app/build.gradle`](test-app/build.gradle).

### Windows file-lock issues during Gradle runs

On Windows, `clean test` can fail intermittently if Gradle or the OpenCL toolchain still holds files such as test result binaries or jars open.

Practical workarounds:

- avoid running multiple Gradle commands in parallel against the same workspace
- rerun with `./gradlew.bat --no-daemon test --rerun-tasks --console=plain`
- if `clean` is the only failing part, retry without it first to confirm the code itself is fine

## Sample Code

The best real project examples live here:

- [Main.java](test-app/src/main/java/net/sixik/ga_utils/Main.java)
- `test-app/src/main/java/net/sixik/ga_utils/*`
- `processor/src/test/java/net/sixik/ga_utils/javatogpu/*`

The test suite is especially useful because it documents exactly what the current frontend supports.

## Limitations And Design Notes

JavaToGpu is intentionally not a "run any Java on GPU" system.

It is a restricted Java DSL for GPU-safe code generation.

That means:

- explicit support is better than implicit magic
- unsupported constructs should fail fast at compile time
- correctness and understandable diagnostics matter more than pretending the whole language is available

## Roadmap

High-value next areas:

- richer complex-type marshalling and ABI formalization
- broader OpenCL surface area
- continued reusable-library hardening
- CUDA backend

## License

See [LICENSE](LICENSE).

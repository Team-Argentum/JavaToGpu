# Getting Started

This guide shows the shortest path from a Java method to an OpenCL-backed GPU call.

## Requirements

- JDK compatible with this Gradle build.
- A working OpenCL runtime for GPU execution.
- For the current alpha evidence path, an NVIDIA OpenCL stack is the locally validated target.

JavaToGpu can still compile and run many tests without a real GPU, but runtime validation requires OpenCL hardware and drivers.

## Add The Processor

Add the processor as both a dependency and an annotation processor:

```groovy
dependencies {
    implementation 'io.github.deussixik:javatogpu:0.1.0-alpha.1'
    annotationProcessor 'io.github.deussixik:javatogpu:0.1.0-alpha.1'
}
```

Optional strict IR validation can be enabled by adding:

```groovy
dependencies {
    annotationProcessor 'io.github.deussixik:javatogpu-ir-validation:0.1.0-alpha.1'
}

tasks.withType(JavaCompile).configureEach {
    options.compilerArgs += '-Ajavatogpu.irValidation=diagnostic'
}
```

The extra artifact provides lowered-IR validation and read-only optimizer diagnostics, while the compiler option chooses how aggressively builds should react. See [IR Validation](IR-Validation.md) for details about `diagnostic`, `strictSafety`, and `strictOptimizer` modes.

## Write A Kernel

```java
import net.sixik.ga_utils.javatogpu.api.GPU;
import net.sixik.ga_utils.javatogpu.api.annotations.GPUGlobal;

public final class DemoKernel {

    @net.sixik.ga_utils.javatogpu.api.annotations.GPU
    public static void transform(
            @GPUGlobal float[] input,
            @GPUGlobal float[] output
    ) {
        int id = GPU.get_global_id(0);
        output[id] = GPU.sin(input[id]) + 2.0f;
    }
}
```

Important rules:

- `@GPU` entry methods currently return `void`.
- Results should be written into output arrays or other supported output parameters.
- Kernel parameters need explicit GPU-facing shapes such as `@GPUGlobal float[]`.
- Use `GPU.*` for OpenCL-style builtins instead of arbitrary Java library calls.

## Run A Kernel

Use a runtime scope around calls that should execute through the generated launcher:

```java
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntime;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeScope;

try (GpuRuntimeScope ignored = GpuRuntime.useOpenCl()) {
    DemoKernel.transform(input, output);
}
```

For hot paths and repeated calls, prefer the shared cache:

```java
try (GpuRuntimeScope ignored = GpuRuntime.useOpenClSharedCache()) {
    DemoKernel.transform(input, output);
    DemoKernel.transform(input, output);
} finally {
    GpuRuntime.shutdownOpenClSharedCache();
}
```

## Validate Locally

Run the normal processor tests:

```powershell
.\gradlew.bat :processor:test --console=plain
```

Run the full OpenCL operational routine on a GPU machine:

```powershell
.\gradlew.bat :processor:openClOperationalRoutine --rerun-tasks --console=plain
```

OpenCL reports are written to:

```text
processor/build/reports/opencl/
```

## Read Next

- [API Overview](API-Overview.md)
- [Language Contract](Language-Contract.md)
- [Runtime Guide](Runtime-Guide.md)
- [IR Validation](IR-Validation.md)
- [Validation and Operations](Validation-and-Operations.md)

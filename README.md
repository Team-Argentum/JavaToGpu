# JavaToGpu

JavaToGpu lets you write a restricted Java method, mark it as a GPU kernel, and run it through an OpenCL runtime.

It is currently a public alpha / developer preview. Use it for experiments, examples, compiler/runtime integration, and early GPU-kernel prototyping. Do not treat the API or generated launcher shape as stable before beta.

## What You Can Do Today

- Write `@GPU` Java kernels over arrays, scalars, vectors, structs, pointers, images, and samplers.
- Use `GPU.*` builtins for OpenCL-style indexing, math, barriers, images, atomics, and low-level helpers.
- Run kernels through `GpuRuntime.useOpenCl()` or `GpuRuntime.useOpenClSharedCache()`.
- Pass explicit launch sizes and OpenCL compile options when needed.
- Add optional IR validation for stricter diagnostics and CI reports.
- Preflight intentionally generated ASM/bytecode artifacts for advanced compiler integrations.

JavaToGpu is not a "run any Java app on the GPU" system. GPU methods must stay inside the supported kernel subset.

## Install

Add JavaToGpu as both a dependency and an annotation processor:

```groovy
dependencies {
    implementation 'io.github.deussixik:javatogpu:0.1.0-alpha.1'
    annotationProcessor 'io.github.deussixik:javatogpu:0.1.0-alpha.1'
}
```

Optional stricter IR validation:

```groovy
dependencies {
    annotationProcessor 'io.github.deussixik:javatogpu-ir-validation:0.1.0-alpha.1'
}

tasks.withType(JavaCompile).configureEach {
    options.compilerArgs += '-Ajavatogpu.irValidation=diagnostic'
    options.compilerArgs += '-Ajavatogpu.irValidationDiagnostics=summary'
    options.compilerArgs += '-Ajavatogpu.irValidationReport=reports/javatogpu-ir-validation.properties'
}
```

Start with `diagnostic` mode. Move to stricter modes only when you want CI to fail on validation diagnostics.

## First Kernel

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

Run it through the OpenCL runtime:

```java
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntime;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeScope;

try (GpuRuntimeScope ignored = GpuRuntime.useOpenClSharedCache()) {
    DemoKernel.transform(input, output);
} finally {
    GpuRuntime.shutdownOpenClSharedCache();
}
```

Use `GpuRuntime.useOpenCl()` for one-off calls. Use `GpuRuntime.useOpenClSharedCache()` for repeated calls so the OpenCL session and compile cache stay warm.

## Important Alpha Limits

- `@GPU` entry methods return `void`; write results to output buffers.
- General object allocation, virtual dispatch, exceptions, recursion, monitors, and heap object graphs are not supported inside kernels.
- Arrays inside `@GPUStruct` fields are not supported in the current alpha.
- OpenCL is the active backend today. CUDA, Vulkan, and Metal are future directions.
- NVIDIA OpenCL is the current strongest validation baseline; AMD and Intel should be validated on real hardware before cross-vendor claims.

See [Known Limitations](docs/Known-Limitations.md) before using JavaToGpu in a larger project.

## Documentation

Start here:

- [Docs Home](docs/Home.md)
- [Getting Started](docs/Getting-Started.md)
- [Cookbook](docs/Cookbook.md)
- [Runtime Guide](docs/Runtime-Guide.md)
- [API Overview](docs/API-Overview.md)
- [Known Limitations](docs/Known-Limitations.md)
- [Troubleshooting](docs/Troubleshooting.md)
- [FAQ](docs/FAQ.md)

Advanced topics:

- [Language Contract](docs/Language-Contract.md)
- [OpenCL Data Model](docs/OpenCL-Data-Model.md)
- [IR Validation](docs/IR-Validation.md)
- [Validation and Operations](docs/Validation-and-Operations.md)
- [Diagnostics Reference](docs/Diagnostics-Reference.md)
- [ASM Contract](docs/ASM-Contract.md)
- [Device Quirks](docs/Device-Quirks.md)
- [OpenCL Runner Contract](docs/OpenCL-Runner-Contract.md)
- [Publishing Guide](docs/Publishing.md)

Maintainer planning notes live outside the public user manual.

## Build And Validate

Run the normal processor tests:

```powershell
.\gradlew.bat :processor:test --console=plain
```

Run real OpenCL validation on a GPU machine:

```powershell
.\gradlew.bat :processor:openClOperationalRoutine --rerun-tasks --console=plain
```

OpenCL reports are written under:

```text
processor/build/reports/opencl/
```

Start with `validation-report.md` when checking a run.

## Project Layout

- `processor` - annotation processor, compiler, OpenCL emitter, runtime, launchers, tests, and validation buckets.
- `ir-validation` - optional stricter IR validation module.
- `examples-app` - example kernels and usage patterns.
- `test-app` - consumer-style sample application.
- `docs` - public documentation.

## Publishing

Published artifacts:

```text
io.github.deussixik:javatogpu
io.github.deussixik:javatogpu-ir-validation
```

Publishing is configured for the main processor artifact and the optional IR validation artifact. Keep Maven Central credentials and signing keys outside the repository. See [Publishing Guide](docs/Publishing.md).

## License

See [LICENSE](LICENSE).

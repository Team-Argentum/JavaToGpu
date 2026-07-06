# JavaToGpu

JavaToGpu is an experimental Java-to-OpenCL compiler and runtime for writing GPU kernels in a restricted Java subset.

You mark GPU entry points with `@GPU`, JavaToGpu validates the supported subset, lowers it to an internal IR, emits OpenCL C, generates launchers, and dispatches through the runtime backend.

## Status

JavaToGpu is ready for a public alpha / developer-preview release.

Use it when you want to experiment with GPU-safe Java kernels, OpenCL code generation, runtime validation, and compiler integration. Do not treat the API or generated-code shape as stable yet.

Current validation baseline:

- Backend: OpenCL
- Proven local device: NVIDIA GeForce RTX 5070 through the NVIDIA CUDA OpenCL stack
- Current confidence signal: NVIDIA is operationally proven for repo-local alpha validation with five full `:processor:openClOperationalRoutine` evidence runs, including four explicit `--rerun-tasks` passes
- Future promotion gates: Intel and AMD OpenCL validation on real hardware
- CUDA backend: planned, not implemented

## What Works Today

- Java source kernels with `@GPU` entry methods.
- OpenCL-style `GPU.*` intrinsics for indexing, math, conversion, atomics, barriers, images, samplers, and low-level helpers.
- Helper functions through `@CCode`, reusable helper libraries through `@CCodeLibrary`, and explicit backend intrinsic bindings through `@GPUIntrinsic`.
- Primitive arrays, scalars, vector wrappers, unsigned aliases, pointer wrappers, address-space pointer views, and `@GPUStruct` values.
- OpenCL address spaces through `@GPUGlobal`, `@GPUConstant`, and `@GPULocal`.
- OpenCL attributes and low-level qualifiers for explicit kernel/data-model work.
- Runtime dispatch through `GpuRuntime`, shared OpenCL cache scopes, fallback selection, capability prechecks, and explicit 1D/2D/3D launch configuration.
- Image and sampler kernel APIs with practical host-side OpenCL image workflows.
- Structured ASM compiler entry point for intentionally generated canonical bytecode.
- Repo-local validation buckets for compile-only, runtime, ABI, image, workload-equivalence, stress, benchmark, and real-device operational checks.

## What Is Intentionally Limited

JavaToGpu is not a "run any Java on the GPU" system.

Current alpha limitations include:

- `@GPU` entry methods must return `void`; use output buffers for results.
- Arbitrary Java object allocation, virtual dispatch, exceptions, monitors, recursion, and heap/object-graph semantics are not supported inside GPU code.
- Arrays inside `@GPUStruct` fields are not supported by the current ABI.
- The structured ASM frontend expects a canonical GPU-safe subset, not arbitrary JVM bytecode.
- Intel and AMD OpenCL devices are not yet validated in the current local evidence set.
- API and generated launcher details may change before beta.

See [Known Limitations](docs/Known-Limitations.md) for the longer contract.

## Quick Start

Add the processor as both a dependency and an annotation processor:

```groovy
dependencies {
    implementation 'io.github.deussixik:javatogpu:0.1.0-alpha.1'
    annotationProcessor 'io.github.deussixik:javatogpu:0.1.0-alpha.1'
}
```

For strict compiler builds, add the optional IR validation module to the annotation-processor path:

```groovy
dependencies {
    annotationProcessor 'io.github.deussixik:javatogpu-ir-validation:0.1.0-alpha.1'
}

tasks.withType(JavaCompile).configureEach {
    options.compilerArgs += '-Ajavatogpu.irValidation=diagnostic'
    // Optional: quiet, summary, or detailed. Defaults to summary.
    options.compilerArgs += '-Ajavatogpu.irValidationDiagnostics=summary'
    // Optional: machine-readable CI artifact under generated sources.
    options.compilerArgs += '-Ajavatogpu.irValidationReport=reports/javatogpu-ir-validation.properties'
}
```

That module plugs into the compiler through Java `ServiceLoader`, but remains inactive until `javatogpu.irValidation` is enabled. Supported validation modes are `diagnostic`, `strictSafety`, and `strictOptimizer`; diagnostic output can be `quiet`, `summary`, or `detailed`, and CI can opt into a structured `.properties` report.

See [IR Validation](docs/IR-Validation.md) for the strict validator, read-only optimizer planning reports, and diagnostic vs strict build modes.

Write a restricted Java kernel:

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

Run generated calls through the OpenCL runtime:

```java
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntime;
import net.sixik.ga_utils.javatogpu.runtime.GpuRuntimeScope;

try (GpuRuntimeScope ignored = GpuRuntime.useOpenClSharedCache()) {
    DemoKernel.transform(input, output);
} finally {
    GpuRuntime.shutdownOpenClSharedCache();
}
```

For one-off usage, `GpuRuntime.useOpenCl()` is simpler. For repeated calls, `GpuRuntime.useOpenClSharedCache()` keeps the OpenCL session and compile cache warm.

## Documentation

- [Docs home](docs/Home.md)
- [Getting started](docs/Getting-Started.md)
- [Alpha release checklist](docs/Alpha-Release-Checklist.md)
- [Publishing guide](docs/Publishing.md)
- [API overview](docs/API-Overview.md)
- [Language contract](docs/Language-Contract.md)
- [Runtime guide](docs/Runtime-Guide.md)
- [OpenCL data model](docs/OpenCL-Data-Model.md)
- [IR validation](docs/IR-Validation.md)
- [Validation and operations](docs/Validation-and-Operations.md)
- [ASM contract](docs/ASM-Contract.md)
- [Troubleshooting](docs/Troubleshooting.md)
- [Known limitations](docs/Known-Limitations.md)
- [FAQ](docs/FAQ.md)

Maintainer planning, backlog notes, and historical design docs live in `docs-project-plan/` and are not the public user manual.

## Build And Validate

Run the normal test suite:

```powershell
.\gradlew.bat :processor:test --console=plain
```

Run the current OpenCL operational routine on a machine with a working OpenCL stack:

```powershell
.\gradlew.bat :processor:openClOperationalRoutine --rerun-tasks --console=plain
```

The OpenCL report bundle is generated under:

```text
processor/build/reports/opencl/
```

Important artifacts include `validation-report.md`, `validation-history.md`, `bucket-status.properties`, `workload-summary.properties`, and `long-running-summary.properties`.

## Publishing

The publishable Maven artifact is:

```text
io.github.deussixik:javatogpu
```

The optional strict IR validation artifact is:

```text
io.github.deussixik:javatogpu-ir-validation
```

Publishing is configured on the `processor` module. Secrets must live outside the repository in `~/.gradle/gradle.properties` or environment variables. See [Publishing Guide](docs/Publishing.md).

## Project Layout

- `processor`: compiler frontend, OpenCL emitter, runtime support, launchers, tests, and benchmark buckets.
- `examples-app`: small example kernels and usage patterns.
- `test-app`: consumer-style sample application.
- `docs`: public documentation.
- `docs-project-plan`: maintainer roadmap and planning notes.

## Programmatic Compiler API

Compiler integrations can use `GpuProgramCompiler` directly:

```java
GpuProgramCompiler compiler = GpuProgramCompiler.createDefault();
String sourceOpencl = compiler.compileSource(methodSource, helperSources);
String asmOpencl = compiler.compileStructuredAsm(kernelAsmMethod, helperAsmMethods, structs);
```

Use the Java source frontend for normal kernels. Use the structured ASM frontend only when you already own an AST/IR and can emit the supported canonical bytecode subset intentionally.

## Release Guidance

Recommended first public version name: `v0.1.0-alpha.1`.

Recommended positioning: public alpha / developer preview. The project has real runtime validation evidence on NVIDIA OpenCL, but it should not be marketed as stable or cross-vendor production-ready until Intel and AMD validation are also proven.

## License

See [LICENSE](LICENSE).

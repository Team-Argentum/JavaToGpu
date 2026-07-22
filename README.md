# JavaToGpu

JavaToGpu lets you write a restricted Java method, mark it as a GPU kernel, and run it through the OpenCL runtime.

It is currently a public alpha / developer preview. Use it for experiments, examples, compiler/runtime integration, and early GPU-kernel prototyping. Do not treat the API or generated launcher shape as stable before beta.

## Current Status

- OpenCL is the active runtime path today.
- NVIDIA OpenCL and AMD OpenCL are the current validated hardware baselines.
- Intel OpenCL still needs real-hardware validation before broad cross-vendor claims.
- CUDA is a staged, explicit opt-in preview path and is not production execution yet.
- IR optimizer mutation is optional, fail-closed, and intended for review/testing before production use.

JavaToGpu is not a "run any Java app on the GPU" system. GPU methods must stay inside the supported kernel subset.

## What You Can Do Today

- Write `@GPU` Java kernels over arrays, scalars, vectors, structs, pointers, images, and samplers.
- Use `GPU.*` builtins for OpenCL-style indexing, math, barriers, images, atomics, and low-level helpers.
- Run kernels through the public `JavaToGpu` runtime facade.
- Add fixture-based `@GPUTest` metadata for manual method probes and future placement evidence.
- Enable optional IR validation for stricter diagnostics and CI reports.
- Inspect backend/device explanations without learning backend SPI internals.

## Install

Add JavaToGpu as both a dependency and an annotation processor:

```groovy
dependencies {
    implementation 'io.github.deussixik:javatogpu:0.1.0-alpha.2'
    annotationProcessor 'io.github.deussixik:javatogpu:0.1.0-alpha.2'
}
```

Optional stricter IR validation:

```groovy
dependencies {
    annotationProcessor 'io.github.deussixik:javatogpu-ir-validation:0.1.0-alpha.2'
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
import net.sixik.ga_utils.javatogpu.api.GpuScope;
import net.sixik.ga_utils.javatogpu.api.JavaToGpu;

try (GpuScope ignored = JavaToGpu.useOpenClSharedCache()) {
    DemoKernel.transform(input, output);
} finally {
    JavaToGpu.shutdownOpenClSharedCache();
}
```

Use `JavaToGpu.useOpenCl()` for one-off calls. Use `JavaToGpu.useOpenClSharedCache()` for repeated calls so the OpenCL session and compile cache stay warm. The lower-level `runtime.GpuRuntime` entrypoint remains available for advanced runtime configuration and compatibility.

## Important Alpha Limits

- `@GPU` entry methods normally return `void`; write results to output buffers.
- Generated launcher convenience helpers can cover narrow return-first cases, but output parameters are the stable alpha pattern.
- General object allocation, virtual dispatch, exceptions, recursion, monitors, and heap object graphs are not supported inside kernels.
- Arrays inside `@GPUStruct` fields are not supported in the current alpha.
- OpenCL is the active backend today. CUDA, Vulkan, and Metal are future directions.

See [Known Limitations](docs/Known-Limitations.md) before using JavaToGpu in a larger project.

## Documentation

Start here:

- [User Quickstart](docs/User-Quickstart.md) - shortest path to one OpenCL-backed output array.
- [Getting Started](docs/Getting-Started.md) - first kernel with more context.
- [Cookbook](docs/Cookbook.md) - copyable user patterns.
- [Troubleshooting](docs/Troubleshooting.md) - first-run failures and fixes.
- [Performance Basics](docs/Performance-Basics.md) - cold compile, warm cache, launch overhead, and when GPU execution is worth it.
- [Known Limitations](docs/Known-Limitations.md) - current alpha boundaries.

Data and runtime:

- [OpenCL Data Model](docs/OpenCL-Data-Model.md) - arrays, structs, vectors, pointers, packed blobs, and images.
- [Method Tests](docs/Method-Tests.md) - fixture-based `@GPUTest` checks, including `@GPUStruct` examples.
- [Runtime Guide](docs/Runtime-Guide.md) - runtime scopes, launch sizes, logging, artifacts, and advanced options.
- [API Overview](docs/API-Overview.md) - public packages and most-used types.

Advanced and maintainer docs:

- [Language Contract](docs/Language-Contract.md) - exact supported Java subset.
- [IR Validation](docs/IR-Validation.md) - optional stricter compiler checks.
- [IR Optimizer](docs/IR-Optimizer.md) - optional optimizer profiles, journals, and dumps.
- [Backend Adapter Authoring](docs/Backend-Adapter-Authoring.md) - backend provider/SPI path.
- [Public API And Extension Contract](docs/Public-API-And-Extension-Contract.md) - extension services and compatibility boundaries.
- [Validation and Operations](docs/Validation-and-Operations.md) - local validation routines and OpenCL evidence artifacts.
- [Diagnostics Reference](docs/Diagnostics-Reference.md) - detailed diagnostic vocabulary.
- [Publishing Guide](docs/Publishing.md) - Maven Central publishing notes.

## Useful Commands

Run the normal processor tests:

```powershell
.\gradlew.bat :processor:test --console=plain
```

Run real OpenCL validation on a GPU machine:

```powershell
.\gradlew.bat :processor:openClOperationalRoutine --rerun-tasks --console=plain
```

Run the curated user-facing OpenCL walkthrough:

```powershell
.\gradlew.bat :examples-app:runOpenClPracticalReleaseExample --console=plain
```

Show backend/device selection explanations without running a kernel:

```powershell
.\gradlew.bat :examples-app:runBackendSelectionExample --console=plain
```

Show the public runtime facade and launch helpers without running a kernel:

```powershell
.\gradlew.bat :examples-app:runRuntimeFacadeExample --console=plain
```

Run the optional IR optimizer journal example:

```powershell
.\gradlew.bat :examples-app:runOptimizationJournalExample --console=plain
```

OpenCL reports are written under:

```text
processor/build/reports/opencl/
```

Start with `validation-report.md` when checking a run.

## Project Layout

- `processor` - annotation processor, compiler, OpenCL emitter, runtime, launchers, tests, and validation buckets.
- `ir-validation` - optional stricter IR validation module.
- `ir-optimizer` - optional backend-neutral IR optimizer skeleton and future transform module.
- `ir-vendor-optimizer` - optional vendor-specific IR optimizer provider skeleton.
- `examples-app` - example kernels and usage patterns.
- `test-app` - consumer-style sample application.
- `docs` - public documentation.

## Publishing

Published artifacts:

```text
io.github.deussixik:javatogpu
io.github.deussixik:javatogpu-ir-validation
io.github.deussixik:javatogpu-ir-optimizer
io.github.deussixik:javatogpu-ir-vendor-optimizer
```

Publishing is configured for the main processor artifact, optional IR validation artifact, optional backend-neutral IR optimizer artifact, and optional vendor optimizer provider artifact. Keep Maven Central credentials and signing keys outside the repository. See [Publishing Guide](docs/Publishing.md).

## License

See [LICENSE](LICENSE).

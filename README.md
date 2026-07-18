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

- `@GPU` entry methods return `void`; write results to output buffers. For the narrow single-output case, generated launchers can expose `invokeReturningFirst*` helpers that allocate one primitive output array and return `output[0]`; `GpuGeneratedLauncherInvoker.launcher(...)`, typed `invokeReturningFirst*As(...)`, `returnValueConvenience(...)`, and compile-time notes explain whether that helper is available. Use `-Ajavatogpu.returnValueConvenienceDiagnostics=quiet` to suppress those notes.
- General object allocation, virtual dispatch, exceptions, recursion, monitors, and heap object graphs are not supported inside kernels.
- Arrays inside `@GPUStruct` fields are not supported in the current alpha.
- OpenCL is the active backend today. CUDA, Vulkan, and Metal are future directions.
- NVIDIA OpenCL and AMD OpenCL are the current validated baselines; Intel should still be validated on real hardware before broad cross-vendor claims.

See [Known Limitations](docs/Known-Limitations.md) before using JavaToGpu in a larger project.

## Documentation

Start here:

- [Docs Home](docs/Home.md)
- [Getting Started](docs/Getting-Started.md)
- [Cookbook](docs/Cookbook.md)
- [Method Tests](docs/Method-Tests.md)
- [Runtime Guide](docs/Runtime-Guide.md)
- [API Overview](docs/API-Overview.md)
- [Known Limitations](docs/Known-Limitations.md)
- [Troubleshooting](docs/Troubleshooting.md)
- [FAQ](docs/FAQ.md)

Advanced topics:

- [Language Contract](docs/Language-Contract.md)
- [OpenCL Data Model](docs/OpenCL-Data-Model.md)
- [IR Validation](docs/IR-Validation.md)
- [IR Optimizer](docs/IR-Optimizer.md)
- IR Vendor Optimizer is documented in the IR Optimizer guide as a separate optional provider artifact.
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

Run the optional IR optimizer journal example:

```powershell
.\gradlew.bat :examples-app:runOptimizationJournalExample --console=plain
```

The example is documented in [examples-app/IR_OPTIMIZER_JOURNAL_EXAMPLE.md](examples-app/IR_OPTIMIZER_JOURNAL_EXAMPLE.md) and shows how to opt into the optimizer module while dumping original and optimized IR artifacts plus the optional ServiceLoader-backed runtime lifecycle event journal for review. It also includes a small custom `GpuRuntimeLifecycleService` example so downstream modules can add tracing or metrics without manual callback registration.

Show backend catalog and selection explanations without running a kernel:

```powershell
.\gradlew.bat :examples-app:runBackendSelectionExample --console=plain
```

This prints the standard backend catalog, planned CUDA/Vulkan/Metal diagnostics, and a combined backend/device
selection explanation. The multi-backend discovery catalog includes real OpenCL device evidence plus explicit planned
CUDA/Vulkan/Metal discovery states. OpenCL discovery is fail-soft: if OpenCL cannot be queried on the current machine,
the example prints the discovery blocker instead of running a kernel. When discovery succeeds, it also shows OpenCL
platform grouping, selected device ranking, and runtime self-test summary.

Inspect `@GPUTest` metadata and fixture readiness without running a kernel:

```powershell
.\gradlew.bat :examples-app:runMethodTestProbeExample --console=plain
```

This prints the generated `IrGpu` test-vector metadata, selection-probe count, classpath fixture-resource readiness,
fixture byte size/SHA-256 evidence, a top-level JSON payload-shape preview, numeric and `@GPUStruct` value bindings to
descriptor parameters, read-only Java invocation argument materialization, explicit CPU-reference comparison, and first blocker or
failure if a fixture reference is missing, malformed, or mismatched. The example stays preflight-only and does not
allocate GPU buffers or invoke OpenCL. Runtime tooling also exposes an opt-in bounded GPU probe executor for callers
that have installed a backend and want to run the same materialized fixtures through the real runtime path; pass a
separate CPU reference callback because rewritten `@GPU` methods may already route to the GPU launcher instead of
retaining their original CPU body. GPU probe executions expose a stable evidence-key hash so later selection caches can
avoid rerunning unchanged method/device/fixture combinations. `GpuRuntimeMethodTestGpuProbeOptions.cached()` enables the
current process-local cache; `persistentCached(path)` enables an opt-in disk-backed cache with fail-closed
corrupt/mismatch/expiry handling. The method-test pipeline also publishes ServiceLoader-friendly lifecycle events,
including GPU probe cache hit/miss events, so tracing or journal services can observe the preflight and probe flow
without manual listener registration. Device selection can also opt into cache-only method-test probe evidence ranking
with `GpuRuntimeCompileOptions.withPersistentMethodTestProbeEvidenceRanking(path)`: passed cached evidence boosts a
candidate, failed cached evidence rejects it, and missing evidence stays neutral. Runtime compile artifact dumps also
write `runtime-method-test-evidence.properties`; the OpenCL validation report aggregates those artifacts under
`Method Test Evidence` so CI archives show which kernels carried `@GPUTest` metadata and whether cached probe evidence
participated in device ranking.
This runtime path is explicitly `GpuRuntimeMethodTestProbeMode.CACHE_ONLY`: it reads warmed evidence only and never runs
method-test GPU probes during device selection.
Applications that want a single explicit operation can use
`GpuRuntimeMethodTestProbeEvidenceSelection.warmAndSelect(...)`: it runs the caller-approved warm-up first, then invokes
normal device selection with cache-only evidence ranking and returns one markdown/artifact-friendly report.

For the same flow with `@GPUStruct[]` fixtures, run:

```powershell
.\gradlew.bat :examples-app:runMethodTestStructProbeExample --console=plain
```

This uses `Vec2[]` JSON object arrays for input and expected output fixtures, materializes Java struct arrays, and
compares flattened field paths such as `[0].x` and `[0].y` in the CPU-reference preflight.

Run the portable cache-only ranking walkthrough:

```powershell
.\gradlew.bat :examples-app:runMethodTestProbeEvidenceRankingExample --console=plain
```

This example uses `GpuRuntimeMethodTestProbeEvidenceWarmup.warmSelectionProbeEvidence(...)` to record one `@GPUTest`
GPU-probe result into a persistent cache through a synthetic reference backend, then reruns backend/device selection
through the higher-level `GpuRuntimeMethodTestProbeEvidenceSelection.warmAndSelect(...)` helper. It does not require a
real OpenCL device: the point is to show that warm-up is an explicit opt-in step, while the ranking policy remains
`cache-only`. Passed cached selection
evidence boosts one candidate; missing evidence for another candidate stays neutral. Pass
`-Pjavatogpu.methodTestProbeEvidenceCacheDir=...` to choose the cache folder.

Run the same boundary against real discovered OpenCL devices:

```powershell
.\gradlew.bat :examples-app:runOpenClMethodTestProbeEvidenceSelectionExample --console=plain
```

This example calls `GpuRuntimeMethodTestProbeEvidenceSelection.warmAndSelectOpenCl(...)`, which discovers OpenCL,
builds owned warm-up candidates for discovered GPU devices, runs tiny `@GPUTest` selection probes only during the
explicit warm-up phase, and then prints the cache-only selection report. Pass
`-Pjavatogpu.methodTestProbeOpenClEvidenceCacheDir=...` to choose the persistent evidence cache and
`-Pjavatogpu.methodTestProbeOpenClWarmupLimit=1` to cap how many discovered devices are warmed.
The helper also emits service-based lifecycle events for discovery, candidate selection, warm-up, and final placement,
so a `GpuRuntimeLifecycleService` can journal the flow without manual listener wiring.
By default the runnable OpenCL example writes `runtime-lifecycle.jsonl` and `opencl-evidence-selection.trace` next to the
method-test evidence cache, then prints a short `warmAndSelectOpenCl(...)` trace preview. Compact trace lines include
`summary=` when portable runtime-state, compilation, invocation-binding, or artifact-dump fields are available. Override those paths with
`-Pjavatogpu.lifecycleJournalFile=...` and `-Pjavatogpu.exampleLifecycleTraceFile=...` when needed.

For a curated end-to-end OpenCL walkthrough that combines backend/device explanation, portable `@GPUTest` preflight,
real OpenCL evidence warm-up, cache-only placement, launch-shape guidance, vector/struct/packed-root-blob/image workload
smoke, generated return-first launcher convenience, image-helper guidance, optimizer artifact review guidance, and lifecycle
trace output, run:

```powershell
.\gradlew.bat :examples-app:runOpenClPracticalReleaseExample --console=plain
```

Use `-Pjavatogpu.practicalOpenClEvidenceCacheDir=...` to choose the evidence/journal directory. The walkthrough also
shows the common `OpenClImageWorkflow.rgbaIntToFloat2D(...)` host helper, then points to
`runOptimizationJournalExample` and the before/after files to compare: `original.backend.opencl-c`,
`optimized.backend.opencl-c`, selected `backend.opencl-c`, and `runtime-ir-optimizer-evidence.properties`.
Use `"-Pjavatogpu.runtimeLog=system-out"` or provide a `GpuRuntimeLogService` through ServiceLoader to route lifecycle
logs into System.out, Log4J, SLF4J, or another application logging backend without manual listener registration.

## Project Layout

- `processor` - annotation processor, compiler, OpenCL emitter, runtime, launchers, tests, and validation buckets.
- `ir-validation` - optional stricter IR validation module.
- `ir-optimizer` - optional backend-neutral IR optimizer skeleton and future transform module.
- `ir-vendor-optimizer` - optional vendor-specific IR optimizer provider skeleton; it plugs into the vendor proposal SPI and is not loaded by the default runtime optimizer bridge.
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
